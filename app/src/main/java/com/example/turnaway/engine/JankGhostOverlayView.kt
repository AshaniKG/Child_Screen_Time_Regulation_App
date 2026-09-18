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

    private val normalPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val ghostPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        // ~35% alpha for realistic optical frame smear / motion persistence
        alpha = 90
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

    fun updateFrame(newBitmap: Bitmap) {
        // Capture previous frame for ghosted smear
        if (currentBitmap != null && !currentBitmap!!.isRecycled) {
            if (ghostBitmap == null || ghostBitmap?.width != currentBitmap!!.width || ghostBitmap?.height != currentBitmap!!.height) {
                ghostBitmap = Bitmap.createBitmap(currentBitmap!!.width, currentBitmap!!.height, Bitmap.Config.ARGB_8888)
            }
            // Snapshot previous frame pixels
            val canvas = Canvas(ghostBitmap!!)
            canvas.drawBitmap(currentBitmap!!, 0f, 0f, null)
        }

        currentBitmap = newBitmap
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        dstRect.set(0, 0, w, h)
        ghostDstRect.set(ghostOffsetX, ghostOffsetY, w + ghostOffsetX, h + ghostOffsetY)
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
    }

    fun clearFrames() {
        currentBitmap = null
        ghostBitmap?.recycle()
        ghostBitmap = null
        invalidate()
    }
}
