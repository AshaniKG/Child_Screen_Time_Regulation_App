package com.example.turnaway.engine

import kotlin.math.exp

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
     * Calculates touch latency delay L(t) with a noticeable starting base (150ms)
     * so touch sluggishness and lag are immediately felt upon starting wind-down.
     */
    fun calculateTouchDelayMs(progress: Float, maxTouchDelayMs: Long, type: DecayCurveType): Long {
        val decay = calculateDecay(progress, type)
        val baseDelay = 150L.coerceAtMost(maxTouchDelayMs / 2)
        val remaining = maxTouchDelayMs - baseDelay
        val delay = baseDelay + (remaining * decay).toLong()
        return delay.coerceIn(0L, maxTouchDelayMs)
    }

    /**
     * Calculates blur radius in pixels in [0, maxBlurRadius] (up to maxBlurRadius, default: 10)
     */
    fun calculateBlurRadius(progress: Float, maxBlurRadius: Int = 10, type: DecayCurveType): Int {
        val decay = calculateDecay(progress, type)
        return (maxBlurRadius * decay).toInt().coerceIn(0, maxBlurRadius)
    }
}
