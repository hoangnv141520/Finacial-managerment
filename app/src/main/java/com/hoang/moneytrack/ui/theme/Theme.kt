package com.hoang.moneytrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoang.moneytrack.R

// Palette (design tokens from Figma v2)
val Primary = Color(0xFF00C896)
val PrimaryLight = Color(0xFF00A67E)
val Destructive = Color(0xFFFF4757)
val DestructiveLight = Color(0xFFE5484D)
val Amber = Color(0xFFF59E0B)
val ChartBlue = Color(0xFF3B82F6)
val ChartViolet = Color(0xFF8B5CF6)

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = Color(0xFF0D1117),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    error = Destructive,
    outline = Color(0x14FFFFFF),
)

private val LightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = Color.White,
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF0D1117),
    surface = Color.White,
    onSurface = Color(0xFF0D1117),
    surfaceVariant = Color(0xFFEDF0F4),
    onSurfaceVariant = Color(0xFF6B7280),
    error = DestructiveLight,
    outline = Color(0x14000000),
)

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
)
val InterFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)
val DmMono = FontFamily(Font(R.font.dm_mono_regular), Font(R.font.dm_mono_medium, FontWeight.Medium))

/** Style for money/data values — DM Mono per design. */
val MoneyStyle = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Medium)

private val AppTypography = Typography().let { t ->
    Typography(
        displaySmall = t.displaySmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontFamily = InterFont, fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontFamily = InterFont),
        bodyMedium = t.bodyMedium.copy(fontFamily = InterFont),
        bodySmall = t.bodySmall.copy(fontFamily = InterFont),
        labelLarge = t.labelLarge.copy(fontFamily = InterFont, fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontFamily = InterFont),
        labelSmall = t.labelSmall.copy(fontFamily = InterFont),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
)

val LocalHideBalance = staticCompositionLocalOf { false }

@Composable
fun MoneyTrackTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
