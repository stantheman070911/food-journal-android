package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Meal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

/** Tiles read best at the photos' own 3:4. */
private const val TILE_ASPECT = 3.0 / 4.0

/**
 * The meals a recap's collage shows: every one, however many, in the order they were eaten, as
 * the Journal's strips run. Meals saved at the same moment keep the order they were saved.
 */
fun recapMeals(meals: List<Meal>): List<Meal> = meals.sortedWith(compareBy<Meal> { it.eatenAt }.thenBy { it.id })

/** A tile's place in the collage, in pixels from its top left. */
data class TileRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * The collage's tiles for [count] meals, row by row, covering an area [width] by [height] with
 * [gap] between tiles. The number of columns keeps the tiles closest to 3:4. Rows are filled
 * evenly, fuller rows first, and a row with fewer tiles widens them, so there are never gaps.
 */
fun collageTiles(count: Int, width: Float, height: Float, gap: Float): List<TileRect> {
    if (count <= 0) return emptyList()
    fun rowsFor(columns: Int) = (count + columns - 1) / columns
    val columns = (1..count).minBy { columns ->
        val rows = rowsFor(columns)
        val tileWidth = (width - gap * (columns - 1)) / columns
        val tileHeight = (height - gap * (rows - 1)) / rows
        abs(ln(tileWidth / tileHeight / TILE_ASPECT))
    }
    val rows = rowsFor(columns)
    val rowHeight = (height - gap * (rows - 1)) / rows
    return buildList {
        for (row in 0 until rows) {
            val inRow = count / rows + if (row < count % rows) 1 else 0
            val tileWidth = (width - gap * (inRow - 1)) / inRow
            val top = row * (rowHeight + gap)
            for (column in 0 until inRow) {
                val left = column * (tileWidth + gap)
                // The last tile of a row ends exactly at the edge, whatever the rounding.
                val right = if (column == inRow - 1) width else left + tileWidth
                val bottom = if (row == rows - 1) height else top + rowHeight
                add(TileRect(left, top, right, bottom))
            }
        }
    }
}

/**
 * The figures a recap can show, each of which can be left off: how many meals, at how many
 * places, what they cost, and how that compares with the period before.
 */
data class RecapOptions(
    val meals: Boolean = true,
    val places: Boolean = true,
    val total: Boolean = true,
    val comparison: Boolean = true,
)

/**
 * Everything a recap could say about its period. [title] is the period, "Nov 2 – 8", "November",
 * or "2026"; [year] stands beside it, or is null when the title is the year. [places] is null
 * when no meal has a place, and [comparison] when Spending would show no change line.
 */
data class RecapFigures(
    val title: String,
    val year: String?,
    /** "24 meals" */
    val meals: String,
    /** "12 places" */
    val places: String?,
    /** "3,450" */
    val total: String,
    /** "120 less than last week" */
    val comparison: String?,
) {
    /** The words the image shows, with only the figures [options] keeps. */
    fun text(options: RecapOptions): RecapText = RecapText(
        title = title,
        year = year,
        stats = listOfNotNull(
            meals.takeIf { options.meals },
            places?.takeIf { options.places },
            total.takeIf { options.total },
        ).joinToString(" · ").ifEmpty { null },
        comparison = comparison?.takeIf { options.comparison },
    )
}

/**
 * The words under a recap's collage: [title] and [year] as in [RecapFigures], [stats] such as
 * "24 meals · 12 places · 3,450", and [comparison] below it. Either can be left off.
 */
data class RecapText(
    val title: String,
    val year: String?,
    val stats: String?,
    val comparison: String?,
)

private val MonthDay = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val Day = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
private val MonthName = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
private val ShortMonth = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

/**
 * What a recap of [period] could say, from [meals], those eaten in it, and [previousMeals], those
 * of the period before. The comparison follows Spending's rules for an unfinished period, and
 * states the difference plainly, never as a judgment.
 */
fun recapFigures(period: Period, meals: List<Meal>, previousMeals: List<Meal>, today: LocalDate): RecapFigures {
    val start = period.start
    val end = period.end
    val title = when (period.kind) {
        PeriodKind.Week -> "${start.format(MonthDay)} – ${end.format(if (start.month == end.month) Day else MonthDay)}"
        PeriodKind.Month -> start.format(MonthName)
        PeriodKind.Year -> start.year.toString()
    }
    val year = when {
        period.kind == PeriodKind.Year -> null
        start.year == end.year -> start.year.toString()
        // A week across New Year: "2025 – 26".
        else -> "${start.year} – ${(end.year % 100).toString().padStart(2, '0')}"
    }

    val total = meals.sumOf { it.price }
    val places = meals.mapNotNull { it.placeId }.distinct().size

    val comparison = comparedTotal(period, previousMeals, today)?.let { before ->
        val previous = when (period.kind) {
            PeriodKind.Week -> if (today in period) "last week" else "the week before"
            PeriodKind.Month -> period.plus(-1).start.format(ShortMonth)
            PeriodKind.Year -> period.plus(-1).start.year.toString()
        }
        val difference = total - before
        when {
            difference < 0 -> "${formatPrice(-difference)} less than $previous"
            difference > 0 -> "${formatPrice(difference)} more than $previous"
            else -> "The same as $previous"
        }
    }
    return RecapFigures(
        title = title,
        year = year,
        meals = count(meals.size, "meal"),
        places = places.takeIf { it > 0 }?.let { count(it, "place") },
        total = formatPrice(total),
        comparison = comparison,
    )
}

private fun count(n: Int, noun: String) = "${formatPrice(n)} $noun${if (n == 1) "" else "s"}"
