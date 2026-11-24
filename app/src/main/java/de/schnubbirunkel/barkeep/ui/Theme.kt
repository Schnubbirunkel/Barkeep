package de.schnubbirunkel.barkeep.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Night-bar palette: ink blue, citrus, coral – deliberately no brown.
val Ink = Color(0xFF0E1320)
val InkRaised = Color(0xFF171E30)
val InkHigh = Color(0xFF212A42)
val Citrus = Color(0xFFF4C542)
val Coral = Color(0xFFFF6F61)
val Mint = Color(0xFF5ED6B0)

private val Dark = darkColorScheme(
    primary = Citrus,
    onPrimary = Color(0xFF1C1600),
    primaryContainer = Color(0xFF3A3210),
    onPrimaryContainer = Color(0xFFFFE89A),
    secondary = Mint,
    onSecondary = Color(0xFF00281D),
    secondaryContainer = Color(0xFF103A30),
    onSecondaryContainer = Color(0xFFB4F2DD),
    tertiary = Coral,
    onTertiary = Color(0xFF2E0603),
    tertiaryContainer = Color(0xFF4A1A16),
    onTertiaryContainer = Color(0xFFFFDAD5),
    background = Ink,
    onBackground = Color(0xFFE7E9F1),
    surface = Ink,
    onSurface = Color(0xFFE7E9F1),
    surfaceVariant = InkHigh,
    onSurfaceVariant = Color(0xFFB7BCCD),
    surfaceContainerLowest = Color(0xFF0A0E18),
    surfaceContainerLow = Color(0xFF131A2A),
    surfaceContainer = InkRaised,
    surfaceContainerHigh = InkHigh,
    surfaceContainerHighest = Color(0xFF2A3452),
    outline = Color(0xFF48526E),
    outlineVariant = Color(0xFF2C3550),
)

private val Light = lightColorScheme(
    primary = Color(0xFF7A5F00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE08A),
    onPrimaryContainer = Color(0xFF261A00),
    secondary = Color(0xFF006B54),
    secondaryContainer = Color(0xFF9DF3D4),
    tertiary = Color(0xFFB3261E),
    tertiaryContainer = Color(0xFFFFDAD5),
    background = Color(0xFFF7F7FB),
    surface = Color(0xFFF7F7FB),
    surfaceVariant = Color(0xFFE3E5EE),
    surfaceContainer = Color(0xFFEDEEF5),
    surfaceContainerHigh = Color(0xFFE6E8F0),
    surfaceContainerHighest = Color(0xFFDFE1EA),
)

private val Display = FontFamily.Serif

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
        headlineLarge = t.headlineLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
    )
}

@Composable
fun BarkeepTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
