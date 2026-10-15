package com.logos.navigator.svg

import com.logos.navigator.data.model.SvgRenderQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgRenderProfileTest {
    @Test
    fun `higher quality allocates a larger map cache and oversampling factor`() {
        val low = SvgRenderQuality.LOW.renderProfile()
        val medium = SvgRenderQuality.MEDIUM.renderProfile()
        val high = SvgRenderQuality.HIGH.renderProfile()

        assertTrue(low.maximumBitmapPixels < medium.maximumBitmapPixels)
        assertTrue(medium.maximumBitmapPixels < high.maximumBitmapPixels)
        assertTrue(low.oversampling < medium.oversampling)
        assertTrue(medium.oversampling < high.oversampling)
    }

    @Test
    fun `render profiles stay within the intended mobile memory budget`() {
        assertEquals(1_200_000, SvgRenderQuality.LOW.renderProfile().maximumBitmapPixels)
        assertEquals(2_400_000, SvgRenderQuality.MEDIUM.renderProfile().maximumBitmapPixels)
        assertEquals(4_000_000, SvgRenderQuality.HIGH.renderProfile().maximumBitmapPixels)
    }
}
