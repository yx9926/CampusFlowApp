package com.campus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

internal val CampusLightColorScheme =
    lightColorScheme(
        primary = CampusPalette.Primary,
        onPrimary = Color.White,
        primaryContainer = CampusPalette.PrimaryContainer,
        onPrimaryContainer = CampusPalette.OnPrimaryContainer,
        secondary = CampusPalette.SecondaryText,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFECF0EC),
        onSecondaryContainer = CampusPalette.Text,
        tertiary = CampusPalette.Accent,
        onTertiary = Color.White,
        tertiaryContainer = CampusPalette.AccentContainer,
        onTertiaryContainer = CampusPalette.OnAccentContainer,
        background = CampusPalette.Background,
        onBackground = CampusPalette.Text,
        surface = CampusPalette.Surface,
        onSurface = CampusPalette.Text,
        surfaceVariant = CampusPalette.Background,
        onSurfaceVariant = CampusPalette.SecondaryText,
        surfaceTint = CampusPalette.Primary,
        surfaceDim = CampusPalette.Border,
        surfaceBright = CampusPalette.Surface,
        surfaceContainerLowest = CampusPalette.Surface,
        surfaceContainerLow = Color(0xFFF0F3F0),
        surfaceContainer = Color(0xFFEBEFEB),
        surfaceContainerHigh = Color(0xFFE5EBE6),
        surfaceContainerHighest = CampusPalette.Border,
        outline = CampusPalette.StrongBorder,
        outlineVariant = CampusPalette.Border,
        error = CampusPalette.Danger,
        onError = Color.White,
        errorContainer = Color(0xFFFCEAE8),
        onErrorContainer = Color(0xFF721C16),
        inverseSurface = CampusPalette.DarkSurface,
        inverseOnSurface = CampusPalette.DarkText,
        inversePrimary = CampusPalette.DarkPrimary,
        scrim = Color.Black,
    )

internal val CampusDarkColorScheme =
    darkColorScheme(
        primary = CampusPalette.DarkPrimary,
        onPrimary = Color(0xFF00382F),
        primaryContainer = CampusPalette.DarkPrimaryContainer,
        onPrimaryContainer = Color(0xFFC4EDE2),
        secondary = CampusPalette.DarkSecondaryText,
        onSecondary = CampusPalette.DarkBackground,
        secondaryContainer = CampusPalette.DarkElevatedSurface,
        onSecondaryContainer = CampusPalette.DarkText,
        tertiary = CampusPalette.DarkAccent,
        onTertiary = Color(0xFF48240E),
        tertiaryContainer = CampusPalette.DarkAccentContainer,
        onTertiaryContainer = CampusPalette.DarkAccent,
        background = CampusPalette.DarkBackground,
        onBackground = CampusPalette.DarkText,
        surface = CampusPalette.DarkSurface,
        onSurface = CampusPalette.DarkText,
        surfaceVariant = CampusPalette.DarkElevatedSurface,
        onSurfaceVariant = CampusPalette.DarkSecondaryText,
        surfaceTint = CampusPalette.DarkPrimary,
        surfaceDim = CampusPalette.DarkBackground,
        surfaceBright = CampusPalette.DarkElevatedSurface,
        surfaceContainerLowest = CampusPalette.DarkBackground,
        surfaceContainerLow = CampusPalette.DarkSurface,
        surfaceContainer = CampusPalette.DarkSurface,
        surfaceContainerHigh = CampusPalette.DarkElevatedSurface,
        surfaceContainerHighest = CampusPalette.DarkBorder,
        outline = Color(0xFF7E9385),
        outlineVariant = CampusPalette.DarkBorder,
        error = CampusPalette.DarkDanger,
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        inverseSurface = CampusPalette.Surface,
        inverseOnSurface = CampusPalette.Text,
        inversePrimary = CampusPalette.Primary,
        scrim = Color.Black,
    )

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun CampusFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // 固定品牌配色，不启用壁纸动态颜色，保证与 Web 的视觉识别一致。
    CompositionLocalProvider(
        LocalCampusColors provides if (darkTheme) DarkSemanticColors else LightSemanticColors,
    ) {
        MaterialExpressiveTheme(
            motionScheme = MotionScheme.standard(),
            colorScheme = if (darkTheme) CampusDarkColorScheme else CampusLightColorScheme,
            typography = Typography,
            shapes = CampusShapes,
            content = content,
        )
    }
}
