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

    val Favorite: ImageVector by lazy { strokeIcon("Favorite", STAR) }

    /** The favorite star, filled in. The only filled icon, so a favorite reads at a glance. */
    val FavoriteOn: ImageVector by lazy { strokeIcon("FavoriteOn", STAR, filled = true) }

    val Close: ImageVector by lazy { strokeIcon("Close", "M6,6L18,18", "M18,6L6,18") }

    val Gallery: ImageVector by lazy {
        strokeIcon(
            "Gallery",
            "M5.5,4.5H18.5A1.5,1.5 0 0 1 20,6V18A1.5,1.5 0 0 1 18.5,19.5H5.5A1.5,1.5 0 0 1 4,18V6A1.5,1.5 0 0 1 5.5,4.5Z",
            "M4,16L9,11L13.5,15.5L16,13L20,17",
            "M17,8.5A1.5,1.5 0 1 1 14,8.5A1.5,1.5 0 1 1 17,8.5Z",
        )
    }

    val Delete: ImageVector by lazy {
        strokeIcon(
            "Delete",
            "M4.5,6.5H19.5",
            "M9.5,6.5V4.5A1,1 0 0 1 10.5,3.5H13.5A1,1 0 0 1 14.5,4.5V6.5",
            "M6.5,6.5L7.3,19.1A1.5,1.5 0 0 0 8.8,20.5H15.2A1.5,1.5 0 0 0 16.7,19.1L17.5,6.5",
            "M10,10.5V16.5",
            "M14,10.5V16.5",
        )
    }
}

private const val STAR =
    "M12,3.6L14.29,9.45L20.56,9.82L15.71,13.81L17.29,19.88L12,16.5" +
        "L6.71,19.88L8.29,13.81L3.44,9.82L9.71,9.45Z"

private const val STROKE_WIDTH = 1.5f

private fun strokeIcon(name: String, vararg paths: String, filled: Boolean = false): ImageVector =
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
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
