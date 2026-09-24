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
import com.example.turnaway.engine.TouchDelayQueueManager
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull

class SoftLandingAccessibilityService : AccessibilityService() {

    private val TAG = "SoftLandingService"
    private lateinit var overlayManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var desaturationController: ColorDesaturationController
    private lateinit var touchDelayQueueManager: TouchDelayQueueManager
    private lateinit var networkThrottlingController: com.example.turnaway.engine.NetworkThrottlingController
    private lateinit var audioFadeManager: com.example.turnaway.engine.AudioFadeManager

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

            // 5. Register debug trigger receiver for adb testing
            val triggerFilter = android.content.IntentFilter().apply {
                addAction("com.example.turnaway.ACTION_START_WINDDOWN")
                addAction("com.example.turnaway.ACTION_STOP_WINDDOWN")
            }
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                debugBroadcastReceiver,
                triggerFilter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Fatal error during accessibility service initialization", e)
        }
    }

    private val debugBroadcastReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                "com.example.turnaway.ACTION_START_WINDDOWN" -> {
                    AppLogger.i(TAG, "Received ACTION_START_WINDDOWN via broadcast")
                    EngineBridge.triggerManualSoftLanding()
                }
                "com.example.turnaway.ACTION_STOP_WINDDOWN" -> {
                    AppLogger.i(TAG, "Received ACTION_STOP_WINDDOWN via broadcast")
                    EngineBridge.abortTransition()
                }
            }
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

    private fun showTimeExceededNotification() {
        try {
            val channelId = "soft_landing_engine_channel"
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val stopIntent = android.content.Intent("com.example.turnaway.ACTION_STOP_WINDDOWN").apply {
                setPackage(packageName)
            }
            val stopPendingIntent = android.app.PendingIntent.getBroadcast(
                this,
                1002,
                stopIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val openAppIntent = android.content.Intent(this, com.example.turnaway.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = android.app.PendingIntent.getActivity(
                this,
                1003,
                openAppIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("⚠️ Time Limit Exceeded")
                .setContentText("Screen-time limit reached. Device lagging, audio muted & color desaturated.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openAppPendingIntent)
                .addAction(android.R.drawable.ic_delete, "STOP DEGRADATION", stopPendingIntent)
                .setOngoing(true)
                .build()

            manager.notify(1001, notification)
            AppLogger.w(TAG, "Posted Time Limit Exceeded notification with STOP action")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to post time exceeded notification", e)
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
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            overlayManager.addView(overlayView, layoutParams)

            desaturationController = ColorDesaturationController(this, overlayView, overlayManager)
            touchDelayQueueManager = TouchDelayQueueManager(this)
            networkThrottlingController = com.example.turnaway.engine.NetworkThrottlingController(this)
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioFadeManager = com.example.turnaway.engine.AudioFadeManager(this, audioManager)
            
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
        // No-op (frame throttling removed)
    }

    private fun isPackageExcluded(pkg: String): Boolean {
        if (pkg.isBlank()) return true
        if (pkg == packageName) return true
        if (pkg == "com.android.systemui" || pkg == "com.android.settings" || pkg == "android") return true

        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            val defaultDialer = telecomManager?.defaultDialerPackage
            if (!defaultDialer.isNullOrBlank() && pkg == defaultDialer) return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking default dialer package", e)
        }

        try {
            val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
            }
            val launcherInfo = packageManager.resolveActivity(homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            val launcherPkg = launcherInfo?.activityInfo?.packageName
            if (!launcherPkg.isNullOrBlank() && pkg == launcherPkg) return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking launcher package", e)
        }

        return false
    }

    private fun enforceAppLockoutIfRestricted(pkg: String) {
        if (isPackageExcluded(pkg)) return

        if (targetedPackages.contains(pkg)) {
            AppLogger.w(TAG, "Restricted app launch intercepted in LOCKED_OUT state: $pkg. Executing GLOBAL_ACTION_HOME.")
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
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
                        AppLogger.i(TAG, "Synced ${targetedPackages.size} targeted apps: $targetedPackages")
                        com.example.turnaway.engine.ThrottleSessionManager.getInstance(applicationContext).setTargetPackageNames(targetedPackages)
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

                val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15

                if (::audioFadeManager.isInitialized) {
                    audioFadeManager.startFadeSession(durationMs, profile.enableAudioFade)
                }

                AppLogger.i(TAG, "Gradual degradation started. Duration: ${durationMinutes}m, Curve: ${profile.curveType}")

                // Ensure initial clean display at start of degradation
                desaturationController.resetAll()

                // Initial baseline state
                com.example.turnaway.engine.ThrottleSessionManager.getInstance(applicationContext).stopSession()
                touchDelayQueueManager.resetQueue()
                updateOverlayTouchableState(false)

                if (profile.enableNetworkThrottling) {
                    com.example.turnaway.engine.ThrottleSessionManager.getInstance(applicationContext).startSession()
                }

                val initialAllowedVol = if (::audioFadeManager.isInitialized) audioFadeManager.getCurrentAllowedVolume(0L) else maxVolume
                EngineBridge.updateStatus(
                    EngineStatusData(
                        state = EngineState.SOFT_LANDING_TRANSITION,
                        timeRemainingMs = durationMs,
                        currentSaturation = 1.0f,
                        currentTouchDelayMs = 0L,
                        currentVolumePercent = if (maxVolume > 0) initialAllowedVol.toFloat() / maxVolume.toFloat() else 1.0f,
                        isNetworkThrottled = com.example.turnaway.engine.ThrottleSessionManager.getInstance(applicationContext).isDropActiveFlow.value,
                        activeProfile = profile
                    )
                )

                var elapsedMs = 0L

                while (elapsedMs < durationMs && isActive) {
                    val progress = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    val decay = DecayCurveCalculator.calculateDecay(progress, curveType)

                    // 1. System Grayscale (activates at midpoint 50%) and Overlay Veil
                    val isGrayscaleActive = profile.enableColorDesaturation && DecayCurveCalculator.evaluateGrayscaleState(elapsedMs, durationMs)
                    val saturationFactor = if (isGrayscaleActive) 0.0f else 1.0f

                    val currentVeilAlpha = if (profile.enableOverlayGraying) {
                        DecayCurveCalculator.calculateCurrentVeilAlpha(
                            elapsedTransitionMs = elapsedMs,
                            totalTransitionMs = durationMs,
                            minVeilAlpha = 0.0f,
                            maxVeilAlpha = profile.overlayMaxAlpha
                        )
                    } else {
                        0.0f
                    }
                    val overlayProgressFactor = if (profile.overlayMaxAlpha > 0.0f) {
                        (currentVeilAlpha / profile.overlayMaxAlpha).coerceIn(0.0f, 1.0f)
                    } else {
                        0.0f
                    }
                    desaturationController.updateVisualEffects(
                        enableSystemGrayscale = isGrayscaleActive,
                        saturationFactor = saturationFactor,
                        enableOverlay = profile.enableOverlayGraying,
                        overlayProgress = overlayProgressFactor,
                        overlayColorHex = profile.overlayColorHex,
                        overlayMaxAlpha = profile.overlayMaxAlpha
                    )

                    // 2. Audio Volume Reduction (uniform linear fade down to 0)
                    var currentVolPercent = 1.0f
                    if (profile.enableAudioFade && ::audioFadeManager.isInitialized) {
                        audioFadeManager.applyVolumeForElapsed(elapsedMs)
                        val allowedVol = audioFadeManager.getCurrentAllowedVolume(elapsedMs)
                        currentVolPercent = if (maxVolume > 0) allowedVol.toFloat() / maxVolume.toFloat() else 0f
                    }

                    // 4. Touch Delay (accelerates linearly to peak by midpoint 50% and clamps)
                    val currentTouchDelay = if (profile.enableTouchDelay) {
                        DecayCurveCalculator.calculateCurrentTouchDelay(
                            elapsedTransitionMs = elapsedMs,
                            totalTransitionMs = durationMs,
                            minLagMs = 0L,
                            maxLagMs = profile.maxTouchDelayMs
                        )
                    } else {
                        0L
                    }
                    touchDelayQueueManager.setTouchDelay(currentTouchDelay)
                    updateOverlayTouchableState(currentTouchDelay > 0L)

                    val remainingMs = (durationMs - elapsedMs).coerceAtLeast(0L)
                    EngineBridge.updateStatus(
                        EngineStatusData(
                            state = EngineState.SOFT_LANDING_TRANSITION,
                            timeRemainingMs = remainingMs,
                            currentSaturation = saturationFactor,
                            currentTouchDelayMs = currentTouchDelay,
                            currentVolumePercent = currentVolPercent,
                            isNetworkThrottled = com.example.turnaway.engine.ThrottleSessionManager.getInstance(applicationContext).isDropActiveFlow.value,
                            activeProfile = profile
                        )
                    )

                    delay(500)
                    elapsedMs = System.currentTimeMillis() - startTime
                }

                if (isActive) {
                    AppLogger.w(TAG, "Time limit exceeded! Screen is 100% grayscale, throttled, muted.")

                    // Final state at end of duration: keep 100% native system grayscale flip and persist visual overlay veil at 100% opacity
                    desaturationController.updateVisualEffects(
                        enableSystemGrayscale = profile.enableColorDesaturation,
                        saturationFactor = 0.0f,
                        enableOverlay = profile.enableOverlayGraying,
                        overlayProgress = 1.0f,
                        overlayColorHex = profile.overlayColorHex,
                        overlayMaxAlpha = profile.overlayMaxAlpha
                    )
                    if (profile.enableAudioFade && ::audioFadeManager.isInitialized) {
                        audioFadeManager.applyVolumeForElapsed(durationMs)
                    }
                    if (profile.enableTouchDelay) {
                        touchDelayQueueManager.setTouchDelay(profile.maxTouchDelayMs)
                        updateOverlayTouchableState(true)
                    }

                    EngineBridge.updateStatus(
                        EngineStatusData(
                            state = EngineState.LOCKED_OUT,
                            timeRemainingMs = 0L,
                            currentSaturation = 0.0f,
                            currentTouchDelayMs = if (profile.enableTouchDelay) profile.maxTouchDelayMs else 0L,
                            currentVolumePercent = 0.0f,
                            isNetworkThrottled = com.example.turnaway.engine.ThrottleSessionManager.getInstance(applicationContext).isDropActiveFlow.value,
                            activeProfile = profile
                        )
                    )

                    // Transition End Trigger: Eject immediately if active app is in restricted set
                    currentForegroundPackage?.let { fgPkg ->
                        enforceAppLockoutIfRestricted(fgPkg)
                    }

                    showTimeExceededNotification()

                    saveSessionMetrics(
                        timestampStart = startTime,
                        durationActiveMs = durationMs,
                        timeToDisengageMs = durationMs,
                        wasAborted = false
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in degradation transition coroutine", e)
            }
        }
    }

    private fun abortTransition() {
        transitionJob?.cancel()

        // 1. Immediately restore full color display and remove overlay veil
        desaturationController.resetAll()

        // 3. Immediately clear touch delay and disable touch interception
        touchDelayQueueManager.resetQueue()
        updateOverlayTouchableState(false)
        if (::networkThrottlingController.isInitialized) {
            networkThrottlingController.stopThrottling()
        }
        com.example.turnaway.engine.ThrottleSessionManager.getInstance(applicationContext).stopSession()

        // 4. Restore normal audio volume
        if (::audioFadeManager.isInitialized) {
            audioFadeManager.stopAndRestoreVolume()
        }

        // 5. Restore normal status notification
        showMonitoringNotification()

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: maxVolume
        val volPercent = if (maxVolume > 0) currentVol.toFloat() / maxVolume.toFloat() else 1.0f

        EngineBridge.updateStatus(
            EngineStatusData(
                state = EngineState.MONITORING,
                timeRemainingMs = 0L,
                currentSaturation = 1.0f,
                currentTouchDelayMs = 0L,
                currentVolumePercent = volPercent,
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
        val currentState = EngineBridge.engineStatus.value.state
        if (currentState == EngineState.SOFT_LANDING_TRANSITION || currentState == EngineState.LOCKED_OUT) {
            if (activeProfile.enableAudioFade) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP,
                    KeyEvent.KEYCODE_VOLUME_DOWN,
                    KeyEvent.KEYCODE_VOLUME_MUTE -> {
                        AppLogger.w(TAG, "Blocked volume key press (${event.keyCode}) due to active soft-landing")
                        return true // Consume the event to prevent volume change
                    }
                }
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            val className = event.className?.toString()
            val isTarget = targetedPackages.contains(pkg)
            AppLogger.d(TAG, "TYPE_WINDOW_STATE_CHANGED event: pkg=$pkg, class=$className, inTargetSet=$isTarget")

            // CRITICAL FIX: Ignore TurnAway's own overlay windows so we do not deactivate targeting
            // when our own overlay is added or rendered on top of the target app!
            if (pkg == packageName && className != "com.example.turnaway.MainActivity") {
                AppLogger.d(TAG, "Ignoring TurnAway overlay window event: class=$className")
                return
            }

            if (!pkg.isNullOrBlank() && pkg != "android" && pkg != "com.android.systemui") {
                if (currentForegroundPackage != pkg) {
                    currentForegroundPackage = pkg
                    AppLogger.i(TAG, "Foreground package changed to: $pkg (inTargetSet=$isTarget)")
                    evaluateAppTargeting()
                }
            }

            // Check if app closure / re-launch lockout is active in LOCKED_OUT state
            if (!pkg.isNullOrBlank()) {
                val currentEngineState = EngineBridge.engineStatus.value.state
                if (currentEngineState == EngineState.LOCKED_OUT) {
                    enforceAppLockoutIfRestricted(pkg)
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
        if (::audioFadeManager.isInitialized) {
            audioFadeManager.stopAndRestoreVolume()
        }
        if (::networkThrottlingController.isInitialized) {
            networkThrottlingController.release()
        }
        if (::overlayView.isInitialized) {
            try {
                overlayManager.removeView(overlayView)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error removing overlay view", e)
            }
        }
        try {
            unregisterReceiver(debugBroadcastReceiver)
        } catch (e: Exception) {}
        serviceScope.cancel()
    }
}
