package com.example.turnaway.engine

import kotlin.math.exp
import kotlin.math.roundToInt

enum class DecayCurveType {
    LINEAR, EXPONENTIAL, SIGMOIDAL
}

object DecayCurveCalculator {

    /**
     * Calculates decay factor f(x) for normalized progress x in [0.0, 1.0]
     */
    fun calculateDecay(progress: Float, type: DecayCurveType): Float {
        val x = progress.coerceIn(0.0f, 1.0f)
        return when (type) {
            DecayCurveType.LINEAR -> x
            DecayCurveType.EXPONENTIAL -> x * x
            DecayCurveType.SIGMOIDAL -> {
                (1.0f / (1.0f + exp(-10.0f * (x - 0.5f))))
            }
        }
    }

    /**
     * Calculates target media stream volume for elapsed transition time.
     * Uniform linear fade from 100% of initialVol down to 0 at totalTransitionMs.
     * Post-transition (elapsedTransitionMs >= totalTransitionMs), returns 0.
     */
    fun calculateCurrentVolume(
        elapsedTransitionMs: Long,
        totalTransitionMs: Long,
        initialVol: Int
    ): Int {
        if (totalTransitionMs <= 0L || initialVol <= 0) return 0
        if (elapsedTransitionMs >= totalTransitionMs) return 0

        val ratio = 1.0 - (elapsedTransitionMs.coerceAtLeast(0L).toDouble() / totalTransitionMs.toDouble())
        return (initialVol.toDouble() * ratio).roundToInt().coerceIn(0, initialVol)
    }

    /**
     * Evaluates whether screen grayscale should be active for elapsed transition time.
     * Grayscale is OFF for the first half of transition (0 <= t < 0.5 * T_tr),
     * and turns ON at the midpoint (50%) and remains ON for the second half and post-transition.
     */
    fun evaluateGrayscaleState(elapsedTransitionMs: Long, totalTransitionMs: Long): Boolean {
        val midpointMs = totalTransitionMs / 2L
        if (midpointMs <= 0L) return true
        return elapsedTransitionMs >= midpointMs
    }

    /**
     * Calculates visual saturation factor s(t) from 1.0 (fully saturated) to 0.0 (monochrome)
     */
    fun calculateSaturation(progress: Float, type: DecayCurveType): Float {
        val decay = calculateDecay(progress, type)
        return (1.0f - decay).coerceIn(0.0f, 1.0f)
    }

    /**
     * Calculates target FPS floor between 25 FPS down to minFpsFloor (5-15 FPS)
     * Starting at 25 FPS ensures that frame stutter pacing is active immediately during wind-down.
     */
    fun calculateTargetFps(progress: Float, minFpsFloor: Int, type: DecayCurveType): Int {
        val decay = calculateDecay(progress, type)
        val effectiveMin = minFpsFloor.coerceIn(5, 25)
        val target = 25 - ((25 - effectiveMin) * decay)
        return target.toInt().coerceIn(effectiveMin, 25)
    }

    /**
     * Calculates artificial touch latency (touch lag) for elapsed transition time.
     * Accelerates to reach maxLagMs by the 50% midpoint of total transition duration (uniform linear progression),
     * and clamps at maxLagMs for the second half (50% to 100%) and beyond.
     */
    fun calculateCurrentTouchDelay(
        elapsedTransitionMs: Long,
        totalTransitionMs: Long,
        minLagMs: Long = 0L,
        maxLagMs: Long
    ): Long {
        val halfTransitionMs = totalTransitionMs / 2L
        if (halfTransitionMs <= 0L) return maxLagMs

        return if (elapsedTransitionMs < halfTransitionMs) {
            val progress = elapsedTransitionMs.toFloat() / halfTransitionMs.toFloat()
            (minLagMs + progress * (maxLagMs - minLagMs)).toLong().coerceIn(minLagMs, maxLagMs)
        } else {
            maxLagMs
        }
    }

    /**
     * Legacy wrapper for calculateCurrentTouchDelay
     */
    fun calculateTouchDelayMs(progress: Float, maxTouchDelayMs: Long, type: DecayCurveType): Long {
        val totalMs = 1000L
        val elapsedMs = (progress * totalMs).toLong()
        return calculateCurrentTouchDelay(elapsedMs, totalMs, 0L, maxTouchDelayMs)
    }

    /**
     * Calculates current screen overlay veil alpha for elapsed transition time.
     * Accelerates to reach maxVeilAlpha by the 50% midpoint of total transition duration (uniform linear progression),
     * and clamps at maxVeilAlpha for the second half (50% to 100%) and beyond.
     */
    fun calculateCurrentVeilAlpha(
        elapsedTransitionMs: Long,
        totalTransitionMs: Long,
        minVeilAlpha: Float = 0.0f,
        maxVeilAlpha: Float
    ): Float {
        val halfTransitionMs = totalTransitionMs / 2L
        if (halfTransitionMs <= 0L) return maxVeilAlpha

        return if (elapsedTransitionMs < halfTransitionMs) {
            val progress = elapsedTransitionMs.toFloat() / halfTransitionMs.toFloat()
            (minVeilAlpha + progress * (maxVeilAlpha - minVeilAlpha)).coerceIn(minVeilAlpha, maxVeilAlpha)
        } else {
            maxVeilAlpha
        }
    }

    /**
     * Calculates blur radius in pixels in [0, maxBlurRadius] (up to maxBlurRadius, default: 10)
     */
    fun calculateBlurRadius(progress: Float, maxBlurRadius: Int = 10, type: DecayCurveType): Int {
        val decay = calculateDecay(progress, type)
        return (maxBlurRadius * decay).toInt().coerceIn(0, maxBlurRadius)
    }
}
