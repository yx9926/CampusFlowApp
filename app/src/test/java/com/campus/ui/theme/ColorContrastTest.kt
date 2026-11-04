package com.campus.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorContrastTest {
    private fun assertContrast(foreground: Color, background: Color, minimum: Float = 4.5f) {
        val first = foreground.luminance()
        val second = background.luminance()
        val ratio = (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
        assertTrue("Contrast $ratio must be at least $minimum", ratio >= minimum)
    }

    @Test
    fun lightTextAndFeedbackMeetContrastRequirements() {
        val textColors = listOf(
            CampusPalette.Text, CampusPalette.SecondaryText, CampusPalette.MutedText,
            CampusPalette.Primary, CampusPalette.Success, CampusPalette.Warning, CampusPalette.Danger,
        )
        for (background in listOf(CampusPalette.Surface, CampusPalette.Background)) {
            textColors.forEach { assertContrast(it, background) }
        }
        assertContrast(Color.White, CampusPalette.Primary)
        assertContrast(CampusPalette.StrongBorder, CampusPalette.Surface, 3f)
    }

    @Test
    fun darkTextAndFeedbackMeetContrastRequirements() {
        val textColors = listOf(
            CampusPalette.DarkText, CampusPalette.DarkSecondaryText, CampusPalette.DarkMutedText,
            CampusPalette.DarkPrimary, CampusPalette.DarkSuccess, CampusPalette.DarkWarning, CampusPalette.DarkDanger,
        )
        for (background in listOf(CampusPalette.DarkSurface, CampusPalette.DarkBackground, CampusPalette.DarkElevatedSurface)) {
            textColors.forEach { assertContrast(it, background) }
        }
        assertContrast(Color(0xFF00382F), CampusPalette.DarkPrimary)
    }
}
