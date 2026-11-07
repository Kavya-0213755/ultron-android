package com.ultron.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ultron.assistant.R

/** "Obsidian & Champagne": near-black graphite, warm ivory text and a single champagne-gold accent. */
object U {
    val Bg = Color(0xFF07080A)
    val Bg2 = Color(0xFF0D0E11)
    val Glass = Color(0xFF15171B)
    val Glass2 = Color(0xFF1C1F24)
    val Hairline = Color(0x14FFFFFF)
    val Gold = Color(0xFFD9B779)
    val GoldLight = Color(0xFFF3DFB5)
    val GoldDeep = Color(0xFFA8834A)
    val Bronze = Color(0xFF6E5130)
    val Ivory = Color(0xFFF4EFE6)
    val Muted = Color(0xFF9C978E)
    val Faint = Color(0xFF6B675F)
    val Sage = Color(0xFF8FC9A3)
    val Coral = Color(0xFFE58F7A)
    val OnGold = Color(0xFF1A1408)

    val goldBrush = Brush.linearGradient(listOf(GoldLight, Gold, GoldDeep))
    val goldSoft = Brush.linearGradient(listOf(Color(0xFFE9D3A6), Color(0xFFC9A465)))
}

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, weight: FontWeight, line: Int = (size * 1.4).toInt(), spacing: Double = 0.0) =
    TextStyle(fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
        letterSpacing = spacing.sp)

private val UltronType = Typography(
    displaySmall = style(34, FontWeight.ExtraBold, 40, -0.5),
    headlineSmall = style(24, FontWeight.Bold, 30, -0.2),
    titleLarge = style(20, FontWeight.Bold, 26),
    titleMedium = style(16, FontWeight.SemiBold, 22),
    bodyLarge = style(16, FontWeight.Normal, 24),
    bodyMedium = style(14, FontWeight.Normal, 21),
    bodySmall = style(12, FontWeight.Medium, 17),
    labelLarge = style(14, FontWeight.SemiBold, 20, 0.2),
    labelMedium = style(12, FontWeight.SemiBold, 16, 0.4),
    labelSmall = style(11, FontWeight.Bold, 14, 1.4),
)

@Composable
fun UltronTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = U.Gold, onPrimary = U.OnGold, secondary = U.GoldLight, onSecondary = U.OnGold,
            background = U.Bg, onBackground = U.Ivory, surface = U.Glass, onSurface = U.Ivory,
            surfaceVariant = U.Glass2, onSurfaceVariant = U.Muted, outline = U.Hairline, error = U.Coral,
            surfaceContainerHigh = U.Glass2, surfaceContainer = U.Glass,
        ),
        typography = UltronType,
        content = content,
    )
}
