package com.auroro.wallpapers.core.data.download

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The crop editor hands the applier a rect in the ROTATED image space plus a clockwise
 * rotation; [CropMath.rotateRectBack] must map that rect onto the original pixels exactly,
 * otherwise a rotated wallpaper would apply the wrong region.
 */
class CropRotationMathTest {

    private fun assertClose(expected: NormalizedCrop, actual: NormalizedCrop) {
        assertTrue(abs(expected.left - actual.left) < 1e-5f, "left: ${actual.left}")
        assertTrue(abs(expected.top - actual.top) < 1e-5f, "top: ${actual.top}")
        assertTrue(abs(expected.right - actual.right) < 1e-5f, "right: ${actual.right}")
        assertTrue(abs(expected.bottom - actual.bottom) < 1e-5f, "bottom: ${actual.bottom}")
    }

    @Test
    fun `zero and full rotations are identity`() {
        val c = NormalizedCrop(0.2f, 0.3f, 0.6f, 0.8f)
        assertEquals(c, CropMath.rotateRectBack(c, 0))
        assertEquals(c, CropMath.rotateRectBack(c, 360))
        assertEquals(c, CropMath.rotateRectBack(c, -360))
    }

    @Test
    fun `90 clockwise maps corners correctly`() {
        val c = NormalizedCrop(0.25f, 0.5f, 0.75f, 1.0f)
        // left = top', top = 1 - right', right = bottom', bottom = 1 - left'
        assertClose(NormalizedCrop(0.5f, 0.25f, 1.0f, 0.75f), CropMath.rotateRectBack(c, 90))
    }

    @Test
    fun `180 mirrors both axes`() {
        val c = NormalizedCrop(0.2f, 0.3f, 0.6f, 0.8f)
        assertClose(NormalizedCrop(0.4f, 0.2f, 0.8f, 0.7f), CropMath.rotateRectBack(c, 180))
    }

    @Test
    fun `270 clockwise maps corners correctly`() {
        val c = NormalizedCrop(0.2f, 0.3f, 0.6f, 0.8f)
        // left = 1 - bottom', top = left', right = 1 - top', bottom = right'
        assertClose(NormalizedCrop(0.2f, 0.2f, 0.7f, 0.6f), CropMath.rotateRectBack(c, 270))
    }

    @Test
    fun `negative rotations normalize to clockwise`() {
        val c = NormalizedCrop(0.1f, 0.2f, 0.55f, 0.9f)
        assertEquals(CropMath.rotateRectBack(c, 90), CropMath.rotateRectBack(c, -270))
        assertEquals(CropMath.rotateRectBack(c, 270), CropMath.rotateRectBack(c, -90))
    }

    @Test
    fun `four quarter turns return the original rect`() {
        var c = NormalizedCrop(0.13f, 0.29f, 0.71f, 0.83f)
        val original = c
        repeat(4) { c = CropMath.rotateRectBack(c, 90) }
        assertClose(original, c)
    }

    @Test
    fun `two half turns return the original rect`() {
        var c = NormalizedCrop(0.05f, 0.4f, 0.6f, 0.95f)
        val original = c
        repeat(2) { c = CropMath.rotateRectBack(c, 180) }
        assertClose(original, c)
    }

    @Test
    fun `full-frame crop survives any rotation`() {
        val full = NormalizedCrop(0f, 0f, 1f, 1f)
        listOf(0, 90, 180, 270).forEach { rot ->
            assertClose(full, CropMath.rotateRectBack(full, rot))
        }
    }
}
