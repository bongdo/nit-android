package space.bongdo.nit.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class SyntaxPalette(
    val keyword: Color, val string: Color, val number: Color, val comment: Color,
    val operator: Color, val path: Color, val bracket1: Color, val bracket2: Color,
    val bracket3: Color, val bracket4: Color, val bracket5: Color, val bracket6: Color,
)

private val SyntaxDark = SyntaxPalette(
    keyword = Color(0xFFF9A8D4), string = Color(0xFF86EFAC), number = Color(0xFFFDBA74),
    comment = Color(0xFF8B949E), operator = Color(0xFF9CA3AF), path = Color(0xFF93C5FD),
    bracket1 = Color(0xFFFB7185), bracket2 = Color(0xFF7DD3FC), bracket3 = Color(0xFFBEF264),
    bracket4 = Color(0xFFFCD34D), bracket5 = Color(0xFFC4B5FD), bracket6 = Color(0xFF5EEAD4),
)

private val SyntaxLight = SyntaxPalette(
    keyword = Color(0xFF9D174D), string = Color(0xFF15803D), number = Color(0xFFB45309),
    comment = Color(0xFF6B7280), operator = Color(0xFF6B7280), path = Color(0xFF1D4ED8),
    bracket1 = Color(0xFFBE123C), bracket2 = Color(0xFF0369A1), bracket3 = Color(0xFF4D7C0F),
    bracket4 = Color(0xFFB45309), bracket5 = Color(0xFF7C3AED), bracket6 = Color(0xFF0F766E),
)

val LocalSyntaxPalette = staticCompositionLocalOf { SyntaxDark }

val THEME_IDS = listOf(
    "system", "light", "dark", "nord", "solarized", "gruvbox", "dracula",
    "paper", "paper-dark", "ember", "kiln", "mist", "ash", "ash-dark",
)

fun themeLabel(id: String): String = when (id) {
    "system" -> "System"
    "light" -> "Default light"
    "dark" -> "Default dark"
    "nord" -> "Nord"
    "solarized" -> "Solarized"
    "gruvbox" -> "Gruvbox"
    "dracula" -> "Dracula"
    "paper" -> "Paper"
    "ember" -> "Ember"
    "paper-dark" -> "Paper dark"
    "kiln" -> "Kiln"
    "mist" -> "Mist"
    "ash" -> "Ash"
    "ash-dark" -> "Ash dark"
    else -> "System"
}

fun themeIsDark(id: String, systemDark: Boolean): Boolean = when (id) {
    "system" -> systemDark
    "light" -> false
    "dark" -> true
    "nord" -> true
    "solarized" -> false
    "gruvbox" -> true
    "dracula" -> true
    "paper" -> false
    "paper-dark" -> true
    "ember" -> true
    "kiln" -> true
    "mist" -> false
    "ash" -> false
    "ash-dark" -> true
    else -> systemDark
}

internal fun color(hex: String): Color {
    val value = hex.removePrefix("#")
    return if (value.length == 8) Color(value.toLong(16)) else Color(0xFF000000L or value.toLong(16))
}

private fun palette(
    background: Color,
    foreground: Color,
    card: Color,
    cardForeground: Color,
    muted: Color,
    mutedForeground: Color,
    primary: Color,
    onPrimary: Color,
    secondary: Color,
    onSecondary: Color,
    border: Color,
    destructive: Color,
    success: Color,
    spark: Color,
) = ColorPalette(background, foreground, card, cardForeground, muted, mutedForeground, primary, onPrimary, secondary, onSecondary, border, destructive, success, spark)

data class ColorPalette(
    val background: Color, val foreground: Color, val card: Color, val cardForeground: Color,
    val muted: Color, val mutedForeground: Color, val primary: Color, val onPrimary: Color,
    val secondary: Color, val onSecondary: Color, val border: Color, val destructive: Color,
    val success: Color, val spark: Color,
)

internal fun ColorPalette.scheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = secondary, onPrimaryContainer = foreground,
        secondary = secondary, onSecondary = onSecondary,
        secondaryContainer = muted, onSecondaryContainer = foreground,
        tertiary = success, onTertiary = background,
        tertiaryContainer = success.copy(alpha = 0.18f), onTertiaryContainer = foreground,
        background = background, onBackground = foreground,
        surface = card, onSurface = cardForeground,
        surfaceVariant = muted, onSurfaceVariant = mutedForeground,
        outline = border, outlineVariant = border.copy(alpha = 0.55f),
        error = destructive, onError = Color.White,
        errorContainer = destructive.copy(alpha = 0.18f), onErrorContainer = foreground,
        inverseSurface = foreground, inverseOnSurface = background,
        surfaceTint = primary,
        surfaceDim = background, surfaceBright = card,
        surfaceContainerLowest = card, surfaceContainerLow = card,
        surfaceContainer = card, surfaceContainerHigh = muted,
        surfaceContainerHighest = muted,
    )
}

