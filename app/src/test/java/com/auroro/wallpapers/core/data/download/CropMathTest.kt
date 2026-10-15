package com.auroro.wallpapers.core.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Slice-14 crop regression: no sequence of gesture steps may leave the crop frame uncovered.
 * Covers the clamp bounds, the centroid-zoom compensation (the black-region bug) and the
 * cover-fit minimum scale for portrait, landscape, square and ultrawide sources.
 */
class CropMathTest {

    private data class Case(val iw: Int, val ih: Int, val vw: Float, val vh: Float)

    private val cases = listOf(
        Case(2000, 1500, 1080f, 2340f),   // landscape source, portrait frame
        Case(1500, 2000, 1080f, 2340f),   // portrait source, portrait frame
        Case(1080, 1080, 1080f, 2340f),   // square source
        Case(6000, 1000, 1080f, 2340f),   // ultrawide panorama
        Case(2000, 1500, 2340f, 1080f),   // landscape frame
        Case(1500, 2000, 1080f, 1080f),   // square frame
    )

    @Test fun clampedOffsetsAlwaysKeepTheFrameCovered() {
        cases.forEach { case ->
            listOf(1f, 1.7f, 4f).forEach { userScale ->
                val base = maxOf(case.vw / case.iw, case.vh / case.ih)
                val s = base * userScale
                // Wild offsets, far outside any sane pan: the clamp must pull them back inside
                // the cover bounds so the scaled image still spans the whole frame.
                listOf(-5000f, -300f, 0f, 450f, 5000f).forEach { ox ->
                    listOf(-5000f, -200f, 0f, 600f, 5000f).forEach { oy ->
                        val (cx, cy) = CropMath.clampOffset(case.iw, case.ih, case.vw, case.vh, s, ox, oy)
                        val maxX = (case.iw * s - case.vw) / 2f
                        val maxY = (case.ih * s - case.vh) / 2f
                        assertTrue("x clamp", abs(cx) <= maxX + 0.01f)
                        assertTrue("y clamp", abs(cy) <= maxY + 0.01f)
                        val rect = CropMath.visibleRect(case.iw, case.ih, case.vw, case.vh, userScale, ox, oy)
                        assertTrue("left in range", rect.left >= -0.001f && rect.left <= 1.001f)
                        assertTrue("right in range", rect.right >= -0.001f && rect.right <= 1.001f)
                        // The visible window must fit inside the image and match the frame ratio.
                        val wFrac = rect.right - rect.left
                        val hFrac = rect.bottom - rect.top
                        assertTrue("window width", wFrac > 0f && wFrac <= 1.001f)
                        assertTrue("window height", hFrac > 0f && hFrac <= 1.001f)
                        assertEquals(
                            "window keeps frame aspect",
                            case.vw / case.vh,
                            (wFrac * case.iw) / (hFrac * case.ih),
                            0.02f,
                        )
                    }
                }
            }
        }
    }

    @Test fun minimumScalePreservesCoverFit() {
        cases.forEach { case ->
            val base = maxOf(case.vw / case.iw, case.vh / case.ih)
            val rect = CropMath.visibleRect(case.iw, case.ih, case.vw, case.vh, 1f, 0f, 0f)
            // At the minimum zoom the visible window is exactly the frame over the cover-scaled
            // image: its pixel size equals the frame, i.e. nothing outside the image is shown.
            assertEquals(case.vw, (rect.right - rect.left) * case.iw * base, 0.5f)
            assertEquals(case.vh, (rect.bottom - rect.top) * case.ih * base, 0.5f)
        }
    }

    @Test fun zoomAroundCentreMatchesPlainPanWhenZoomIsOne() {
        val (x, y) = CropMath.applyZoomPan(
            offsetX = 12f, offsetY = -8f,
            centroidX = 300f, centroidY = 400f,
            centerX = 300f, centerY = 400f,
            zoom = 1f, panX = 5f, panY = 6f,
        )
        assertEquals(17f, x, 1e-4f)
        assertEquals(-2f, y, 1e-4f)
    }

    @Test fun offCentrePinchCompensationKeepsThePinchedPointFixed() {
        // Pinching at a point must keep the content under that point under it: applying the
        // compensation and then mapping the centroid through the layer transform must be a
        // fixed point of the zoom step.
        val centerX = 540f
        val centerY = 1170f
        val centroidX = 200f
        val centroidY = 900f
        val zoom = 1.35f
        val ox = 40f
        val oy = -60f
        val (nx, ny) = CropMath.applyZoomPan(ox, oy, centroidX, centroidY, centerX, centerY, zoom, 0f, 0f)
        fun mapAxis(center: Float, offset: Float, scale: Float, px: Float) = center + scale * (px - center) + offset
        // Content point that sat under the centroid before the zoom:
        val beforeX = (centroidX - centerX - ox) / 1f + centerX
        val afterX = mapAxis(centerX, nx, zoom, beforeX)
        assertEquals(centroidX, afterX, 0.01f)
        val beforeY = (centroidY - centerY - oy) / 1f + centerY
        val afterY = mapAxis(centerY, ny, zoom, beforeY)
        assertEquals(centroidY, afterY, 0.01f)
    }

    @Test fun resetRestoresCentredCoverFit() {
        cases.forEach { case ->
            val rect = CropMath.centerCrop(case.iw, case.ih, case.vw.toInt(), case.vh.toInt())
            assertEquals((1f - case.vw / (case.iw * maxOf(case.vw / case.iw, case.vh / case.ih))) / 2f, rect.left, 0.01f)
        }
    }
}
