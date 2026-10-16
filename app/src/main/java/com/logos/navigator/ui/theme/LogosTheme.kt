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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
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
    val accent = Color(accentColorArgb)
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accent.copy(
                red = (accent.red + 0.42f).coerceAtMost(1f),
                green = (accent.green + 0.42f).coerceAtMost(1f),
                blue = (accent.blue + 0.42f).coerceAtMost(1f),
            ),
            onPrimary = Color(0xFF07162E),
            primaryContainer = Color(0xFF23416E),
            onPrimaryContainer = Color(0xFFEDF4FF),
            secondary = Color(0xFFA8BDD7),
            onSecondary = Color(0xFF0B1D36),
            secondaryContainer = Color(0xFF142D53),
            onSecondaryContainer = Color(0xFFDCE8FA),
            tertiary = Color(0xFFFFD267),
            onTertiary = Color(0xFF2A210A),
            background = Color(0xFF07162E),
            onBackground = Color(0xFFE5EDFB),
            surface = Color(0xFF102342),
            onSurface = Color(0xFFE5EDFB),
            surfaceVariant = Color(0xFF142D53),
            onSurfaceVariant = Color(0xFFA8BAD5),
            outline = Color(0xFF627A9E),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE6EEFF),
            onPrimaryContainer = Color(0xFF0D1C3B),
            secondary = Color(0xFF4D668C),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE3ECFA),
            onSecondaryContainer = Color(0xFF142D53),
            tertiary = Color(0xFF9B6700),
            onTertiary = Color.White,
            background = Color(0xFFF4F7FC),
            onBackground = Color(0xFF0D1C3B),
            surface = Color.White,
            onSurface = Color(0xFF0D1C3B),
            surfaceVariant = Color(0xFFEAF0FA),
            onSurfaceVariant = Color(0xFF667793),
            outline = Color(0xFFA9B8D0),
        )
    }
    val typography = remember {
        val defaults = Typography()
        defaults.copy(
            displayLarge = defaults.displayLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal),
            displayMedium = defaults.displayMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal),
            displaySmall = defaults.displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal),
            headlineLarge = defaults.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
            headlineMedium = defaults.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
            headlineSmall = defaults.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
            titleLarge = defaults.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
            titleMedium = defaults.titleMedium.copy(letterSpacing = 0.sp),
        )
    }
    val shapes = remember {
        Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        )
    }
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() }
    SideEffect {
        activity?.window?.let { window ->
            val systemBarColor = if (darkTheme) {
                android.graphics.Color.rgb(7, 22, 46)
            } else {
                android.graphics.Color.rgb(244, 247, 252)
            }
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
