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
     * Calculates target FPS floor between 60 FPS down to minFpsFloor
     */
    fun calculateTargetFps(progress: Float, minFpsFloor: Int, type: DecayCurveType): Int {
        val decay = calculateDecay(progress, type)
        val target = 60 - ((60 - minFpsFloor.coerceIn(5, 60)) * decay)
        return target.toInt().coerceIn(minFpsFloor, 60)
    }

    /**
     * Calculates touch latency delay L(t) in [0, maxTouchDelayMs]
     */
    fun calculateTouchDelayMs(progress: Float, maxTouchDelayMs: Long, type: DecayCurveType): Long {
        val decay = calculateDecay(progress, type)
        return (maxTouchDelayMs * decay).toLong().coerceIn(0L, maxTouchDelayMs)
    }
}
