package com.stanley.foodjournal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.stanley.foodjournal.R

// The interface is set in the system sans-serif, with tabular figures so prices line up.
// Fraunces, a soft serif, sets only the display styles (the few big numbers) and the headline
// styles (headings). Fraunces has proportional figures only, so it never sets numbers that
// need to line up.

private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

/** Big numbers: Fraunces' display cut, light and soft. */
private val FrauncesDisplay = FontFamily(
    Font(
        resId = R.font.fraunces,
        weight = FontWeight.Light,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(300),
            FontVariation.Setting("opsz", 144f),
            FontVariation.Setting("SOFT", 100f),
            FontVariation.Setting("WONK", 0f),
        ),
    ),
)

/** Headings: Fraunces' text cut at medium weight, so it holds its own beside the sans. */
private val FrauncesHeadline = FontFamily(
    Font(
        resId = R.font.fraunces,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.Setting("opsz", 32f),
            FontVariation.Setting("SOFT", 100f),
            FontVariation.Setting("WONK", 0f),
        ),
    ),
)

private fun TextStyle.display() = copy(fontFamily = FrauncesDisplay, fontWeight = FontWeight.Light)

private fun TextStyle.headline() = copy(fontFamily = FrauncesHeadline, fontWeight = FontWeight.Medium)

val FoodJournalTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.display(),
        displayMedium = displayMedium.display(),
        displaySmall = displaySmall.display(),
        headlineLarge = headlineLarge.headline(),
        headlineMedium = headlineMedium.headline(),
        headlineSmall = headlineSmall.headline(),
        titleLarge = titleLarge.tabular(),
        titleMedium = titleMedium.tabular(),
        titleSmall = titleSmall.tabular(),
        bodyLarge = bodyLarge.tabular(),
        bodyMedium = bodyMedium.tabular(),
        bodySmall = bodySmall.tabular(),
        labelLarge = labelLarge.tabular(),
        labelMedium = labelMedium.tabular(),
        labelSmall = labelSmall.tabular(),
    )
}
