package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.data.PlaceSummary

/** A saved place as the picker lists it. */
data class PickerPlace(
    val place: Place,
    /** "80 m", or null without a usable location on either side. */
    val distance: String?,
    val isSelected: Boolean,
)

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
 * The picker's rows. Saved places matching [query] in [orderForPicker] order, with distances
 * from [location]. A typed name that no saved or pending place has is offered as a new place.
 */
fun placePickerContent(
    places: List<PlaceSummary>,
    query: String,
    location: LocationFix?,
    selectedPlaceId: Long?,
    newPlaceName: String?,
): PlacePickerContent {
    val typed = normalizePlaceName(query)
    // A pending name that a saved place has since taken is that place now; save reuses it.
    val pending = newPlaceName?.takeIf { name -> places.none { sameName(it.place.name, name) } }
    val selectedName = newPlaceName?.takeIf { pending == null }
    val usable = location?.takeIf { it.isUsable() }
    val rows = orderForPicker(places, usable)
        .filter { matchesQuery(it.place.name, typed) }
        .map { summary ->
            PickerPlace(
                place = summary.place,
                distance = usable?.let { distanceTo(summary.place, it) }?.let(::formatDistance),
                isSelected = if (newPlaceName != null) {
                    selectedName != null && sameName(summary.place.name, selectedName)
                } else {
                    summary.place.id == selectedPlaceId
                },
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
