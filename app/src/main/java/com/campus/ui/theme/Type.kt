package com.campus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun campusTextStyle(size: Int, height: Int, weight: FontWeight) =
    TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = height.sp,
        letterSpacing = 0.sp,
    )

val Typography =
    Typography(
        displayLarge = campusTextStyle(36, 44, FontWeight.SemiBold),
        displayMedium = campusTextStyle(36, 44, FontWeight.SemiBold),
        displaySmall = campusTextStyle(28, 36, FontWeight.SemiBold),
        headlineLarge = campusTextStyle(28, 36, FontWeight.SemiBold),
        headlineMedium = campusTextStyle(24, 32, FontWeight.SemiBold),
        headlineSmall = campusTextStyle(22, 30, FontWeight.SemiBold),
        titleLarge = campusTextStyle(18, 28, FontWeight.Medium),
        titleMedium = campusTextStyle(18, 28, FontWeight.Medium),
        titleSmall = campusTextStyle(16, 24, FontWeight.SemiBold),
        bodyLarge = campusTextStyle(18, 28, FontWeight.Medium),
        bodyMedium = campusTextStyle(16, 24, FontWeight.Normal),
        bodySmall = campusTextStyle(12, 18, FontWeight.Normal),
        labelLarge = campusTextStyle(16, 24, FontWeight.Medium),
        labelMedium = campusTextStyle(12, 18, FontWeight.Medium),
        labelSmall = campusTextStyle(12, 18, FontWeight.Medium),
    )
