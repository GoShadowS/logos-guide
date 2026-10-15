package com.logos.navigator.svg

import com.logos.navigator.data.model.SvgRenderQuality

/** Ограничения кэша и запас по разрешению для каждого режима качества SVG. */
data class SvgRenderProfile(
    val maximumBitmapPixels: Int,
    val oversampling: Float,
)

fun SvgRenderQuality.renderProfile(): SvgRenderProfile = when (this) {
    SvgRenderQuality.LOW -> SvgRenderProfile(maximumBitmapPixels = 1_200_000, oversampling = 1f)
    SvgRenderQuality.MEDIUM -> SvgRenderProfile(maximumBitmapPixels = 2_400_000, oversampling = 1.2f)
    SvgRenderQuality.HIGH -> SvgRenderProfile(maximumBitmapPixels = 4_000_000, oversampling = 1.5f)
}
