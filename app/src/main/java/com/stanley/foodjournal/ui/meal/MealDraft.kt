package com.stanley.foodjournal.ui.meal

import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.logic.canSave
import com.stanley.foodjournal.logic.parsePrice
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
     * Set for a meal backfilled from the gallery. Its place comes only from the photo's
     * location, [photoLatitude] and [photoLongitude], which are null when the photo had none.
     */
    val fromGallery: Boolean = false,
    val photoLatitude: Double? = null,
    val photoLongitude: Double? = null,
) : Serializable {
    val canSave: Boolean get() = canSave(hasPhoto = photo != null, name = name, priceText = priceText)

    /** The meal this draft saves as. A [newPlaceName] is resolved separately, on save. */
    fun toMeal(id: Long): Meal = Meal(
        id = id,
        eatenAt = eatenAt,
        photo = photo,
        name = name.trim().ifEmpty { null },
        price = parsePrice(priceText) ?: 0,
        isFavorite = isFavorite,
        note = note.trim().ifEmpty { null },
        placeId = if (newPlaceName != null) null else placeId,
    )

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
        )
    }
}
