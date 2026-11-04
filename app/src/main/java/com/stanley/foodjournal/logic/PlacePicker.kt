package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.data.PlaceSummary

/** A saved place as the picker lists it. */
data class PickerPlace(
    val place: Place,
    /** "80 m", or null without a usable location on either side. */
    val distance: String?,
    val isSelected: Boolean,
    /** "臺北市 大安區", or null without coordinates in Taiwan. */
    val district: String? = null,
) {
    /** The row's grey detail: "臺北市 大安區 · 80 m", or either part alone, or null. */
    val detail: String? get() = listOfNotNull(district, distance).joinToString(" · ").ifEmpty { null }
}

/** What the place picker shows for what has been typed. */
data class PlacePickerContent(
    /** The cleaned name to offer as a new place, or null when nothing new was typed. */
    val addName: String?,
    /** A new place chosen earlier but not yet saved, shown first while it matches. */
    val pendingName: String?,
    /** "No place" shows only before anything is typed. */
    val showNoPlace: Boolean,
    val noPlaceSelected: Boolean,
    val places: List<PickerPlace>,
)

/**
 * The picker's rows. Saved places matching [query], with distances from [location]. While
 * typing, names that start with the query come first, then names that contain it, so typing
 * 林 lists 林家乾麵 before 小林拉麵; each group keeps [orderForPicker] order. A typed name that
 * no saved or pending place has is offered as a new place. [districts] holds each place's county
 * and district, by place id.
 */
fun placePickerContent(
    places: List<PlaceSummary>,
    query: String,
    location: LocationFix?,
    selectedPlaceId: Long?,
    newPlaceName: String?,
    districts: Map<Long, String> = emptyMap(),
): PlacePickerContent {
    val typed = normalizePlaceName(query)
    // A pending name that a saved place has since taken is that place now; save reuses it.
    val pending = newPlaceName?.takeIf { name -> places.none { sameName(it.place.name, name) } }
    val selectedName = newPlaceName?.takeIf { pending == null }
    val usable = location?.takeIf { it.isUsable() }
    val rows = orderForPicker(places, usable)
        .filter { matchesQuery(it.place.name, typed) }
        .sortedBy { if (typed.isEmpty() || startsWithQuery(it.place.name, typed)) 0 else 1 }
        .map { summary ->
            PickerPlace(
                place = summary.place,
                distance = usable?.let { distanceTo(summary.place, it) }?.let(::formatDistance),
                isSelected = if (newPlaceName != null) {
                    selectedName != null && sameName(summary.place.name, selectedName)
                } else {
                    summary.place.id == selectedPlaceId
                },
                district = districts[summary.place.id],
            )
        }
    val taken = places.any { sameName(it.place.name, typed) } || (pending != null && sameName(pending, typed))
    return PlacePickerContent(
        addName = typed.takeIf { it.isNotEmpty() && !taken },
        pendingName = pending?.takeIf { matchesQuery(it, typed) },
        showNoPlace = typed.isEmpty(),
        noPlaceSelected = selectedPlaceId == null && newPlaceName == null,
        places = rows,
    )
}

/** What the picker's ✓ key does with what has been typed. */
sealed interface PickerSubmit {
    data class Select(val placeId: Long) : PickerSubmit

    data class Add(val name: String) : PickerSubmit

    /** Nothing typed: the key only closes the keyboard. */
    data object Nothing : PickerSubmit
}

/**
 * The ✓ key picks the saved place whose name was typed in full, adds a new name, or keeps a
 * pending new place typed again. Otherwise it only closes the keyboard.
 */
fun pickerSubmit(content: PlacePickerContent, query: String): PickerSubmit {
    val typed = normalizePlaceName(query)
    if (typed.isEmpty()) return PickerSubmit.Nothing
    content.places.firstOrNull { sameName(it.place.name, typed) }?.let { return PickerSubmit.Select(it.place.id) }
    content.addName?.let { return PickerSubmit.Add(it) }
    content.pendingName?.takeIf { sameName(it, typed) }?.let { return PickerSubmit.Add(it) }
    return PickerSubmit.Nothing
}
