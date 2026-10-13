package com.logos.navigator.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalView
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
            primary = accent.copy(red = (accent.red + 0.42f).coerceAtMost(1f), green = (accent.green + 0.42f).coerceAtMost(1f), blue = (accent.blue + 0.42f).coerceAtMost(1f)),
            onPrimary = Color(0xFF10131C),
            primaryContainer = accent.copy(alpha = 0.42f),
            onPrimaryContainer = Color(0xFFE8EAF4),
            secondary = Color(0xFF77D9F3),
            onSecondary = Color(0xFF003543),
            secondaryContainer = Color(0xFF004E60),
            onSecondaryContainer = Color(0xFFC1F1FF),
            tertiary = Color(0xFF74D9BB),
            onTertiary = Color(0xFF00382D),
            background = Color(0xFF10131C),
            onBackground = Color(0xFFE1E3EC),
            surface = Color(0xFF171A24),
            onSurface = Color(0xFFE1E3EC),
            surfaceVariant = Color(0xFF292D39),
            onSurfaceVariant = Color(0xFFC2C6D4),
            outline = Color(0xFF8C91A0),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.14f),
            onPrimaryContainer = Color(0xFF171A25),
            secondary = Color(0xFF087E9D),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFB9F0FF),
            onSecondaryContainer = Color(0xFF003545),
            tertiary = Color(0xFF00856A),
            onTertiary = Color.White,
            background = Color(0xFFF5F7FC),
            onBackground = Color(0xFF171A25),
            surface = Color.White,
            onSurface = Color(0xFF171A25),
            surfaceVariant = Color(0xFFE8EBF4),
            onSurfaceVariant = Color(0xFF454957),
            outline = Color(0xFF767B8B),
        )
    }
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() }
    SideEffect {
        activity?.window?.let { window ->
            val systemBarColor = if (darkTheme) android.graphics.Color.rgb(16, 19, 28) else android.graphics.Color.rgb(245, 247, 252)
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
        MaterialTheme(colorScheme = colorScheme, typography = Typography(), content = content)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
