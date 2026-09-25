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
    private lateinit var mediaVolumeManager: com.example.turnaway.engine.MediaVolumeManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var transitionJob: Job? = null

    private var activeProfile = RestrictionProfileEntity(profileName = "Standard Soft-Landing")
    private var isDispatchingGesture = false
    private var lastHandledTriggerTimestamp: Long = 0L

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
            mediaVolumeManager = com.example.turnaway.engine.MediaVolumeManager(this, audioManager)
            
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

    private var lastInterceptState: Boolean? = null

    private fun updateOverlayTouchableState(shouldIntercept: Boolean) {
        if (!::overlayView.isInitialized || !::overlayManager.isInitialized) return
        try {
            val wantToIntercept = shouldIntercept && !isDispatchingGesture
            if (lastInterceptState != wantToIntercept) {
                lastInterceptState = wantToIntercept
                val lp = overlayView.layoutParams as WindowManager.LayoutParams
                val currentlyIntercepting = (lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0

                if (currentlyIntercepting != wantToIntercept) {
                    if (wantToIntercept) {
                        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                    } else {
                        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    }
                    overlayManager.updateViewLayout(overlayView, lp)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error updating overlay touchable state", e)
        }
    }

    private var currentForegroundPackage: String? = null
    private var targetedPackages: Set<String> = emptySet()

    private fun evaluateAppTargeting() {
        val pkg = currentForegroundPackage ?: return
        if (isPackageExcluded(pkg)) return

        val isTargeted = targetedPackages.contains(pkg)
        val currentState = EngineBridge.engineStatus.value.state

        AppLogger.d(TAG, "evaluateAppTargeting: pkg=$pkg, isTargeted=$isTargeted, state=$currentState")

        if (isTargeted) {
            when (currentState) {
                EngineState.MONITORING -> {
                    AppLogger.d(TAG, "Targeted app ($pkg) active in foreground during MONITORING state. Awaiting manual wind-down start.")
                }
                EngineState.SOFT_LANDING_TRANSITION -> {
                    AppLogger.d(TAG, "Targeted app ($pkg) active in foreground during SOFT_LANDING_TRANSITION.")
                }
                EngineState.LOCKED_OUT -> {
                    AppLogger.w(TAG, "Targeted app ($pkg) launched during LOCKED_OUT state. Enforcing lockout.")
                    enforceAppLockoutIfRestricted(pkg)
                }
            }
        }
    }

    private fun isPackageExcluded(pkg: String): Boolean {
        if (pkg.isBlank()) return true
        if (pkg == packageName) return true
        if (pkg == "com.android.systemui" || pkg == "com.android.settings" || pkg == "android" ||
            pkg.contains("permissioncontroller") || pkg.contains("packageinstaller") || pkg.contains("inputmethod")) return true

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

        if (!targetedPackages.contains(pkg)) {
            AppLogger.d(TAG, "Package $pkg is not in targeted set. Skipping app closure lockout.")
            return
        }

        AppLogger.w(TAG, "Restricted app launch intercepted in LOCKED_OUT state: $pkg. Closing app completely via BACK & process kill.")

        // 1. Perform BACK action to finish activity stack and close the app
        performGlobalAction(GLOBAL_ACTION_BACK)

        // 2. Kill background processes for target package
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            am?.killBackgroundProcesses(pkg)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error killing background process for $pkg", e)
        }

        // 3. Safeguard: if app remains in foreground, send BACK & HOME fallback
        serviceScope.launch {
            delay(150)
            if (currentForegroundPackage == pkg && targetedPackages.contains(pkg)) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(100)
                if (currentForegroundPackage == pkg && targetedPackages.contains(pkg)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
                try {
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                    am?.killBackgroundProcesses(pkg)
                } catch (e: Exception) {}
            }
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
                            val updated = if (!p.enableColorDesaturation || !p.enableOverlayGraying) {
                                p.copy(enableColorDesaturation = true, enableOverlayGraying = true)
                            } else p
                            if (updated != p) {
                                dao.insertProfile(updated)
                            }
                            activeProfile = updated
                            AppLogger.i(TAG, "Active profile synced from database: ${updated.profileName}, duration: ${updated.transitionDurationMinutes}m")
                        } else {
                            val newP = RestrictionProfileEntity(
                                profileName = "Standard Soft-Landing",
                                enableColorDesaturation = true,
                                enableOverlayGraying = true
                            )
                            dao.insertProfile(newP)
                            activeProfile = newP
                            AppLogger.i(TAG, "Inserted default Soft-Landing profile into Room database")
                        }
                    }
                }

                launch {
                    dao.getTargetedApps().collect { apps ->
                        targetedPackages = apps.filter { it.isTargeted }.map { it.packageName }.toSet()
                        AppLogger.i(TAG, "Synced ${targetedPackages.size} targeted apps: $targetedPackages")
                        com.example.turnaway.engine.ThrottleSessionManager.getInstance(applicationContext).setTargetPackageNames(targetedPackages)
                    }
                }

                if (com.example.turnaway.engine.SessionStateManager.isSessionActive(applicationContext)) {
                    val savedConfig = com.example.turnaway.engine.SessionStateManager.getSavedConfig(applicationContext)
                    AppLogger.i(TAG, "Resuming persisted active session: totalUsage=${savedConfig.totalUsageMinutes}m, transition=${savedConfig.transitionMinutes}m")
                    startTwoStageSessionLoop(savedConfig.totalUsageMinutes, savedConfig.transitionMinutes)
                }

                launch {
                    EngineBridge.manualTriggerEvent.collect { params ->
                        if (params != null && params.timestamp > lastHandledTriggerTimestamp) {
                            lastHandledTriggerTimestamp = params.timestamp
                            AppLogger.i(TAG, "Manual two-stage session start triggered: totalUsage=${params.totalUsageMinutes}m, transition=${params.transitionMinutes}m")
                            startTwoStageSessionLoop(params.totalUsageMinutes, params.transitionMinutes)
                        }
                    }
                }

                launch {
                    EngineBridge.abortEvent.collect { shouldAbort ->
                        if (shouldAbort) {
                            AppLogger.w(TAG, "Soft-Landing session stopped by parent")
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

    private fun startTwoStageSessionLoop(totalUsageMinutes: Int, transitionMinutes: Int) {
        transitionJob?.cancel()
        transitionJob = serviceScope.launch {
            try {
                val totalDurationMs = totalUsageMinutes * 60 * 1000L
                val transitionMs = transitionMinutes * 60 * 1000L
                val normalUsageMs = (totalDurationMs - transitionMs).coerceAtLeast(0L)
                val startTime = com.example.turnaway.engine.SessionStateManager.getStartTimestamp(applicationContext).let {
                    if (it > 0L) it else System.currentTimeMillis()
                }

                val curveType = try {
                    DecayCurveType.valueOf(activeProfile.curveType)
                } catch (e: Exception) {
                    DecayCurveType.LINEAR
                }

                val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15

                AppLogger.i(
                    TAG,
                    "Started two-stage session. Total Usage: ${totalUsageMinutes}m, Transition: ${transitionMinutes}m, Normal Phase: ${normalUsageMs / 60000}m"
                )

                var wasTransitionInitialized = false
                desaturationController.resetAll()

                while (isActive && com.example.turnaway.engine.SessionStateManager.isSessionActive(applicationContext)) {
                    val now = System.currentTimeMillis()
                    val elapsedMs = now - startTime

                    if (elapsedMs < normalUsageMs) {
                        // ─── STAGE 1: NORMAL USAGE (No Degradations) ───
                        val totalRemainingMs = (totalDurationMs - elapsedMs).coerceAtLeast(0L)
                        val normalRemainingMs = (normalUsageMs - elapsedMs).coerceAtLeast(0L)

                        desaturationController.resetAll()
                        touchDelayQueueManager.resetQueue()
                        updateOverlayTouchableState(false)
                        com.example.turnaway.engine.ThrottleSessionManager.getInstance(applicationContext).stopSession()

                        val currentVolPercent = if (maxVolume > 0 && audioManager != null) {
                            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()
                        } else 1.0f

                        val progressData = com.example.turnaway.engine.SessionProgress(
                            state = com.example.turnaway.engine.SessionState.NORMAL_USAGE,
                            config = com.example.turnaway.engine.SessionConfig(totalUsageMinutes, transitionMinutes),
                            startTimestampMs = startTime,
                            totalTimeRemainingMs = totalRemainingMs,
                            normalTimeRemainingMs = normalRemainingMs,
                            transitionTimeRemainingMs = transitionMs,
                            currentSaturation = 1.0f,
                            currentTouchDelayMs = 0L,
                            currentVolumePercent = currentVolPercent,
                            isNetworkThrottled = false
                        )
                        com.example.turnaway.engine.SessionStateManager.updateProgress(progressData)

                        EngineBridge.updateStatus(
                            EngineStatusData(
                                state = EngineState.MONITORING,
                                sessionState = com.example.turnaway.engine.SessionState.NORMAL_USAGE,
                                totalUsageMinutes = totalUsageMinutes,
                                transitionMinutes = transitionMinutes,
                                timeRemainingMs = totalRemainingMs,
                                normalTimeRemainingMs = normalRemainingMs,
                                transitionTimeRemainingMs = transitionMs,
                                currentSaturation = 1.0f,
                                currentTouchDelayMs = 0L,
                                currentVolumePercent = currentVolPercent,
                                isNetworkThrottled = false,
                                activeProfile = activeProfile
                            )
                        )
                    } else if (elapsedMs < totalDurationMs) {
                        // ─── STAGE 2: AUTOMATIC WIND-DOWN TRANSITION ───
                        val transitionElapsedMs = elapsedMs - normalUsageMs
                        val transitionRemainingMs = (totalDurationMs - elapsedMs).coerceAtLeast(0L)

                        if (!wasTransitionInitialized) {
                            wasTransitionInitialized = true
                            if (::mediaVolumeManager.isInitialized) {
                                mediaVolumeManager.startFade(transitionMs, activeProfile.enableAudioFade)
                            }
                            if (activeProfile.enableNetworkThrottling) {
                                com.example.turnaway.engine.ThrottleSessionManager.getInstance(applicationContext).startSession()
                            }
                            AppLogger.i(TAG, "Automatic Wind-Down Transition triggered at t=$elapsedMs ms!")
                        }

                        // 1. Grayscale & Overlay Veil (with automatic visual overlay fallback if WRITE_SECURE_SETTINGS missing)
                        val isGrayscaleActive = activeProfile.enableColorDesaturation &&
                                DecayCurveCalculator.evaluateGrayscaleState(transitionElapsedMs, transitionMs)
                        val saturationFactor = if (isGrayscaleActive) 0.0f else 1.0f

                        val shouldEnableOverlay = activeProfile.enableOverlayGraying || (isGrayscaleActive && !desaturationController.hasWriteSecureSettingsPermission())

                        val currentVeilAlpha = if (shouldEnableOverlay) {
                            DecayCurveCalculator.calculateCurrentVeilAlpha(
                                elapsedTransitionMs = transitionElapsedMs,
                                totalTransitionMs = transitionMs,
                                minVeilAlpha = 0.0f,
                                maxVeilAlpha = activeProfile.overlayMaxAlpha
                            )
                        } else 0.0f

                        val overlayProgressFactor = if (activeProfile.overlayMaxAlpha > 0.0f) {
                            (currentVeilAlpha / activeProfile.overlayMaxAlpha).coerceIn(0.0f, 1.0f)
                        } else 0.0f

                        desaturationController.updateVisualEffects(
                            enableSystemGrayscale = isGrayscaleActive,
                            saturationFactor = saturationFactor,
                            enableOverlay = shouldEnableOverlay,
                            overlayProgress = overlayProgressFactor,
                            overlayColorHex = activeProfile.overlayColorHex,
                            overlayMaxAlpha = activeProfile.overlayMaxAlpha
                        )

                        // 2. Audio Volume Reduction
                        var currentVolPercent = 1.0f
                        if (activeProfile.enableAudioFade && ::mediaVolumeManager.isInitialized) {
                            mediaVolumeManager.applyVolumeForElapsed(transitionElapsedMs)
                            val allowedVol = mediaVolumeManager.calculateCurrentTargetVolume(transitionElapsedMs)
                            currentVolPercent = if (maxVolume > 0) allowedVol.toFloat() / maxVolume.toFloat() else 0f
                        }

                        // 3. Touch Delay
                        val currentTouchDelay = if (activeProfile.enableTouchDelay) {
                            DecayCurveCalculator.calculateCurrentTouchDelay(
                                elapsedTransitionMs = transitionElapsedMs,
                                totalTransitionMs = transitionMs,
                                minLagMs = 0L,
                                maxLagMs = activeProfile.maxTouchDelayMs
                            )
                        } else 0L

                        touchDelayQueueManager.setTouchDelay(currentTouchDelay)
                        updateOverlayTouchableState(currentTouchDelay > 0L)

                        val isThrottled = com.example.turnaway.engine.ThrottleSessionManager.getInstance(applicationContext).isDropActiveFlow.value

                        val progressData = com.example.turnaway.engine.SessionProgress(
                            state = com.example.turnaway.engine.SessionState.IN_TRANSITION,
                            config = com.example.turnaway.engine.SessionConfig(totalUsageMinutes, transitionMinutes),
                            startTimestampMs = startTime,
                            totalTimeRemainingMs = transitionRemainingMs,
                            normalTimeRemainingMs = 0L,
                            transitionTimeRemainingMs = transitionRemainingMs,
                            currentSaturation = saturationFactor,
                            currentTouchDelayMs = currentTouchDelay,
                            currentVolumePercent = currentVolPercent,
                            isNetworkThrottled = isThrottled
                        )
                        com.example.turnaway.engine.SessionStateManager.updateProgress(progressData)

                        EngineBridge.updateStatus(
                            EngineStatusData(
                                state = EngineState.SOFT_LANDING_TRANSITION,
                                sessionState = com.example.turnaway.engine.SessionState.IN_TRANSITION,
                                totalUsageMinutes = totalUsageMinutes,
                                transitionMinutes = transitionMinutes,
                                timeRemainingMs = transitionRemainingMs,
                                normalTimeRemainingMs = 0L,
                                transitionTimeRemainingMs = transitionRemainingMs,
                                currentSaturation = saturationFactor,
                                currentTouchDelayMs = currentTouchDelay,
                                currentVolumePercent = currentVolPercent,
                                isNetworkThrottled = isThrottled,
                                activeProfile = activeProfile
                            )
                        )
                    } else {
                        // ─── STAGE 3: COMPLETED LOCKOUT (PERSISTENT UNTIL STOP) ───
                        val isGrayscaleActive = activeProfile.enableColorDesaturation
                        val shouldEnableOverlay = activeProfile.enableOverlayGraying || (isGrayscaleActive && !desaturationController.hasWriteSecureSettingsPermission())

                        desaturationController.updateVisualEffects(
                            enableSystemGrayscale = isGrayscaleActive,
                            saturationFactor = 0.0f,
                            enableOverlay = shouldEnableOverlay,
                            overlayProgress = 1.0f,
                            overlayColorHex = activeProfile.overlayColorHex,
                            overlayMaxAlpha = activeProfile.overlayMaxAlpha
                        )
                        if (activeProfile.enableAudioFade && ::mediaVolumeManager.isInitialized) {
                            mediaVolumeManager.applyVolumeForElapsed(transitionMs)
                        }
                        if (activeProfile.enableTouchDelay) {
                            touchDelayQueueManager.setTouchDelay(activeProfile.maxTouchDelayMs)
                            updateOverlayTouchableState(true)
                        }

                        val isThrottled = com.example.turnaway.engine.ThrottleSessionManager.getInstance(applicationContext).isDropActiveFlow.value

                        val progressData = com.example.turnaway.engine.SessionProgress(
                            state = com.example.turnaway.engine.SessionState.COMPLETED_LOCKED,
                            config = com.example.turnaway.engine.SessionConfig(totalUsageMinutes, transitionMinutes),
                            startTimestampMs = startTime,
                            totalTimeRemainingMs = 0L,
                            normalTimeRemainingMs = 0L,
                            transitionTimeRemainingMs = 0L,
                            currentSaturation = 0.0f,
                            currentTouchDelayMs = if (activeProfile.enableTouchDelay) activeProfile.maxTouchDelayMs else 0L,
                            currentVolumePercent = 0.0f,
                            isNetworkThrottled = isThrottled
                        )
                        com.example.turnaway.engine.SessionStateManager.updateProgress(progressData)

                        EngineBridge.updateStatus(
                            EngineStatusData(
                                state = EngineState.LOCKED_OUT,
                                sessionState = com.example.turnaway.engine.SessionState.COMPLETED_LOCKED,
                                totalUsageMinutes = totalUsageMinutes,
                                transitionMinutes = transitionMinutes,
                                timeRemainingMs = 0L,
                                normalTimeRemainingMs = 0L,
                                transitionTimeRemainingMs = 0L,
                                currentSaturation = 0.0f,
                                currentTouchDelayMs = if (activeProfile.enableTouchDelay) activeProfile.maxTouchDelayMs else 0L,
                                currentVolumePercent = 0.0f,
                                isNetworkThrottled = isThrottled,
                                activeProfile = activeProfile
                            )
                        )

                        currentForegroundPackage?.let { fgPkg ->
                            enforceAppLockoutIfRestricted(fgPkg)
                        }
                        showTimeExceededNotification()
                    }

                    delay(500)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in two-stage session coroutine", e)
            }
        }
    }

    private fun abortTransition() {
        transitionJob?.cancel()
        com.example.turnaway.engine.SessionStateManager.stopSession(applicationContext)

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
        if (::mediaVolumeManager.isInitialized) {
            mediaVolumeManager.onStopClicked()
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
                sessionState = com.example.turnaway.engine.SessionState.IDLE,
                timeRemainingMs = 0L,
                normalTimeRemainingMs = 0L,
                transitionTimeRemainingMs = 0L,
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
        if (::mediaVolumeManager.isInitialized && mediaVolumeManager.isFadingActive) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE -> {
                    AppLogger.w(TAG, "Blocked hardware volume key press (${event.keyCode}) due to active wind-down/lockout")
                    return true // Consume event completely to prevent hardware volume changes & popup UI
                }
            }
        }
        val currentState = EngineBridge.engineStatus.value.state
        if (currentState == EngineState.SOFT_LANDING_TRANSITION || currentState == EngineState.LOCKED_OUT) {
            if (activeProfile.enableAudioFade) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP,
                    KeyEvent.KEYCODE_VOLUME_DOWN,
                    KeyEvent.KEYCODE_VOLUME_MUTE -> {
                        return true
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
        if (::mediaVolumeManager.isInitialized) {
            mediaVolumeManager.onStopClicked()
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
