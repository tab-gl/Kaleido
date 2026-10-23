package com.tabgl.kaleido.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EditStateTest {

    private val identity = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    @Test
    fun defaultEditLeavesColoursUnchanged() {
        val m = EditState().colorMatrix()
        assertEquals(20, m.size)
        m.forEachIndexed { i, v -> assertEquals("index $i", identity[i], v, 1e-4f) }
    }

    @Test
    fun brightnessShiftsEveryChannelOffset() {
        val m = EditState(brightness = 0.2f).colorMatrix()
        // Offsets sit in the last column of each colour row (indexes 4, 9 and 14).
        assertEquals(0.2f * 255f, m[4], 0.01f)
        assertEquals(0.2f * 255f, m[9], 0.01f)
        assertEquals(0.2f * 255f, m[14], 0.01f)
    }

    @Test
    fun zeroSaturationMakesGreyscaleRows() {
        val m = EditState(saturation = 0f).colorMatrix()
        // Every colour row uses the same luma weights, so the three rows match column by column.
        for (col in 0..2) {
            assertEquals(m[col], m[5 + col], 1e-4f)
            assertEquals(m[col], m[10 + col], 1e-4f)
        }
    }
}
