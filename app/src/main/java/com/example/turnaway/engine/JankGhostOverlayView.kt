package com.example.turnaway.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.example.turnaway.util.AppLogger

/**
 * Minimal debug overlay for the frame throttling system.
 *
 * The actual visual degradation (Gaussian blur) is applied by [ColorDesaturationController]
 * via WindowManager.LayoutParams.setBlurBehindRadius() on the service's main overlay.
 *
 * This overlay only renders a small debug HUD to confirm the throttling system is active.
 * It does NOT draw any black flashes, pulses, or full-screen tints.
 */
class JankGhostOverlayView(context: Context) : View(context) {

    private var frameCount = 0L
    private var debugSource = "Idle"
    private var debugFps = 0

    // Diagnostic HUD paints
    private val debugTextBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val debugTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        textSize = 32f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /** Called by FrameThrottlingController when FPS or source changes */
    fun setDebugInfo(source: String, fps: Int) {
        debugSource = source
        debugFps = fps
        frameCount++
        invalidate()
    }

    /** Legacy compat — FrameThrottlingController may call this with a bitmap */
    fun updateFrame(bitmap: Bitmap, source: String, fps: Int) {
        debugSource = source
        debugFps = fps
        frameCount++
        invalidate()
    }

    /** Pulse methods are no-ops — blur is handled by ColorDesaturationController */
    fun startPulse(targetFps: Int) {
        debugFps = targetFps
        debugSource = "Blur Active"
        frameCount = 0
        invalidate()
        AppLogger.d("JankOverlayView", "startPulse called (no-op pulse, blur via ColorDesaturation): fps=$targetFps")
    }

    fun stopPulse() {
        debugSource = "Stopped"
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        AppLogger.d("JankOverlayView", "onSizeChanged: ${w}x$h")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Small debug HUD in top-left corner only — no full-screen effects
        val hudW = 520f
        val hudH = 50f
        val margin = 16f
        canvas.drawRect(margin, margin, margin + hudW, margin + hudH, debugTextBgPaint)
        val currentBlur = com.example.turnaway.service.EngineBridge.engineStatus.value.currentBlurRadius
        canvas.drawText(
            "TurnAway | FPS:$debugFps | Blur:${currentBlur}px | $debugSource",
            margin + 10f, margin + 34f, debugTextPaint
        )
    }

    fun clearFrames() {
        stopPulse()
        frameCount = 0
    }
}

