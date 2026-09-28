package com.example.turnaway

import com.example.turnaway.engine.DecayCurveCalculator
import com.example.turnaway.engine.DecayCurveType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecayCurveCalculatorTest {

    @Test
    fun testLinearDecayCurve() {
        val f0 = DecayCurveCalculator.calculateDecay(0.0f, DecayCurveType.LINEAR)
        val f50 = DecayCurveCalculator.calculateDecay(0.5f, DecayCurveType.LINEAR)
        val f100 = DecayCurveCalculator.calculateDecay(1.0f, DecayCurveType.LINEAR)

        assertEquals(0.0f, f0, 0.001f)
        assertEquals(0.5f, f50, 0.001f)
        assertEquals(1.0f, f100, 0.001f)
    }

    @Test
    fun testExponentialDecayCurve() {
        val f0 = DecayCurveCalculator.calculateDecay(0.0f, DecayCurveType.EXPONENTIAL)
        val f50 = DecayCurveCalculator.calculateDecay(0.5f, DecayCurveType.EXPONENTIAL)
        val f100 = DecayCurveCalculator.calculateDecay(1.0f, DecayCurveType.EXPONENTIAL)

        assertEquals(0.0f, f0, 0.001f)
        assertEquals(0.25f, f50, 0.001f)
        assertEquals(1.0f, f100, 0.001f)
    }

    @Test
    fun testSigmoidalDecayCurve() {
        val f0 = DecayCurveCalculator.calculateDecay(0.0f, DecayCurveType.SIGMOIDAL)
        val f50 = DecayCurveCalculator.calculateDecay(0.5f, DecayCurveType.SIGMOIDAL)
        val f100 = DecayCurveCalculator.calculateDecay(1.0f, DecayCurveType.SIGMOIDAL)

        assertTrue(f0 < 0.01f)
        assertEquals(0.5f, f50, 0.01f)
        assertTrue(f100 > 0.99f)
    }

    @Test
    fun testSaturationDecay() {
        val s0 = DecayCurveCalculator.calculateSaturation(0.0f, DecayCurveType.LINEAR)
        val s50 = DecayCurveCalculator.calculateSaturation(0.5f, DecayCurveType.LINEAR)
        val s100 = DecayCurveCalculator.calculateSaturation(1.0f, DecayCurveType.LINEAR)

        assertEquals(1.0f, s0, 0.001f)
        assertEquals(0.5f, s50, 0.001f)
        assertEquals(0.0f, s100, 0.001f)
    }

    @Test
    fun testTargetFpsCalculation() {
        val fpsStart = DecayCurveCalculator.calculateTargetFps(0.0f, 5, DecayCurveType.LINEAR)
        val fpsMid = DecayCurveCalculator.calculateTargetFps(0.5f, 5, DecayCurveType.LINEAR)
        val fpsEnd = DecayCurveCalculator.calculateTargetFps(1.0f, 5, DecayCurveType.LINEAR)

        assertEquals(25, fpsStart)
        assertEquals(15, fpsMid)
        assertEquals(5, fpsEnd)
    }

    @Test
    fun testTouchDelayMsCalculation() {
        val totalMs = 1000L
        val maxLag = 800L
        val delayStart = DecayCurveCalculator.calculateCurrentTouchDelay(0L, totalMs, 0L, maxLag)
        val delayMid = DecayCurveCalculator.calculateCurrentTouchDelay(500L, totalMs, 0L, maxLag)
        val delayEnd = DecayCurveCalculator.calculateCurrentTouchDelay(1000L, totalMs, 0L, maxLag)

        assertEquals(0L, delayStart)
        assertEquals(800L, delayMid)
        assertEquals(800L, delayEnd)
    }

    @Test
    fun testAcceleratedTouchDelayMidpointPeaking() {
        val totalMs = 60_000L // 1 minute transition duration
        val maxLagMs = 1000L  // 1000ms maximum latency

        // t = 0: 0% of max (0ms)
        val delayStart = DecayCurveCalculator.calculateCurrentTouchDelay(0L, totalMs, 0L, maxLagMs)
        assertEquals(0L, delayStart)

        // t = 0.25 * T_tr: 50% of max (500ms)
        val delayQuarter = DecayCurveCalculator.calculateCurrentTouchDelay((totalMs * 0.25).toLong(), totalMs, 0L, maxLagMs)
        assertEquals(500L, delayQuarter)

        // t = 0.50 * T_tr: 100% of max (1000ms)
        val delayHalf = DecayCurveCalculator.calculateCurrentTouchDelay((totalMs * 0.50).toLong(), totalMs, 0L, maxLagMs)
        assertEquals(1000L, delayHalf)

        // t = 0.75 * T_tr: Clamped at 100% of max (1000ms)
        val delayThreeQuarter = DecayCurveCalculator.calculateCurrentTouchDelay((totalMs * 0.75).toLong(), totalMs, 0L, maxLagMs)
        assertEquals(1000L, delayThreeQuarter)

        // t = 1.00 * T_tr: Clamped at 100% of max (1000ms)
        val delayFull = DecayCurveCalculator.calculateCurrentTouchDelay(totalMs, totalMs, 0L, maxLagMs)
        assertEquals(1000L, delayFull)

        // t > T_tr (Post-transition): Clamped at 100% of max (1000ms)
        val delayPost = DecayCurveCalculator.calculateCurrentTouchDelay(totalMs + 10_000L, totalMs, 0L, maxLagMs)
        assertEquals(1000L, delayPost)
    }

    @Test
    fun testAcceleratedVeilAlphaMidpointPeaking() {
        val totalMs = 60_000L  // 1 minute transition duration
        val maxVeilAlpha = 0.70f // 70% max opacity

        // t = 0: 0% of max opacity (0.0f)
        val alphaStart = DecayCurveCalculator.calculateCurrentVeilAlpha(0L, totalMs, 0.0f, maxVeilAlpha)
        assertEquals(0.0f, alphaStart, 0.001f)

        // t = 0.25 * T_tr: 50% of max opacity (0.35f)
        val alphaQuarter = DecayCurveCalculator.calculateCurrentVeilAlpha((totalMs * 0.25).toLong(), totalMs, 0.0f, maxVeilAlpha)
        assertEquals(0.35f, alphaQuarter, 0.001f)

        // t = 0.50 * T_tr: 100% of max opacity (0.70f)
        val alphaHalf = DecayCurveCalculator.calculateCurrentVeilAlpha((totalMs * 0.50).toLong(), totalMs, 0.0f, maxVeilAlpha)
        assertEquals(0.70f, alphaHalf, 0.001f)

        // t = 0.75 * T_tr: Clamped at 100% of max opacity (0.70f)
        val alphaThreeQuarter = DecayCurveCalculator.calculateCurrentVeilAlpha((totalMs * 0.75).toLong(), totalMs, 0.0f, maxVeilAlpha)
        assertEquals(0.70f, alphaThreeQuarter, 0.001f)

        // t = 1.00 * T_tr: Clamped at 100% of max opacity (0.70f)
        val alphaFull = DecayCurveCalculator.calculateCurrentVeilAlpha(totalMs, totalMs, 0.0f, maxVeilAlpha)
        assertEquals(0.70f, alphaFull, 0.001f)

        // t > T_tr (Post-transition / session completed): Clamped at 100% of max opacity (0.70f)
        val alphaPost = DecayCurveCalculator.calculateCurrentVeilAlpha(totalMs + 30_000L, totalMs, 0.0f, maxVeilAlpha)
        assertEquals(0.70f, alphaPost, 0.001f)
    }

    @Test
    fun testGrayscaleMidpointActivation() {
        val totalMs = 60_000L // 1 minute transition duration

        // t = 0 (0%): OFF (false)
        val stateStart = DecayCurveCalculator.evaluateGrayscaleState(0L, totalMs)
        assertEquals(false, stateStart)

        // t = 0.25 * T_tr (25%): OFF (false)
        val stateQuarter = DecayCurveCalculator.evaluateGrayscaleState((totalMs * 0.25).toLong(), totalMs)
        assertEquals(false, stateQuarter)

        // t = 0.49 * T_tr (49%): OFF (false)
        val stateJustBeforeMid = DecayCurveCalculator.evaluateGrayscaleState((totalMs * 0.49).toLong(), totalMs)
        assertEquals(false, stateJustBeforeMid)

        // t = 0.50 * T_tr (50%): ON (true)
        val stateMid = DecayCurveCalculator.evaluateGrayscaleState((totalMs * 0.50).toLong(), totalMs)
        assertEquals(true, stateMid)

        // t = 0.75 * T_tr (75%): ON (true)
        val stateThreeQuarter = DecayCurveCalculator.evaluateGrayscaleState((totalMs * 0.75).toLong(), totalMs)
        assertEquals(true, stateThreeQuarter)

        // t = 1.00 * T_tr (100%): ON (true)
        val stateFull = DecayCurveCalculator.evaluateGrayscaleState(totalMs, totalMs)
        assertEquals(true, stateFull)

        // t > T_tr (Post-transition): ON (true)
        val statePost = DecayCurveCalculator.evaluateGrayscaleState(totalMs + 30_000L, totalMs)
        assertEquals(true, statePost)
    }

    @Test
    fun testBlurRadiusCalculation() {
        val blurStart = DecayCurveCalculator.calculateBlurRadius(0.0f, 10, DecayCurveType.LINEAR)
        val blurMid = DecayCurveCalculator.calculateBlurRadius(0.5f, 10, DecayCurveType.LINEAR)
        val blurEnd = DecayCurveCalculator.calculateBlurRadius(1.0f, 10, DecayCurveType.LINEAR)

        assertEquals(0, blurStart)
        assertEquals(5, blurMid)
        assertEquals(10, blurEnd)

        // Also test with default maxBlurRadius = 10
        assertEquals(10, DecayCurveCalculator.calculateBlurRadius(1.0f, type = DecayCurveType.LINEAR))

        // Also test with 80px blur
        assertEquals(40, DecayCurveCalculator.calculateBlurRadius(0.5f, 80, DecayCurveType.LINEAR))
        assertEquals(80, DecayCurveCalculator.calculateBlurRadius(1.0f, 80, DecayCurveType.LINEAR))
    }

    @Test
    fun testUniformLinearVolumeFade() {
        val totalMs = 60_000L // 1 minute transition duration
        val initialVol = 10   // Initial volume = 10

        // t = 0: 100% of initialVol (10)
        val volStart = DecayCurveCalculator.calculateCurrentVolume(0L, totalMs, initialVol)
        assertEquals(10, volStart)

        // t = 0.25 * T_tr: 75% of initialVol (8) -> round(10 * 0.75) = 8
        val volQuarter = DecayCurveCalculator.calculateCurrentVolume((totalMs * 0.25).toLong(), totalMs, initialVol)
        assertEquals(8, volQuarter)

        // t = 0.50 * T_tr: 50% of initialVol (5) -> round(10 * 0.5) = 5
        val volHalf = DecayCurveCalculator.calculateCurrentVolume((totalMs * 0.50).toLong(), totalMs, initialVol)
        assertEquals(5, volHalf)

        // t = 0.75 * T_tr: 25% of initialVol (3) -> round(10 * 0.25) = 3
        val volThreeQuarter = DecayCurveCalculator.calculateCurrentVolume((totalMs * 0.75).toLong(), totalMs, initialVol)
        assertEquals(3, volThreeQuarter)

        // t = 1.00 * T_tr: 0% of initialVol (0)
        val volFull = DecayCurveCalculator.calculateCurrentVolume(totalMs, totalMs, initialVol)
        assertEquals(0, volFull)

        // t > T_tr (Post-transition): Clamped at 0
        val volPost = DecayCurveCalculator.calculateCurrentVolume(totalMs + 10_000L, totalMs, initialVol)
        assertEquals(0, volPost)
    }
}
