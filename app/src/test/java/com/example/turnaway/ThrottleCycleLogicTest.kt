package com.example.turnaway

import org.junit.Assert.assertEquals
import org.junit.Test

class ThrottleCycleLogicTest {

    enum class ExpectedThrottleState {
        OFF, CYCLE_ACTIVE, AUTO_STOPPED
    }

    private fun evaluateThrottleState(elapsedMs: Long, totalTransitionMs: Long): ExpectedThrottleState {
        val midpointMs = totalTransitionMs / 2L
        return when {
            elapsedMs < midpointMs -> ExpectedThrottleState.OFF
            elapsedMs in midpointMs until totalTransitionMs -> ExpectedThrottleState.CYCLE_ACTIVE
            else -> ExpectedThrottleState.AUTO_STOPPED
        }
    }

    @Test
    fun testFirstHalfOfTransition_ThrottlingIsOff() {
        val totalMs = 360_000L // 6 minutes transition
        // 0s, 1m, 2m, 2m59s
        assertEquals(ExpectedThrottleState.OFF, evaluateThrottleState(0L, totalMs))
        assertEquals(ExpectedThrottleState.OFF, evaluateThrottleState(60_000L, totalMs))
        assertEquals(ExpectedThrottleState.OFF, evaluateThrottleState(120_000L, totalMs))
        assertEquals(ExpectedThrottleState.OFF, evaluateThrottleState(179_999L, totalMs))
    }

    @Test
    fun testMidpointActivation_ThrottlingStartsAtFiftyPercent() {
        val totalMs = 360_000L // 6 minutes transition
        val midpointMs = 180_000L // 3 minutes

        assertEquals(ExpectedThrottleState.CYCLE_ACTIVE, evaluateThrottleState(midpointMs, totalMs))
        assertEquals(ExpectedThrottleState.CYCLE_ACTIVE, evaluateThrottleState(240_000L, totalMs))
        assertEquals(ExpectedThrottleState.CYCLE_ACTIVE, evaluateThrottleState(359_999L, totalMs))
    }

    @Test
    fun testTransitionEnd_AutoStopsAtHundredPercent() {
        val totalMs = 360_000L // 6 minutes transition

        assertEquals(ExpectedThrottleState.AUTO_STOPPED, evaluateThrottleState(totalMs, totalMs))
        assertEquals(ExpectedThrottleState.AUTO_STOPPED, evaluateThrottleState(totalMs + 10_000L, totalMs))
    }
}
