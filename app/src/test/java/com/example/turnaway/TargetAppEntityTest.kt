package com.example.turnaway

import com.example.turnaway.data.entity.TargetAppEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetAppEntityTest {

    @Test
    fun testTargetAppEntityCreationAndDefaults() {
        val app = TargetAppEntity(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            isTargeted = true
        )

        assertEquals("com.google.android.youtube", app.packageName)
        assertEquals("YouTube", app.appName)
        assertTrue(app.isTargeted)
    }

    @Test
    fun testTargetAppEntityCopyToggle() {
        val app = TargetAppEntity(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            isTargeted = false
        )

        assertFalse(app.isTargeted)
        val toggled = app.copy(isTargeted = true)
        assertTrue(toggled.isTargeted)
    }

    @Test
    fun testBatchTargetFiltering() {
        val apps = listOf(
            TargetAppEntity("com.google.android.youtube", "YouTube", true),
            TargetAppEntity("com.whatsapp", "WhatsApp", false),
            TargetAppEntity("com.android.chrome", "Chrome", true)
        )

        val targeted = apps.filter { it.isTargeted }.map { it.packageName }.toSet()
        assertEquals(2, targeted.size)
        assertTrue(targeted.contains("com.google.android.youtube"))
        assertTrue(targeted.contains("com.android.chrome"))
        assertFalse(targeted.contains("com.whatsapp"))
    }
}
