package com.stanley.foodjournal.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's icons: outlined, on a 24-unit grid, all drawn with the same stroke weight.
 * Colors come from the tint at the call site.
 */
object FoodJournalIcons {
    val Journal: ImageVector by lazy {
        strokeIcon(
            "Journal",
            "M6.5,3.5H17.5A1.5,1.5 0 0 1 19,5V19A1.5,1.5 0 0 1 17.5,20.5H6.5A1.5,1.5 0 0 1 5,19V5A1.5,1.5 0 0 1 6.5,3.5Z",
            "M9,3.5V20.5",
            "M13,3.5V9.5L14.75,8.25L16.5,9.5V3.5",
        )
    }

    val Spending: ImageVector by lazy {
        strokeIcon(
            "Spending",
            "M4,12H6A1,1 0 0 1 7,13V19.5H3V13A1,1 0 0 1 4,12Z",
            "M11,4.5H13A1,1 0 0 1 14,5.5V19.5H10V5.5A1,1 0 0 1 11,4.5Z",
            "M18,9H20A1,1 0 0 1 21,10V19.5H17V10A1,1 0 0 1 18,9Z",
        )
    }

    val Search: ImageVector by lazy {
        strokeIcon(
            "Search",
            "M17.5,11A6.5,6.5 0 1 1 4.5,11A6.5,6.5 0 1 1 17.5,11Z",
            "M15.75,15.75L20,20",
        )
    }

    val Add: ImageVector by lazy {
        strokeIcon("Add", "M12,5V19", "M5,12H19")
    }

    val Back: ImageVector by lazy {
        strokeIcon("Back", "M19,12H5", "M11,6L5,12L11,18")
    }
}

private const val STROKE_WIDTH = 1.5f

private fun strokeIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { data ->
            addPath(
                pathData = addPathNodes(data),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
