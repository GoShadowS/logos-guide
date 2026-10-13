package com.logos.navigator.svg

import android.content.Context
import com.caverock.androidsvg.SVG
import com.logos.navigator.R
import java.io.IOException

/** Загружает и парсит SVG непосредственно из assets без промежуточного PNG/Bitmap. */
object SvgLoader {
    fun load(context: Context, assetPath: String): SVG {
        val appContext = context.applicationContext
        require(assetPath.endsWith(".svg", ignoreCase = true)) {
            appContext.getString(R.string.svg_loader_invalid_asset)
        }
        return try {
            appContext.assets.open(assetPath).use { input -> SVG.getFromInputStream(input) }
        } catch (exception: Exception) {
            throw IOException(
                appContext.getString(
                    R.string.svg_loader_error,
                    assetPath,
                    exception.localizedMessage ?: appContext.getString(R.string.unknown_error),
                ),
                exception,
            )
        }
    }
}