internal fun themeSpec(id: String, systemDark: Boolean): Pair<ColorPalette, Boolean> {
    val dark = themeIsDark(id, systemDark)
    val p = when (id) {
        "light" -> palette(color("#F7F6F3"), color("#1B1C1F"), color("#FFFFFF"), color("#1B1C1F"), color("#EDEAE4"), color("#5D6169"), color("#A9611A"), color("#FFFFFF"), color("#EDEAE4"), color("#1B1C1F"), color("#CFCAC1"), color("#B3261E"), color("#4F7A4A"), color("#B36B1F"))
        "dark" -> palette(color("#141519"), color("#E9E7E2"), color("#1A1C21"), color("#E9E7E2"), color("#24262D"), color("#A9AFBB"), color("#E3A15C"), color("#2A1909"), color("#24262D"), color("#E9E7E2"), color("#353944"), color("#E5695B"), color("#7BC98A"), color("#E3A15C"))
        "nord" -> palette(color("#2e3440"), color("#eceff4"), color("#3b4252"), color("#eceff4"), color("#3b4252"), color("#a9b1c0"), color("#88c0d0"), color("#2e3440"), color("#434c5e"), color("#e5e9f0"), color("#434c5e"), color("#bf616a"), color("#a3be8c"), color("#ebcb8b"))
        "solarized" -> palette(color("#fdf6e3"), color("#586e75"), color("#eee8d5"), color("#073642"), color("#eee8d5"), color("#5e747c"), color("#1a6fa8"), color("#fdf6e3"), color("#eee8d5"), color("#073642"), color("#d9d1bc"), color("#dc322f"), color("#859900"), color("#cb4b16"))
        "gruvbox" -> palette(color("#282828"), color("#ebdbb2"), color("#32302f"), color("#ebdbb2"), color("#3c3836"), color("#a89984"), color("#fabd2f"), color("#282828"), color("#3c3836"), color("#ebdbb2"), color("#504945"), color("#fb4934"), color("#b8bb26"), color("#fe8019"))
        "dracula" -> palette(color("#282a36"), color("#f8f8f2"), color("#303240"), color("#f8f8f2"), color("#383a4a"), color("#9aa0b5"), color("#bd93f9"), color("#282a36"), color("#44475a"), color("#f8f8f2"), color("#44475a"), color("#ff5555"), color("#50fa7b"), color("#ffb86c"))
        "paper" -> palette(color("#f7f3ea"), color("#2f2a24"), color("#fffdf8"), color("#2f2a24"), color("#ede6d8"), color("#766c60"), color("#7a5c3e"), color("#fffdf8"), color("#ede6d8"), color("#3b342c"), color("#ded3c1"), color("#b4472f"), color("#4f7a4a"), color("#c2703a"))
        "ember" -> palette(color("#1c1714"), color("#f0e3d6"), color("#251e1a"), color("#f0e3d6"), color("#2a211c"), color("#b09a8a"), color("#e8794a"), color("#1c1714"), color("#30251f"), color("#f0e3d6"), color("#3a2d25"), color("#e05545"), color("#7fbf7a"), color("#ffb066"))
        "paper-dark" -> palette(color("#221f1b"), color("#ece4d8"), color("#2b2723"), color("#ece4d8"), color("#2c2722"), color("#b3a696"), color("#d6b184"), color("#241f1a"), color("#332d27"), color("#ece4d8"), color("#3d352d"), color("#e06a55"), color("#8fbc85"), color("#e0a46a"))
        "kiln" -> palette(color("#1a1310"), color("#f3e6db"), color("#241a15"), color("#f3e6db"), color("#2a1d17"), color("#bda898"), color("#ff8a3d"), color("#1a1310"), color("#33231a"), color("#f3e6db"), color("#422c20"), color("#ff5c47"), color("#9bc47a"), color("#ffb066"))
        "mist" -> palette(color("#f6f6f3"), color("#2b3238"), color("#ffffff"), color("#2b3238"), color("#ececea"), color("#626a70"), color("#3f739c"), color("#ffffff"), color("#ececea"), color("#2b3238"), color("#dcdcd8"), color("#b4483a"), color("#4f7a4a"), color("#5a8fb8"))
        "ash" -> palette(color("#e9e9e7"), color("#26292b"), color("#f2f2f0"), color("#26292b"), color("#dededb"), color("#5f6568"), color("#3f739c"), color("#ffffff"), color("#dededb"), color("#26292b"), color("#cfcfcb"), color("#b4483a"), color("#4a7045"), color("#5a8fb8"))
        "ash-dark" -> palette(color("#121212"), color("#e5e6e6"), color("#272829"), color("#e5e6e6"), color("#262728"), color("#9ba0a3"), color("#8ab6d6"), color("#14212b"), color("#303233"), color("#e5e6e6"), color("#363839"), color("#e06a5c"), color("#8fbf87"), color("#8ab6d6"))
        else -> if (dark) palette(color("#141519"), color("#E9E7E2"), color("#1A1C21"), color("#E9E7E2"), color("#24262D"), color("#A9AFBB"), color("#E3A15C"), color("#2A1909"), color("#24262D"), color("#E9E7E2"), color("#353944"), color("#E5695B"), color("#7BC98A"), color("#E3A15C")) else palette(color("#F7F6F3"), color("#1B1C1F"), color("#FFFFFF"), color("#1B1C1F"), color("#EDEAE4"), color("#5D6169"), color("#A9611A"), color("#FFFFFF"), color("#EDEAE4"), color("#1B1C1F"), color("#CFCAC1"), color("#B3261E"), color("#4F7A4A"), color("#B36B1F"))
    }
    return p to dark
}


internal fun syntaxPalette(dark: Boolean): SyntaxPalette = if (dark) SyntaxDark else SyntaxLight


internal fun SyntaxPalette.colorFor(kind: SyntaxKind): Color = when (kind) {
    SyntaxKind.KEYWORD -> keyword
    SyntaxKind.STRING -> string
    SyntaxKind.NUMBER -> number
    SyntaxKind.COMMENT -> comment
    SyntaxKind.PATH -> path
    SyntaxKind.URL -> path
    SyntaxKind.PUNCTUATION -> operator
    SyntaxKind.BRACKET1 -> bracket1
    SyntaxKind.BRACKET2 -> bracket2
    SyntaxKind.BRACKET3 -> bracket3
    SyntaxKind.BRACKET4 -> bracket4
    SyntaxKind.BRACKET5 -> bracket5
    SyntaxKind.BRACKET6 -> bracket6
}
