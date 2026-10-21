package com.logos.navigator.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import com.logos.navigator.data.model.AppSettings
import com.logos.navigator.data.model.ThemeMode

@Composable
fun LogosTheme(
    themeMode: ThemeMode,
    accentColorArgb: Int,
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = remember(darkTheme, accentColorArgb) {
        if (darkTheme) logosDarkColors(accentColorArgb) else logosLightColors(accentColorArgb)
    }
    val typography = remember { logosTypography() }
    val shapes = remember {
        // Сайт использует чёткие рамки и умеренные радиусы 8–14 px.
        Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(14.dp),
        )
    }
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() }
    SideEffect {
        activity?.window?.let { window ->
            val systemBarColor = colorScheme.background.toArgb()
            window.statusBarColor = systemBarColor
            window.navigationBarColor = systemBarColor
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * fontScale.coerceIn(0.8f, 1.6f),
        ),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}

/** Палитра logos-guide.ru: глубокий синий акцент, холодный светлый фон и тёмно-синий текст. */
private fun logosLightColors(accentArgb: Int) = lightColorScheme(
    primary = Color(accentArgb),
    onPrimary = Color.White,
    primaryContainer = LogosColors.BluePale,
    onPrimaryContainer = LogosColors.BlueDeep,
    inversePrimary = LogosColors.BlueNight,
    secondary = LogosColors.Muted,
    onSecondary = Color.White,
    secondaryContainer = LogosColors.BlueSoft,
    onSecondaryContainer = LogosColors.BlueDeep,
    tertiary = Color(0xFF9A6A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEFC2),
    onTertiaryContainer = Color(0xFF3D2C00),
    background = LogosColors.Background,
    onBackground = LogosColors.Ink,
    surface = Color.White,
    onSurface = LogosColors.Ink,
    surfaceVariant = LogosColors.SurfaceSoft,
    onSurfaceVariant = LogosColors.Muted,
    surfaceTint = Color(accentArgb),
    inverseSurface = LogosColors.BlueDeep,
    inverseOnSurface = Color(0xFFF2F7FF),
    outline = LogosColors.Line,
    outlineVariant = Color(0xFFDCE4F1),
    error = Color(0xFFC0313F),
    errorContainer = Color(0xFFFFE3E5),
    onErrorContainer = Color(0xFF5C0A13),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDCE3F0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F9FD),
    surfaceContainer = Color(0xFFF4F7FC),
    surfaceContainerHigh = Color(0xFFEEF2FA),
    surfaceContainerHighest = LogosColors.SurfaceSoft,
)

private fun logosDarkColors(accentArgb: Int): androidx.compose.material3.ColorScheme {
    val primary = if (accentArgb in AppSettings.BRAND_BLUES) LogosColors.BlueNightAccent else Color(accentArgb).lighten(0.42f)
    return darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF04122E),
        primaryContainer = Color(0xFF17479E),
        onPrimaryContainer = Color(0xFFE5EDFF),
        inversePrimary = LogosColors.Blue,
        secondary = Color(0xFFB3C8EE),
        onSecondary = Color(0xFF0A1C3F),
        secondaryContainer = Color(0xFF16305E),
        onSecondaryContainer = Color(0xFFDCE8FA),
        tertiary = Color(0xFFFFD267),
        onTertiary = Color(0xFF2A210A),
        tertiaryContainer = Color(0xFF4A3A10),
        onTertiaryContainer = Color(0xFFFFEFC2),
        background = Color(0xFF040B1D),
        onBackground = Color(0xFFF2F7FF),
        surface = Color(0xFF0A1C3F),
        onSurface = Color(0xFFF2F7FF),
        surfaceVariant = Color(0xFF122A5C),
        onSurfaceVariant = Color(0xFFB3C8EE),
        surfaceTint = primary,
        inverseSurface = Color(0xFFE5EDFB),
        inverseOnSurface = Color(0xFF0A1C3F),
        outline = Color(0xFF2C4D85),
        outlineVariant = Color(0xFF1C3766),
        error = Color(0xFFFF8A94),
        errorContainer = Color(0xFF5C1520),
        onErrorContainer = Color(0xFFFFDADD),
        surfaceBright = Color(0xFF16305E),
        surfaceDim = Color(0xFF040B1D),
        surfaceContainerLowest = Color(0xFF030818),
        surfaceContainerLow = Color(0xFF071530),
        surfaceContainer = Color(0xFF0A1C3F),
        surfaceContainerHigh = Color(0xFF0E244D),
        surfaceContainerHighest = Color(0xFF122A5C),
    )
}

/** Чистый гротеск без засечек, как на сайте; заголовки плотнее и жирнее основного текста. */
private fun logosTypography(): Typography {
    val defaults = Typography()
    val sans = FontFamily.SansSerif
    fun androidx.compose.ui.text.TextStyle.heading(weight: FontWeight, spacing: Float) =
        copy(fontFamily = sans, fontWeight = weight, letterSpacing = spacing.sp)
    return defaults.copy(
        displayLarge = defaults.displayLarge.heading(FontWeight.Bold, -0.5f),
        displayMedium = defaults.displayMedium.heading(FontWeight.Bold, -0.4f),
        displaySmall = defaults.displaySmall.heading(FontWeight.Bold, -0.3f),
        headlineLarge = defaults.headlineLarge.heading(FontWeight.Bold, -0.3f),
        headlineMedium = defaults.headlineMedium.heading(FontWeight.Bold, -0.2f),
        headlineSmall = defaults.headlineSmall.heading(FontWeight.SemiBold, -0.1f),
        titleLarge = defaults.titleLarge.heading(FontWeight.SemiBold, -0.1f),
        titleMedium = defaults.titleMedium.heading(FontWeight.SemiBold, 0f),
        titleSmall = defaults.titleSmall.heading(FontWeight.SemiBold, 0f),
        labelLarge = defaults.labelLarge.heading(FontWeight.SemiBold, 0.1f),
        labelMedium = defaults.labelMedium.heading(FontWeight.Medium, 0.2f),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = sans),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = sans),
        bodySmall = defaults.bodySmall.copy(fontFamily = sans),
    )
}

private fun Color.lighten(amount: Float) = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
