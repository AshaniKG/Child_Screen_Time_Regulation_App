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

        assertEquals(60, fpsStart)
        assertEquals(32, fpsMid)
        assertEquals(5, fpsEnd)
    }

    @Test
    fun testTouchDelayMsCalculation() {
        val delayStart = DecayCurveCalculator.calculateTouchDelayMs(0.0f, 800L, DecayCurveType.LINEAR)
        val delayMid = DecayCurveCalculator.calculateTouchDelayMs(0.5f, 800L, DecayCurveType.LINEAR)
        val delayEnd = DecayCurveCalculator.calculateTouchDelayMs(1.0f, 800L, DecayCurveType.LINEAR)

        assertEquals(0L, delayStart)
        assertEquals(400L, delayMid)
        assertEquals(800L, delayEnd)
    }
}
