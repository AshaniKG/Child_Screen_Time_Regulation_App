package com.example.turnaway.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

class ScreenCaptureForegroundService : Service() {

    private val TAG = "ScreenCaptureService"

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 1080
    private var screenHeight = 1920
    private var screenDpi = 320

    private var reusablePaddedBitmap: Bitmap? = null
    private var reusableTargetBitmap: Bitmap? = null

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        if (action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != 0 && resultData != null) {
            startForegroundServiceNotification()
            initCapture(resultCode, resultData)
        } else {
            AppLogger.w(TAG, "Invalid resultCode or resultData received")
            stopSelf()
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Lag Frame Capture",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Captures display frames for wind-down frame rate reduction"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Pacing Active")
            .setContentText("Adaptive frame rate regulation active.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initCapture(resultCode: Int, resultData: Intent) {
        try {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, resultData)

            if (mediaProjection == null) {
                AppLogger.e(TAG, "Failed to obtain MediaProjection instance")
                _isProjectionActive.value = false
                return
            }

            // Register callback required for Android 14+
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    AppLogger.w(TAG, "MediaProjection was stopped by system or user revocation")
                    tearDown()
                    _isProjectionActive.value = false
                }
            }, handler)

            setupDisplayMetrics()
            setupImageReaderAndVirtualDisplay()

            instance = this
            _isProjectionActive.value = true
            AppLogger.i(TAG, "MediaProjection capture pipeline initialized: ${screenWidth}x${screenHeight} @ ${screenDpi}dpi")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error initializing MediaProjection capture", e)
            _isProjectionActive.value = false
        }
    }

    private fun setupDisplayMetrics() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            screenWidth = metrics.bounds.width()
            screenHeight = metrics.bounds.height()
            screenDpi = resources.configuration.densityDpi
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
            screenDpi = displayMetrics.densityDpi
        }
    }

    private fun setupImageReaderAndVirtualDisplay() {
        // Max images = 2 to minimize memory while always holding latest frame
        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "TurnAwayCaptureDisplay",
            screenWidth,
            screenHeight,
            screenDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            handler
        )
    }

    private var acquireCallCount = 0L

    @Synchronized
    fun acquireLatestFrame(): Bitmap? {
        acquireCallCount++
        val reader = imageReader
        if (reader == null) {
            AppLogger.w(TAG, "acquireLatestFrame() called but imageReader is NULL")
            return null
        }

        var image: Image? = null
        try {
            image = reader.acquireLatestImage()
            if (image == null) {
                if (acquireCallCount % 30 == 0L) {
                    AppLogger.d(TAG, "acquireLatestImage() returned NULL (reusing cached bitmap: ${reusableTargetBitmap != null})")
                }
                return reusableTargetBitmap
            }

            val planes = image.planes
            if (planes.isEmpty()) {
                AppLogger.w(TAG, "acquireLatestFrame() Image has 0 planes!")
                return null
            }

            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth
            val paddedWidth = screenWidth + rowPadding / pixelStride

            if (acquireCallCount % 30 == 0L) {
                AppLogger.d(TAG, "Frame acquired: ${image.width}x${image.height} | pixelStride=$pixelStride, rowStride=$rowStride, rowPadding=$rowPadding")
            }

            if (rowPadding == 0) {
                if (reusableTargetBitmap == null || reusableTargetBitmap?.isRecycled == true ||
                    reusableTargetBitmap?.width != screenWidth || reusableTargetBitmap?.height != screenHeight
                ) {
                    reusableTargetBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
                }
                buffer.rewind()
                reusableTargetBitmap?.copyPixelsFromBuffer(buffer)
                return reusableTargetBitmap
            } else {
                if (reusablePaddedBitmap == null || reusablePaddedBitmap?.isRecycled == true ||
                    reusablePaddedBitmap?.width != paddedWidth || reusablePaddedBitmap?.height != screenHeight
                ) {
                    reusablePaddedBitmap = Bitmap.createBitmap(paddedWidth, screenHeight, Bitmap.Config.ARGB_8888)
                }
                buffer.rewind()
                reusablePaddedBitmap?.copyPixelsFromBuffer(buffer)

                // Crop to real width
                val cropped = Bitmap.createBitmap(reusablePaddedBitmap!!, 0, 0, screenWidth, screenHeight)
                reusableTargetBitmap = cropped
                return cropped
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error acquiring frame from ImageReader: ${e.javaClass.simpleName} - ${e.message}", e)
            return null
        } finally {
            image?.close()
        }
    }

    private fun stopCapture() {
        tearDown()
        _isProjectionActive.value = false
        AppLogger.i(TAG, "MediaProjection capture pipeline torn down cleanly")
    }

    private fun tearDown() {
        instance = null
        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error releasing virtual display", e)
        }

        try {
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error closing image reader", e)
        }

        try {
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error stopping media projection", e)
        }

        reusablePaddedBitmap?.recycle()
        reusablePaddedBitmap = null
        reusableTargetBitmap?.recycle()
        reusableTargetBitmap = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
    }

    companion object {
        const val ACTION_STOP = "com.example.turnaway.ACTION_STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val CHANNEL_ID = "turnaway_media_projection_channel"
        private const val NOTIFICATION_ID = 2002

        @Volatile
        var instance: ScreenCaptureForegroundService? = null
            private set

        private val _isProjectionActive = MutableStateFlow(false)
        val isProjectionActive: StateFlow<Boolean> = _isProjectionActive.asStateFlow()

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenCaptureForegroundService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
