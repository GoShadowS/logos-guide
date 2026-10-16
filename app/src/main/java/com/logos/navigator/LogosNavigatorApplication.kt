package com.logos.navigator

import android.app.Application
import android.util.Log
import ru.dgis.sdk.Context as DgisContext
import ru.dgis.sdk.DGis
import ru.dgis.sdk.positioning.DefaultLocationSource
import ru.dgis.sdk.positioning.registerPlatformLocationSource

/** Инициализирует 2ГИС при первом открытии наружной карты и наличии локального ключа. */
class LogosNavigatorApplication : Application() {
    var dgisSdkContext: DgisContext? = null
        private set

    private var dgisLocationSource: DefaultLocationSource? = null
    private var dgisInitializationAttempted = false

    @Synchronized
    fun initializeDgisSdk(): DgisContext? {
        dgisSdkContext?.let { return it }
        if (dgisInitializationAttempted || !hasDgisAccessKey()) return null
        dgisInitializationAttempted = true

        return try {
            val sdkContext = DGis.initialize(applicationContext)
            val locationSource = DefaultLocationSource(applicationContext)
            registerPlatformLocationSource(sdkContext, locationSource)
            dgisLocationSource = locationSource
            dgisSdkContext = sdkContext
            sdkContext
        } catch (exception: Exception) {
            Log.e(TAG, "Не удалось инициализировать SDK 2ГИС", exception)
            null
        }
    }

    private fun hasDgisAccessKey(): Boolean = runCatching {
        assets.open(DGIS_ACCESS_KEY_ASSET).use { it.read() >= 0 }
    }.getOrDefault(false)

    private companion object {
        const val DGIS_ACCESS_KEY_ASSET = "dgissdk.key"
        const val TAG = "LogosNavigator"
    }
}
