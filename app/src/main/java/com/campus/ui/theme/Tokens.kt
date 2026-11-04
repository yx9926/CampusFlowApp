package com.campus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object CampusSpacing {
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 16.dp
    val ExtraLarge = 24.dp
    val Section = 32.dp
    val Page = 24.dp
    val MinimumTouchTarget = 48.dp
}

object CampusRadius {
    val Tag = 8.dp
    val Control = 16.dp
    val Card = 20.dp
    val Sheet = 24.dp
    val Hero = 24.dp
}

object CampusElevation {
    val Card = 1.dp
    val Border = 1.dp
    val Hairline = 0.5.dp
}

val CampusShapes =
    Shapes(
        extraSmall = RoundedCornerShape(CampusRadius.Tag),
        small = RoundedCornerShape(CampusRadius.Control),
        medium = RoundedCornerShape(CampusRadius.Card),
        large = RoundedCornerShape(CampusRadius.Sheet),
        extraLarge = RoundedCornerShape(CampusRadius.Sheet),
    )

@Immutable
data class CampusSemanticColors(
    val success: Color,
    val warning: Color,
    val mutedText: Color,
)

internal val LightSemanticColors =
    CampusSemanticColors(
        success = CampusPalette.Success,
        warning = CampusPalette.Warning,
        mutedText = CampusPalette.MutedText,
    )

internal val DarkSemanticColors =
    CampusSemanticColors(
        success = CampusPalette.DarkSuccess,
        warning = CampusPalette.DarkWarning,
        mutedText = CampusPalette.DarkMutedText,
    )

internal val LocalCampusColors = staticCompositionLocalOf { LightSemanticColors }

object CampusTheme {
    val colors: CampusSemanticColors
        @Composable get() = LocalCampusColors.current
}

object CampusLayout {
    val MetricsBreakpoint = 280.dp
    val TileBreakpoint = 360.dp
    val FormMaxWidth = 440.dp
    val ContentMaxWidth = 1000.dp
    val WideBreakpoint = 840.dp
}

object CampusComponents {
    val PlaceResultsHeight = 240.dp
    val OriginLabelWidth = 144.dp
    val SkeletonHeight = 72.dp
    val DialogMaxHeight = 640.dp
    val Icon = 24.dp
    val SmallIcon = 20.dp
    val Avatar = 64.dp
    val HeroHeight = 128.dp
    val ControlHeight = 56.dp
    val EmptyArt = 112.dp
    val TileHeight = 152.dp
    val GlyphStroke = 1.8.dp
}
