package com.example.turnaway.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

class JankGhostOverlayView(context: Context) : View(context) {

    private var currentBitmap: Bitmap? = null
    private var ghostBitmap: Bitmap? = null

    private var frameCount = 0L
    private var debugSource = "None"
    private var debugFps = 0

    private val normalPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val ghostPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        // ~35% alpha for realistic optical frame smear / motion persistence
        alpha = 90
    }

    // Diagnostic Visible Marker Paints
    private val debugBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.MAGENTA
        style = Paint.Style.STROKE
        strokeWidth = 24f // 24px thick border around entire display
    }

    private val debugTextBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(220, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val debugTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.YELLOW
        textSize = 42f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val dstRect = Rect()
    private val ghostDstRect = Rect()

    // 12px offset creates visual smear when dropping frames
    private val ghostOffsetX = 12
    private val ghostOffsetY = 14

    init {
        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun updateFrame(newBitmap: Bitmap, source: String = "MediaProjection", fps: Int = 0) {
        frameCount++
        debugSource = source
        debugFps = fps

        // Retain previous frame for ghosted smear without illegal software canvas drawing of hardware bitmaps
        if (currentBitmap != null && !currentBitmap!!.isRecycled) {
            ghostBitmap = currentBitmap
        }

        currentBitmap = newBitmap
        invalidate()
    }

    fun setDebugInfo(source: String, fps: Int) {
        this.debugSource = source
        this.debugFps = fps
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        dstRect.set(0, 0, w, h)
        ghostDstRect.set(ghostOffsetX, ghostOffsetY, w + ghostOffsetX, h + ghostOffsetY)
        com.example.turnaway.util.AppLogger.d("JankOverlayView", "onSizeChanged: ${w}x$h, alpha=$alpha, visibility=$visibility")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw ghosted offset copy of prior frame behind current frame
        ghostBitmap?.let { ghost ->
            if (!ghost.isRecycled) {
                canvas.drawBitmap(ghost, null, ghostDstRect, ghostPaint)
            }
        }

        // 2. Snap current frame instantly over the top
        currentBitmap?.let { current ->
            if (!current.isRecycled) {
                canvas.drawBitmap(current, null, dstRect, normalPaint)
            }
        }

        // 3. DIAGNOSTIC VISIBLE DEBUG MARKER:
        // A. Draw bright magenta border around the entire overlay canvas
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), debugBorderPaint)

        // B. Draw debug HUD bar at the top of the screen
        val hudHeight = 110f
        canvas.drawRect(0f, 0f, width.toFloat(), hudHeight, debugTextBgPaint)

        val statusText = if (currentBitmap != null) {
            "⚡ TURNAWAY OVERLAY ACTIVE | FPS: $debugFps | Frame #$frameCount | Src: $debugSource"
        } else {
            "⚠ TURNAWAY OVERLAY ACTIVE | WAITING FOR FRAMES (Bitmap is null) | Src: $debugSource"
        }
        canvas.drawText(statusText, width / 2f, 70f, debugTextPaint)
    }

    fun clearFrames() {
        currentBitmap = null
        ghostBitmap?.recycle()
        ghostBitmap = null
        frameCount = 0
        invalidate()
    }
}
