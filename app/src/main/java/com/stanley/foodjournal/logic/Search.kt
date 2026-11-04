package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Meal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What Search filters by. Every part is optional; with none, every meal is a result. */
data class SearchFilter(
    val text: String = "",
    val placeId: Long? = null,
    /** A district's code. A search holds a place or a district, not both. */
    val districtCode: String? = null,
    /** The first day, included. */
    val from: LocalDate? = null,
    /** The last day, included. */
    val to: LocalDate? = null,
    val favoritesOnly: Boolean = false,
)

/**
 * The meals [filter] finds, newest first. Each typed word must appear in the meal's name, its
 * note, or its place's name, ignoring case and full-width letters, as place names are compared.
 * [placeNames] maps place ids to names, and [mealDistricts] meal ids to the code of the district
 * each was eaten in, as [mealDistricts] in MealMap.kt finds them.
 */
fun searchMeals(
    meals: List<Meal>,
    placeNames: Map<Long, String>,
    filter: SearchFilter,
    mealDistricts: Map<Long, String> = emptyMap(),
): List<Meal> {
    val words = searchWords(filter.text)
    return meals
        .filter { meal ->
            val day = meal.eatenAt.toLocalDate()
            (filter.placeId == null || meal.placeId == filter.placeId) &&
                (filter.districtCode == null || mealDistricts[meal.id] == filter.districtCode) &&
                (filter.from == null || day >= filter.from) &&
                (filter.to == null || day <= filter.to) &&
                (!filter.favoritesOnly || meal.isFavorite) &&
                (words.isEmpty() || haystack(meal, placeNames).let { text -> words.all { it in text } })
        }
        .sortedWith(compareByDescending<Meal> { it.eatenAt }.thenByDescending { it.id })
}

/** The words of a search, cleaned and lowercased. Spaces separate them. */
fun searchWords(text: String): List<String> =
    searchKey(text).split(' ').filter { it.isNotEmpty() }

/** A meal's searchable text. A space joins the parts, so no word can match across two. */
private fun haystack(meal: Meal, placeNames: Map<Long, String>): String =
    listOfNotNull(meal.name, meal.note, meal.placeId?.let(placeNames::get))
        .joinToString(" ") { searchKey(it) }

private fun searchKey(text: String): String = normalizePlaceName(text).lowercase(Locale.ROOT)

private val ResultDay = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
private val ResultDayWithYear = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH)

/** A result's date: "Tue, Nov 3", with the year outside [today]'s year: "Tue, Nov 3, 2025". */
fun formatResultDate(date: LocalDate, today: LocalDate): String =
    date.format(if (date.year == today.year) ResultDay else ResultDayWithYear)
