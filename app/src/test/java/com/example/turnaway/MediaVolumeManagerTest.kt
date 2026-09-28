package com.example.turnaway

import com.example.turnaway.engine.DecayCurveCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaVolumeManagerTest {

    @Test
    fun testVolumeFadeCalculations() {
        val capturedVol = 12
        val totalMs = 10 * 60 * 1000L // 10 minutes

        // t = 0: 100% of baseline (12)
        val v0 = DecayCurveCalculator.calculateCurrentVolume(0L, totalMs, capturedVol)
        assertEquals(12, v0)

        // t = 2.5m (25% progress): 75% of baseline (9)
        val v25 = DecayCurveCalculator.calculateCurrentVolume((totalMs * 0.25).toLong(), totalMs, capturedVol)
        assertEquals(9, v25)

        // t = 5m (50% progress): 50% of baseline (6)
        val v50 = DecayCurveCalculator.calculateCurrentVolume((totalMs * 0.50).toLong(), totalMs, capturedVol)
        assertEquals(6, v50)

        // t = 7.5m (75% progress): 25% of baseline (3)
        val v75 = DecayCurveCalculator.calculateCurrentVolume((totalMs * 0.75).toLong(), totalMs, capturedVol)
        assertEquals(3, v75)

        // t = 10m (100% progress): 0% of baseline (0)
        val v100 = DecayCurveCalculator.calculateCurrentVolume(totalMs, totalMs, capturedVol)
        assertEquals(0, v100)

        // t > 10m (post transition): Clamped at 0
        val vPost = DecayCurveCalculator.calculateCurrentVolume(totalMs + 5000L, totalMs, capturedVol)
        assertEquals(0, vPost)
    }
}
