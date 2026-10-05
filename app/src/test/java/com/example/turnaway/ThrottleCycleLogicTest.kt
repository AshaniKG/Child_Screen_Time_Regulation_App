package com.example.turnaway

import org.junit.Assert.assertEquals
import org.junit.Test

class ThrottleCycleLogicTest {

    enum class ExpectedThrottleState {
        DROP_ACTIVE, NORMAL_CONNECTIVITY
    }

    private fun evaluateThrottleDropState(elapsedMs: Long, totalTransitionMs: Long): ExpectedThrottleState {
        if (elapsedMs >= totalTransitionMs) {
            return ExpectedThrottleState.NORMAL_CONNECTIVITY
        }
        val halfTransitionMs = totalTransitionMs / 2L
        if (elapsedMs < halfTransitionMs) {
            return ExpectedThrottleState.NORMAL_CONNECTIVITY
        }
        val activeElapsedMs = elapsedMs - halfTransitionMs
        val cyclePosMs = activeElapsedMs % 120_000L
        return if (cyclePosMs < 60_000L) {
            ExpectedThrottleState.DROP_ACTIVE
        } else {
            ExpectedThrottleState.NORMAL_CONNECTIVITY
        }
    }

    @Test
    fun testFirstHalfOfTransition_NetworkDropIsInactive() {
        val totalTransitionMs = 600_000L // 10 minutes
        // First 5 minutes (0s, 2.5 min, 4.99 min): Normal connectivity
        assertEquals(ExpectedThrottleState.NORMAL_CONNECTIVITY, evaluateThrottleDropState(0L, totalTransitionMs))
        assertEquals(ExpectedThrottleState.NORMAL_CONNECTIVITY, evaluateThrottleDropState(150_000L, totalTransitionMs))
        assertEquals(ExpectedThrottleState.NORMAL_CONNECTIVITY, evaluateThrottleDropState(299_999L, totalTransitionMs))
    }

    @Test
    fun testSecondHalfOfTransition_AlternatingThrottleCycleEngages() {
        val totalTransitionMs = 600_000L // 10 minutes (Half = 300,000ms / 5 min)
        // Minute 5 (300,000ms to 359,999ms): 1-minute drop ON
        assertEquals(ExpectedThrottleState.DROP_ACTIVE, evaluateThrottleDropState(300_000L, totalTransitionMs))
        assertEquals(ExpectedThrottleState.DROP_ACTIVE, evaluateThrottleDropState(330_000L, totalTransitionMs))

        // Minute 6 (360,000ms to 419,999ms): 1-minute normal connectivity RESTORED
        assertEquals(ExpectedThrottleState.NORMAL_CONNECTIVITY, evaluateThrottleDropState(360_000L, totalTransitionMs))
        assertEquals(ExpectedThrottleState.NORMAL_CONNECTIVITY, evaluateThrottleDropState(390_000L, totalTransitionMs))

        // Minute 7 (420,000ms to 479,999ms): 1-minute drop ON
        assertEquals(ExpectedThrottleState.DROP_ACTIVE, evaluateThrottleDropState(420_000L, totalTransitionMs))
    }

    @Test
    fun testTransitionCompletion_NetworkDropStopsAutomatically() {
        val totalTransitionMs = 600_000L // 10 minutes
        // At or beyond 10 minutes (600,000ms): Normal connectivity restored
        assertEquals(ExpectedThrottleState.NORMAL_CONNECTIVITY, evaluateThrottleDropState(600_000L, totalTransitionMs))
        assertEquals(ExpectedThrottleState.NORMAL_CONNECTIVITY, evaluateThrottleDropState(650_000L, totalTransitionMs))
    }
}
