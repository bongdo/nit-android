package space.bongdo.nit.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val Spark = Color(0xFFE3A15C)
val Success = Color(0xFF7BC98A)
val Danger = Color(0xFFE5695B)

private val DarkColors = darkColorScheme(
    primary = Spark,
    onPrimary = Color(0xFF2A1909),
    primaryContainer = Color(0xFF3B2A19),
    onPrimaryContainer = Color(0xFFFFDDB8),
    secondary = Color(0xFFB8BFCC),
    onSecondary = Color(0xFF1A1C20),
    background = Color(0xFF141519),
    onBackground = Color(0xFFE9E7E2),
    surface = Color(0xFF1A1C21),
    onSurface = Color(0xFFE9E7E2),
    surfaceVariant = Color(0xFF24262D),
    onSurfaceVariant = Color(0xFFA9AFBB),
    outline = Color(0xFF353944),
    outlineVariant = Color(0xFF2A2D35),
    error = Danger,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFA9611A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1C2),
    onPrimaryContainer = Color(0xFF351A00),
    secondary = Color(0xFF5D6169),
    background = Color(0xFFF7F6F3),
    onBackground = Color(0xFF1B1C1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1C1F),
    surfaceVariant = Color(0xFFEDEAE4),
    onSurfaceVariant = Color(0xFF5D6169),
    outline = Color(0xFFCFCAC1),
    outlineVariant = Color(0xFFE3DFD7),
    error = Color(0xFFB3261E),
)

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
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) DarkColors else LightColors
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
    ) {
        MaterialTheme(colorScheme = colors, typography = typography(), content = content)
    }
}
