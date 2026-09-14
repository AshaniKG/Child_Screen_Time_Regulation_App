package com.example.turnaway

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorDesaturationMatrixTest {

    private fun computeColorMatrix(s: Float): FloatArray {
        val lr = 0.2126f
        val lg = 0.7152f
        val lb = 0.0722f

        return floatArrayOf(
            lr + (1 - lr) * s, lg * (1 - s),       lb * (1 - s),       0f, 0f,
            lr * (1 - s),       lg + (1 - lg) * s, lb * (1 - s),       0f, 0f,
            lr * (1 - s),       lg * (1 - s),       lb + (1 - lb) * s, 0f, 0f,
            0f,                 0f,                 0f,                 1f, 0f
        )
    }

    @Test
    fun testFullySaturatedMatrixIsIdentity() {
        val matrix = computeColorMatrix(1.0f)
        assertEquals(1.0f, matrix[0], 0.001f) // a11
        assertEquals(0.0f, matrix[1], 0.001f) // a12
        assertEquals(0.0f, matrix[2], 0.001f) // a13
        assertEquals(1.0f, matrix[6], 0.001f) // a22
        assertEquals(1.0f, matrix[12], 0.001f) // a33
    }

    @Test
    fun testFullyDesaturatedMatrixUsesLuminanceWeights() {
        val matrix = computeColorMatrix(0.0f)

        // Row 1 (Red output)
        assertEquals(0.2126f, matrix[0], 0.001f)
        assertEquals(0.7152f, matrix[1], 0.001f)
        assertEquals(0.0722f, matrix[2], 0.001f)

        // Row 2 (Green output)
        assertEquals(0.2126f, matrix[5], 0.001f)
        assertEquals(0.7152f, matrix[6], 0.001f)
        assertEquals(0.0722f, matrix[7], 0.001f)

        // Row 3 (Blue output)
        assertEquals(0.2126f, matrix[10], 0.001f)
        assertEquals(0.7152f, matrix[11], 0.001f)
        assertEquals(0.0722f, matrix[12], 0.001f)
    }
}
