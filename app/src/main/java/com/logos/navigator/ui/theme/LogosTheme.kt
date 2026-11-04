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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.logos.navigator.R
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
        Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp),
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

private fun logosLightColors(accentArgb: Int) = lightColorScheme(
    primary = Color(accentArgb),
    onPrimary = Color.White,
    primaryContainer = LogosColors.BluePale,
    onPrimaryContainer = LogosColors.BlueNight,
    inversePrimary = LogosColors.NightBlue,
    secondary = LogosColors.Muted,
    onSecondary = Color.White,
    secondaryContainer = LogosColors.BlueSoft,
    onSecondaryContainer = LogosColors.BlueNight,
    tertiary = Color(0xFF8A6100),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF1CC),
    onTertiaryContainer = Color(0xFF3D2C00),
    background = LogosColors.Background,
    onBackground = LogosColors.Ink,
    surface = LogosColors.Surface,
    onSurface = LogosColors.Ink,
    surfaceVariant = LogosColors.SurfaceSoft,
    onSurfaceVariant = LogosColors.Muted,
    surfaceTint = Color(accentArgb),
    inverseSurface = LogosColors.Ink,
    inverseOnSurface = Color(0xFFF3F6FB),
    outline = LogosColors.Line,
    outlineVariant = LogosColors.LineSoft,
    error = Color(0xFFC0313F),
    errorContainer = Color(0xFFFFE3E5),
    onErrorContainer = Color(0xFF5C0A13),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDDE3EE),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color(0xFFF3F6FB),
    surfaceContainerHigh = LogosColors.SurfaceSoft,
    surfaceContainerHighest = Color(0xFFE6EBF4),
)

private fun logosDarkColors(accentArgb: Int): androidx.compose.material3.ColorScheme {
    val primary = if (accentArgb in AppSettings.BRAND_BLUES) LogosColors.NightBlue else Color(accentArgb).lighten(0.42f)
    return darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF0B1433),
        primaryContainer = LogosColors.NightBluePale,
        onPrimaryContainer = Color(0xFFE2E8FF),
        inversePrimary = LogosColors.Blue,
        secondary = LogosColors.NightMuted,
        onSecondary = LogosColors.NightBackground,
        secondaryContainer = LogosColors.NightBlueSoft,
        onSecondaryContainer = Color(0xFFDCE3F7),
        tertiary = LogosColors.Yellow,
        onTertiary = Color(0xFF2A210A),
        tertiaryContainer = Color(0xFF3F3417),
        onTertiaryContainer = Color(0xFFFFEFC2),
        background = LogosColors.NightBackground,
        onBackground = LogosColors.NightInk,
        surface = LogosColors.NightSurface,
        onSurface = LogosColors.NightInk,
        surfaceVariant = LogosColors.NightSurfaceSoft,
        onSurfaceVariant = LogosColors.NightMuted,
        surfaceTint = primary,
        inverseSurface = LogosColors.NightInk,
        inverseOnSurface = LogosColors.NightSurface,
        outline = LogosColors.NightLine,
        outlineVariant = LogosColors.NightLineSoft,
        error = Color(0xFFFF8A94),
        errorContainer = Color(0xFF4E1820),
        onErrorContainer = Color(0xFFFFDADD),
        surfaceBright = LogosColors.NightSurfaceRaised,
        surfaceDim = LogosColors.NightBackground,
        surfaceContainerLowest = Color(0xFF0C0E14),
        surfaceContainerLow = Color(0xFF151822),
        surfaceContainer = LogosColors.NightSurface,
        surfaceContainerHigh = LogosColors.NightSurfaceSoft,
        surfaceContainerHighest = LogosColors.NightSurfaceRaised,
    )
}

/** Onest — the typeface of logos-guide.ru; static instances so weights also work before Android 8. */
private val Onest = FontFamily(
    Font(R.font.onest_regular, FontWeight.Normal),
    Font(R.font.onest_medium, FontWeight.Medium),
    Font(R.font.onest_semibold, FontWeight.SemiBold),
    Font(R.font.onest_bold, FontWeight.Bold),
)

private fun logosTypography(): Typography {
    val defaults = Typography()
    fun androidx.compose.ui.text.TextStyle.onest(weight: FontWeight? = null, spacing: Float? = null) = copy(
        fontFamily = Onest,
        fontWeight = weight ?: fontWeight,
        letterSpacing = spacing?.sp ?: letterSpacing,
    )
    return defaults.copy(
        displayLarge = defaults.displayLarge.onest(FontWeight.Bold, -1.2f),
        displayMedium = defaults.displayMedium.onest(FontWeight.Bold, -0.9f),
        displaySmall = defaults.displaySmall.onest(FontWeight.Bold, -0.7f),
        headlineLarge = defaults.headlineLarge.onest(FontWeight.Bold, -0.6f),
        headlineMedium = defaults.headlineMedium.onest(FontWeight.Bold, -0.5f),
        headlineSmall = defaults.headlineSmall.onest(FontWeight.SemiBold, -0.3f),
        titleLarge = defaults.titleLarge.onest(FontWeight.SemiBold, -0.3f),
        titleMedium = defaults.titleMedium.onest(FontWeight.SemiBold, -0.1f),
        titleSmall = defaults.titleSmall.onest(FontWeight.SemiBold, 0f),
        labelLarge = defaults.labelLarge.onest(FontWeight.SemiBold, 0f),
        labelMedium = defaults.labelMedium.onest(FontWeight.Medium, 0f),
        labelSmall = defaults.labelSmall.onest(FontWeight.Medium, 0f),
        bodyLarge = defaults.bodyLarge.onest(spacing = 0f),
        bodyMedium = defaults.bodyMedium.onest(spacing = 0f),
        bodySmall = defaults.bodySmall.onest(spacing = 0.1f),
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
