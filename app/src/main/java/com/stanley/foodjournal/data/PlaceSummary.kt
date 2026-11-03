package com.stanley.foodjournal.data

import androidx.room.Embedded
import java.time.LocalDateTime

/** A place with its most recent visit, computed from its meals. */
data class PlaceSummary(
    @Embedded val place: Place,
    val lastVisit: LocalDateTime?,
)
