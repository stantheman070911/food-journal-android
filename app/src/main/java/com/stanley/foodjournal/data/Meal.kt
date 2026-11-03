package com.stanley.foodjournal.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * One meal. A meal needs a photo or a name, and a price.
 *
 * [eatenAt] is wall-clock time where the meal was eaten, so its day and time of day never shift
 * when the phone changes time zone. [photo] is a file name inside the app's photo folder.
 */
@Entity(
    tableName = "meal",
    foreignKeys = [
        ForeignKey(
            entity = Place::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("eatenAt"), Index("placeId")],
)
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eatenAt: LocalDateTime,
    val photo: String? = null,
    val name: String? = null,
    val price: Int,
    val isFavorite: Boolean = false,
    val note: String? = null,
    val placeId: Long? = null,
)
