package com.stanley.foodjournal.ui.meal

import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.logic.LocationFix
import com.stanley.foodjournal.logic.LocationSource
import com.stanley.foodjournal.logic.canSave
import com.stanley.foodjournal.logic.parsePrice
import com.stanley.foodjournal.logic.placeLocation
import com.stanley.foodjournal.logic.savesLocationByDefault
import com.stanley.foodjournal.logic.showsLocationRow
import java.io.Serializable
import java.time.LocalDateTime

/**
 * What the Meal screen holds while a meal is added or edited. It is saved with the screen's
 * state, so a form restored after Android closed the app comes back as it was.
 *
 * A [photo] is a file name. While [photoIsPending], the file is in `pending/` and only moves
 * into place when the meal is saved.
 */
data class MealDraft(
    val eatenAt: LocalDateTime,
    val photo: String? = null,
    val photoIsPending: Boolean = false,
    val name: String = "",
    val priceText: String = "",
    val isFavorite: Boolean = false,
    val note: String = "",
    val placeId: Long? = null,
    /** A place typed in the picker that doesn't exist yet. It is created on save. */
    val newPlaceName: String? = null,
    /** Set once the place is chosen by hand, so a late location fix never overwrites it. */
    val placeTouched: Boolean = false,
    /**
     * Where the meal was eaten, as far as is known: a gallery photo's location, or the phone's,
     * as [locationSource] says. Null until there is one.
     */
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationSource: LocationSource? = null,
    /** The Save location switch. It follows [locationSource] until it is tapped. */
    val saveLocation: Boolean = false,
    val saveLocationTouched: Boolean = false,
) : Serializable {
    val canSave: Boolean get() = canSave(hasPhoto = photo != null, name = name, priceText = priceText)

    /** Where the meal was eaten, or null without both coordinates. */
    val location: LocationFix?
        get() = if (latitude != null && longitude != null) LocationFix(latitude, longitude) else null

    /**
     * The draft with its location from [source], at [location]. Unless it has been tapped, the
     * switch starts as that source sets it.
     */
    fun withLocation(source: LocationSource?, location: LocationFix?): MealDraft = copy(
        latitude = location?.latitude,
        longitude = location?.longitude,
        locationSource = source,
        saveLocation = if (saveLocationTouched || source == null) saveLocation else savesLocationByDefault(source),
    )

    /** Whether the Save location row shows; see [showsLocationRow]. */
    fun showsLocationRow(isNew: Boolean, locationAllowed: Boolean): Boolean =
        showsLocationRow(isNew, location, locationAllowed)

    /** Where places are matched and created from; see [placeLocation]. */
    fun placeLocation(isNew: Boolean, locationAllowed: Boolean): LocationFix? =
        placeLocation(isNew, locationSource, location, saveLocation, locationAllowed)

    /** The place to create on save for a [newPlaceName], at [location] when there is one. */
    fun newPlace(location: LocationFix?): Place? = newPlaceName?.let { name ->
        Place(name = name, latitude = location?.latitude, longitude = location?.longitude)
    }

    /**
     * The meal this draft saves as. A [newPlaceName] is resolved separately, on save. The location
     * is kept while the switch is on, unless [keepLocation] says the row isn't there to show it.
     */
    fun toMeal(id: Long, keepLocation: Boolean = true): Meal {
        val saved = location?.takeIf { saveLocation && keepLocation }
        return Meal(
            id = id,
            eatenAt = eatenAt,
            photo = photo,
            name = name.trim().ifEmpty { null },
            price = parsePrice(priceText) ?: 0,
            isFavorite = isFavorite,
            note = note.trim().ifEmpty { null },
            placeId = if (newPlaceName != null) null else placeId,
            latitude = saved?.latitude,
            longitude = saved?.longitude,
        )
    }

    /** Whether saving this draft would change [meal]. */
    fun hasChangesFrom(meal: Meal): Boolean = newPlaceName != null || toMeal(meal.id) != meal

    companion object {
        fun from(meal: Meal) = MealDraft(
            eatenAt = meal.eatenAt,
            photo = meal.photo,
            name = meal.name.orEmpty(),
            priceText = meal.price.toString(),
            isFavorite = meal.isFavorite,
            note = meal.note.orEmpty(),
            placeId = meal.placeId,
            placeTouched = true,
            latitude = meal.latitude,
            longitude = meal.longitude,
            locationSource = LocationSource.Saved.takeIf { meal.latitude != null && meal.longitude != null },
            saveLocation = meal.latitude != null && meal.longitude != null,
        )
    }
}
