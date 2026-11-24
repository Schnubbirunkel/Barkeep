package de.schnubbirunkel.barkeep.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import de.schnubbirunkel.barkeep.R
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
    background = Color.Transparent,
    onBackground = Color(0xFFF4F5FA),
    surface = Color.Transparent,
    onSurface = Color(0xFFF4F5FA),
    surfaceVariant = InkHigh,
    onSurfaceVariant = Color(0xFFB4BBCF),
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
    background = Color.Transparent,
    surface = Color.Transparent,
    onBackground = Color(0xFF151824),
    onSurface = Color(0xFF151824),
    surfaceVariant = Color(0xFFE3E5EE),
    surfaceContainer = Color(0xFFEDEEF5),
    surfaceContainerHigh = Color(0xFFE6E8F0),
    surfaceContainerHighest = Color(0xFFDFE1EA),
)

// Plus Jakarta Sans for headings, Inter for text – both variable fonts (SIL OFL), bundled in res/font.
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, w: Int) = Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

private val Jakarta = FontFamily(
    variable(R.font.plus_jakarta_sans, 500), variable(R.font.plus_jakarta_sans, 600),
    variable(R.font.plus_jakarta_sans, 700), variable(R.font.plus_jakarta_sans, 800),
)
private val InterFont = FontFamily(
    variable(R.font.inter, 400), variable(R.font.inter, 500), variable(R.font.inter, 600), variable(R.font.inter, 700),
)

private val AppTypography = Typography().let { t ->
    fun h(st: TextStyle, w: FontWeight, ls: Double) = st.copy(fontFamily = Jakarta, fontWeight = w, letterSpacing = ls.sp)
    fun b(st: TextStyle, w: FontWeight = FontWeight.Normal) = st.copy(fontFamily = InterFont, fontWeight = w)
    t.copy(
        displayLarge = h(t.displayLarge, FontWeight.ExtraBold, -1.2),
        displayMedium = h(t.displayMedium, FontWeight.ExtraBold, -1.0),
        displaySmall = h(t.displaySmall, FontWeight.ExtraBold, -0.8),
        headlineLarge = h(t.headlineLarge, FontWeight.ExtraBold, -0.6),
        headlineMedium = h(t.headlineMedium, FontWeight.Bold, -0.4),
        headlineSmall = h(t.headlineSmall, FontWeight.Bold, -0.3),
        titleLarge = h(t.titleLarge, FontWeight.Bold, -0.2),
        titleMedium = b(t.titleMedium, FontWeight.SemiBold),
        titleSmall = b(t.titleSmall, FontWeight.SemiBold),
        bodyLarge = b(t.bodyLarge),
        bodyMedium = b(t.bodyMedium),
        bodySmall = b(t.bodySmall),
        labelLarge = b(t.labelLarge, FontWeight.SemiBold),
        labelMedium = b(t.labelMedium, FontWeight.Medium),
        labelSmall = b(t.labelSmall, FontWeight.Medium).copy(letterSpacing = 0.4.sp),
    )
}

@Composable
fun brandTitleStyle() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)

val ACCENTS: Map<String, Pair<Color, Color>> = mapOf(
    // accent id -> (dark-theme colour, light-theme colour)
    "citrus" to (Citrus to Color(0xFF7A5F00)),
    "mint" to (Mint to Color(0xFF006B54)),
    "coral" to (Coral to Color(0xFFB3261E)),
    "lavender" to (Color(0xFFB9A6FF) to Color(0xFF5B45B0)),
    "ocean" to (Color(0xFF6CC4FF) to Color(0xFF00639B)),
)

@Composable
fun BarkeepTheme(theme: String = "dark", accent: String = "citrus", content: @Composable () -> Unit) {
    val dark = when (theme) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val (accDark, accLight) = ACCENTS[accent] ?: ACCENTS.getValue("citrus")
    val base = if (dark) Dark else Light
    val acc = if (dark) accDark else accLight
    val scheme = if (accent == "citrus") base else base.copy(
        primary = acc,
        onPrimary = if (dark) Color(0xFF101010) else Color.White,
        primaryContainer = if (dark) acc.copy(alpha = 0.22f).compositeOver(base.surface) else acc.copy(alpha = 0.18f).compositeOver(base.surface),
        onPrimaryContainer = if (dark) acc else Color(0xFF1A1A1A),
    )
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
