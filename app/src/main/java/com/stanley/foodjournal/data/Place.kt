package com.stanley.foodjournal.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A place where meals are eaten. Names are unique, ignoring case.
 *
 * The coordinates are where the place was first saved, or null when no location was available.
 * Visits, spend, and last visit are computed from meals and never stored.
 */
@Entity(
    tableName = "place",
    indices = [Index(value = ["name"], unique = true)],
)
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
