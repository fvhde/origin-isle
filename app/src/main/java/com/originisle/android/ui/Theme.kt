package com.originisle.android.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.originisle.android.R

/** The redesign's palette: near-black ground, grouped grey cards, one blue accent. */
object IsleColors {
    val Background = Color(0xFF0B0B0D)
    val Surface = Color(0xFF1A1A1E)
    /** Dividers, idle chips, segmented-control track. */
    val SurfaceHigh = Color(0xFF26262B)
    /** Selected segment. */
    val SurfaceHigher = Color(0xFF3E3E46)
    val Menu = Color(0xFF26262C)

    val Text = Color(0xFFF4F4F6)
    val TextSecondary = Color(0xFF8E8E96)
    val TextTertiary = Color(0xFFA1A1AA)
    val TextChip = Color(0xFFD4D4DA)
    val Chevron = Color(0xFF6E6E78)

    val Accent = Color(0xFF3A63F0)
    val AccentText = Color(0xFF8FA8FF)
    val SwitchOff = Color(0xFF3A3A40)
    val Danger = Color(0xFFFF8A7A)

    val Good = Color(0xFF34C77B)
    val GoodText = Color(0xFF5BD99A)
    val GoodBg = Color(0xFF14261C)
    val GoodButton = Color(0xFF1D3527)
    val GoodSubtle = Color(0xFF9CCBB0)
    val GoodIconBg = Color(0xFF173524)

    val Bad = Color(0xFFFF6B5E)
    val BadText = Color(0xFFFF8A7A)
    val BadBg = Color(0xFF2A1715)
    val BadButton = Color(0xFF3A1F1C)
    val BadSubtle = Color(0xFFD9A49C)

    val Warn = Color(0xFFFFB35C)
    val WarnBg = Color(0xFF2B2114)

    val CastTagBg =Color(0xFF1F2A4D)
    val CastTagFg = Color(0xFF9DB4FF)
    val PreviewBg = Color(0xFF1E2335)
    val PreviewLabel = Color(0xFF8C94B4)

    // Icon tiles in settings rows.
    val TileBlue = Color(0xFF3A63F0)
    val TileOrange = Color(0xFFF2683C)
    val TileTeal = Color(0xFF2BA8C4)
    val TilePurple = Color(0xFF7B5CFF)
    val TileGreen = Color(0xFF20A36B)
    val TileAmber = Color(0xFFE0A21B)
    val TileGray = Color(0xFF4A4A55)
}

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: FontWeight) = Font(
    R.font.plus_jakarta_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val JakartaSans = FontFamily(
    jakarta(FontWeight.Normal),
    jakarta(FontWeight.Medium),
    jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold),
    jakarta(FontWeight.ExtraBold),
)

private fun TextStyle.jakarta() = copy(fontFamily = JakartaSans)

private val IsleTypography = Typography().run {
    copy(
        displayLarge = displayLarge.jakarta(), displayMedium = displayMedium.jakarta(),
        displaySmall = displaySmall.jakarta(), headlineLarge = headlineLarge.jakarta(),
        headlineMedium = headlineMedium.jakarta(), headlineSmall = headlineSmall.jakarta(),
        titleLarge = titleLarge.jakarta(), titleMedium = titleMedium.jakarta(),
        titleSmall = titleSmall.jakarta(), bodyLarge = bodyLarge.jakarta(),
        bodyMedium = bodyMedium.jakarta(), bodySmall = bodySmall.jakarta(),
        labelLarge = labelLarge.jakarta(), labelMedium = labelMedium.jakarta(),
        labelSmall = labelSmall.jakarta(),
    )
}

/**
 * Origin Isle is dark-mode
 */
private val OriginIsleColorScheme = darkColorScheme(
    primary = IsleColors.Accent,
    onPrimary = Color.White,
    secondary = IsleColors.Good,
    onSecondary = Color.White,
    background = IsleColors.Background,
    onBackground = IsleColors.Text,
    surface = IsleColors.Surface,
    onSurface = IsleColors.Text,
    surfaceVariant = IsleColors.SurfaceHigh,
    onSurfaceVariant = IsleColors.TextTertiary,
    surfaceContainer = IsleColors.Menu,
    outline = IsleColors.SwitchOff,
)

@Composable
fun OriginIsleTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(colorScheme = OriginIsleColorScheme, typography = IsleTypography, content = content)
}
