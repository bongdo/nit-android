package space.bongdo.nit.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val LocalIsDark = staticCompositionLocalOf { true }
val LocalSpark = staticCompositionLocalOf { Color.Unspecified }
val LocalSuccess = staticCompositionLocalOf { Color.Unspecified }

private fun typography(): Typography {
    val base = Typography()
    return base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
        titleLarge = base.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.6.sp,
        ),
    )
}

val CodeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, lineHeight = 19.sp)

@Composable
fun NitTheme(
    theme: String,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val (palette, dark) = themeSpec(theme, systemDark)
    val colors = remember(palette) { palette.scheme(dark) }
    val syntax = remember(dark) { syntaxPalette(dark) }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, (density.fontScale * fontScale).coerceIn(0.8f, 1.6f)),
        LocalSyntaxPalette provides syntax,
        LocalSpark provides palette.spark,
        LocalSuccess provides palette.success,
        LocalIsDark provides dark,
    ) {
        MaterialTheme(colorScheme = colors, typography = typography(), content = content)
    }
}
