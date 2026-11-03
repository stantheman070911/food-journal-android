package com.stanley.foodjournal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

// One family, the system sans-serif. Size and weight set the hierarchy.
// Tabular figures everywhere, so prices and totals line up wherever they appear.

private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

val FoodJournalTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.tabular(),
        displayMedium = displayMedium.tabular(),
        displaySmall = displaySmall.tabular(),
        headlineLarge = headlineLarge.tabular(),
        headlineMedium = headlineMedium.tabular(),
        headlineSmall = headlineSmall.tabular(),
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
