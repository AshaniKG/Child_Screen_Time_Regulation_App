package com.example.turnaway.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.media.AudioManager
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.example.turnaway.data.db.SoftLandingDatabase
import com.example.turnaway.data.entity.RestrictionProfileEntity
import com.example.turnaway.data.entity.SessionLogEntity
import com.example.turnaway.engine.ColorDesaturationController
import com.example.turnaway.engine.DecayCurveCalculator
import com.example.turnaway.engine.DecayCurveType
import com.example.turnaway.engine.FrameThrottlingController
import com.example.turnaway.engine.TouchDelayQueueManager
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull

class SoftLandingAccessibilityService : AccessibilityService() {

    private val TAG = "SoftLandingService"
    private lateinit var overlayManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var desaturationController: ColorDesaturationController
    private lateinit var frameThrottlingController: FrameThrottlingController
    private lateinit var touchDelayQueueManager: TouchDelayQueueManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var transitionJob: Job? = null

    private var activeProfile = RestrictionProfileEntity(profileName = "Standard Soft-Landing")
    private var isDispatchingGesture = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLogger.i(TAG, "Soft-Landing Accessibility Service connected successfully")

        try {
            // 1. Preserve XML configuration and capabilities (canTakeScreenshot, canPerformGestures, etc.)
            val currentInfo = serviceInfo ?: AccessibilityServiceInfo()
            currentInfo.eventTypes = currentInfo.eventTypes or
                    AccessibilityEvent.TYPE_TOUCH_INTERACTION_START or
                    AccessibilityEvent.TYPE_TOUCH_INTERACTION_END or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            currentInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            currentInfo.flags = currentInfo.flags or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            this.serviceInfo = currentInfo

            // 2. Post Persistent Status Notification
            showMonitoringNotification()

            // 3. Initialize Overlay Layer Canvas & Engine Controllers
            initializeOverlayCanvas()

            // 4. Listen to EngineBridge events & DB profiles
            observeBridgeAndDatabase()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Fatal error during accessibility service initialization", e)
        }
    }

    private fun showMonitoringNotification() {
        try {
            val channelId = "soft_landing_engine_channel"
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Soft-Landing Monitoring Service",
                    NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Soft-Landing Engine Active")
                .setContentText("Monitoring screen-time restrictions & sensory transitions.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()

            manager.notify(1001, notification)
            AppLogger.d(TAG, "Posted monitoring notification")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to post service notification", e)
        }
    }

    private fun initializeOverlayCanvas() {
        try {
            overlayManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            overlayView = com.example.turnaway.engine.DesaturationOverlayView(this)

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )

            overlayManager.addView(overlayView, layoutParams)

            desaturationController = ColorDesaturationController(this, overlayView, overlayManager)
            frameThrottlingController = FrameThrottlingController(this, overlayView, overlayManager)
            touchDelayQueueManager = TouchDelayQueueManager(this)
            
            var gesturePath = android.graphics.Path()
            var gestureStartTime = 0L

            overlayView.setOnTouchListener { _, event ->
                val delay = touchDelayQueueManager.getTouchDelay()
                if (delay > 0L && !isDispatchingGesture) {
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            gesturePath = android.graphics.Path()
                            gesturePath.moveTo(event.rawX, event.rawY)
                            gestureStartTime = System.currentTimeMillis()
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            gesturePath.lineTo(event.rawX, event.rawY)
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            gesturePath.lineTo(event.rawX, event.rawY + 0.5f)
                            val duration = System.currentTimeMillis() - gestureStartTime
                            
                            isDispatchingGesture = true
                            updateOverlayTouchableState(true)
                            
                            touchDelayQueueManager.processInterceptedMotionEvent(gesturePath, duration.coerceAtLeast(10L)) {
                                isDispatchingGesture = false
                                serviceScope.launch(Dispatchers.Main) {
                                    updateOverlayTouchableState(touchDelayQueueManager.getTouchDelay() > 0L)
                                }
                            }
                        }
                    }
                    true
                } else {
                    false
                }
            }
            
            AppLogger.i(TAG, "Initialized overlay canvas and engine controllers")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error initializing overlay canvas view", e)
        }
    }

    private fun updateOverlayTouchableState(shouldIntercept: Boolean) {
        if (!::overlayView.isInitialized || !::overlayManager.isInitialized) return
        try {
            val lp = overlayView.layoutParams as WindowManager.LayoutParams
            val currentlyIntercepting = (lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0
            val wantToIntercept = shouldIntercept && !isDispatchingGesture
            
            if (currentlyIntercepting != wantToIntercept) {
                if (wantToIntercept) {
                    lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                } else {
                    lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                }
                overlayManager.updateViewLayout(overlayView, lp)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error updating overlay touchable state", e)
        }
    }

    private var currentForegroundPackage: String? = null
    private var targetedPackages: Set<String> = emptySet()

    private fun evaluateAppTargeting() {
        if (!::frameThrottlingController.isInitialized) return
        val currentPkg = currentForegroundPackage
        val isExcluded = currentPkg == null ||
                currentPkg == packageName ||
                currentPkg == "com.android.systemui" ||
                currentPkg == "com.android.settings" ||
                currentPkg.contains("launcher")

        val isTargeted = !isExcluded && targetedPackages.contains(currentPkg)
        AppLogger.d(TAG, "evaluateAppTargeting: pkg=$currentPkg, isTargeted=$isTargeted, targetedCount=${targetedPackages.size}")
        frameThrottlingController.setTargetAppForeground(isTargeted)
    }

    private fun observeBridgeAndDatabase() {
        serviceScope.launch {
            try {
                val database = SoftLandingDatabase.getDatabase(applicationContext)
                val dao = database.softLandingDao()

                launch {
                    dao.getAllProfiles().collect { profiles ->
                        val p = profiles.firstOrNull()
                        if (p != null) {
                            activeProfile = p
                            AppLogger.i(TAG, "Active profile synced from database: ${p.profileName}, duration: ${p.transitionDurationMinutes}m")
                        } else {
                            dao.insertProfile(activeProfile)
                            AppLogger.i(TAG, "Inserted default Soft-Landing profile into Room database")
                        }
                    }
                }

                launch {
                    dao.getTargetedApps().collect { apps ->
                        targetedPackages = apps.filter { it.isTargeted }.map { it.packageName }.toSet()
                        AppLogger.i(TAG, "Synced ${targetedPackages.size} targeted apps for selective regulation")
                        evaluateAppTargeting()
                    }
                }

                launch {
                    EngineBridge.manualTriggerEvent.collect { timestamp ->
                        if (timestamp != null) {
                            AppLogger.i(TAG, "Manual Soft-Landing transition triggered (timestamp=$timestamp)")
                            evaluateAppTargeting()
                            startTransitionWindow(activeProfile)
                        }
                    }
                }

                launch {
                    EngineBridge.abortEvent.collect { shouldAbort ->
                        if (shouldAbort) {
                            AppLogger.w(TAG, "Soft-Landing transition aborted by parent")
                            abortTransition()
                            EngineBridge.resetAbort()
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error observing database or EngineBridge", e)
            }
        }
    }

    private fun startTransitionWindow(profile: RestrictionProfileEntity) {
        transitionJob?.cancel()
        transitionJob = serviceScope.launch {
            try {
                val durationMinutes = profile.transitionDurationMinutes.coerceAtLeast(1)
                val durationMs = durationMinutes * 60 * 1000L
                val startTime = System.currentTimeMillis()
                val curveType = try {
                    DecayCurveType.valueOf(profile.curveType)
                } catch (e: Exception) {
                    DecayCurveType.LINEAR
                }

                AppLogger.i(TAG, "Transition window started. Duration: ${durationMinutes}m, Curve: ${profile.curveType}, MinFps: ${profile.minFpsFloor}")

                EngineBridge.updateStatus(
                    EngineStatusData(
                        state = EngineState.SOFT_LANDING_TRANSITION,
                        timeRemainingMs = durationMs,
                        currentSaturation = 1.0f,
                        currentBlurRadius = 0,
                        currentFps = 60,
                        currentTouchDelayMs = 0L,
                        currentVolumePercent = 1.0f,
                        activeProfile = profile
                    )
                )

                var elapsedMs = 0L
                while (elapsedMs < durationMs && isActive) {
                    val progress = elapsedMs.toFloat() / durationMs.toFloat()

                    // Screen dimming removed per instructions: keep saturation at 1.0f and blur at 0
                    val saturation = 1.0f
                    val blurRadius = 0

                    // Pure Screen FPS Lag focus
                    val targetFps = if (profile.enableFrameThrottling) {
                        DecayCurveCalculator.calculateTargetFps(progress, profile.minFpsFloor, curveType)
                    } else 60

                    // Touch delay & audio fade disabled for now per user focus on screen FPS lag
                    desaturationController.updateSaturationAndBlur(1.0f, 0)
                    frameThrottlingController.setTargetFps(targetFps)
                    touchDelayQueueManager.setTouchDelay(0L)

                    withContext(Dispatchers.Main) {
                        updateOverlayTouchableState(false)
                    }

                    val remainingMs = durationMs - elapsedMs
                    EngineBridge.updateStatus(
                        EngineStatusData(
                            state = EngineState.SOFT_LANDING_TRANSITION,
                            timeRemainingMs = remainingMs,
                            currentSaturation = saturation,
                            currentBlurRadius = blurRadius,
                            currentFps = targetFps,
                            currentTouchDelayMs = 0L,
                            currentVolumePercent = 1.0f,
                            activeProfile = profile
                        )
                    )

                    delay(500)
                    elapsedMs = System.currentTimeMillis() - startTime
                }

                if (isActive) {
                    AppLogger.w(TAG, "Soft-Landing transition reached complete lockout phase (Lag remains active)")
                    desaturationController.updateSaturationAndBlur(1.0f, 0)
                    frameThrottlingController.setTargetFps(profile.minFpsFloor)
                    touchDelayQueueManager.setTouchDelay(0L)

                    withContext(Dispatchers.Main) {
                        updateOverlayTouchableState(false)
                    }

                    EngineBridge.updateStatus(
                        EngineStatusData(
                            state = EngineState.LOCKED_OUT,
                            timeRemainingMs = 0L,
                            currentSaturation = 1.0f,
                            currentBlurRadius = 0,
                            currentFps = profile.minFpsFloor,
                            currentTouchDelayMs = 0L,
                            currentVolumePercent = 1.0f,
                            activeProfile = profile
                        )
                    )

                    saveSessionMetrics(
                        timestampStart = startTime,
                        durationActiveMs = durationMs,
                        timeToDisengageMs = durationMs,
                        wasAborted = false
                    )

                    // Retain maximum lag throttling without locking device screen so Stop button remains available
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in transition window coroutine", e)
            }
        }
    }

    private fun abortTransition() {
        transitionJob?.cancel()
        desaturationController.updateSaturationAndBlur(1.0f, 0)
        frameThrottlingController.stopThrottling()
        touchDelayQueueManager.setTouchDelay(0L)
        updateOverlayTouchableState(false)

        EngineBridge.updateStatus(
            EngineStatusData(
                state = EngineState.MONITORING,
                timeRemainingMs = 0L,
                currentSaturation = 1.0f,
                currentBlurRadius = 0,
                currentFps = 60,
                currentTouchDelayMs = 0L,
                currentVolumePercent = 1.0f,
                activeProfile = activeProfile
            )
        )
    }

    private fun saveSessionMetrics(
        timestampStart: Long,
        durationActiveMs: Long,
        timeToDisengageMs: Long,
        wasAborted: Boolean
    ) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = SoftLandingDatabase.getDatabase(applicationContext)
                db.softLandingDao().insertSessionLog(
                    SessionLogEntity(
                        timestampStart = timestampStart,
                        durationActiveMs = durationActiveMs,
                        timeToDisengageMs = timeToDisengageMs,
                        wasManuallyAborted = wasAborted
                    )
                )
                AppLogger.i(TAG, "Saved session metrics to Room database")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to save session metrics", e)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val currentState = EngineBridge.engineStatus.value.state
            if (currentState == EngineState.SOFT_LANDING_TRANSITION || currentState == EngineState.LOCKED_OUT) {
                if (activeProfile.enableAudioFade) {
                    AppLogger.w(TAG, "Blocked volume key press due to active soft-landing")
                    return true // Consume the event to prevent volume change
                }
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank() && pkg != "android" && pkg != "com.android.systemui") {
                if (currentForegroundPackage != pkg) {
                    currentForegroundPackage = pkg
                    AppLogger.d(TAG, "Active foreground window changed: $pkg")
                    evaluateAppTargeting()
                }
            }
        }
    }

    override fun onInterrupt() {
        AppLogger.w(TAG, "Accessibility Service interrupted by OS")
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.i(TAG, "Accessibility Service destroyed")
        if (::frameThrottlingController.isInitialized) {
            frameThrottlingController.release()
        }
        if (::overlayView.isInitialized) {
            try {
                overlayManager.removeView(overlayView)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error removing overlay view", e)
            }
        }
        serviceScope.cancel()
    }
}
