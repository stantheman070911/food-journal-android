package com.stanley.foodjournal.backup

import com.stanley.foodjournal.data.Converters
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.logic.PRICE_MAX_DIGITS
import com.stanley.foodjournal.logic.normalizePlaceName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/** The version of the backup file this app writes. A newer one is refused, an older one read. */
const val BACKUP_FORMAT = 1

/** The records, at the top of the ZIP. */
const val BACKUP_JSON = "backup.json"

/** The folder in the ZIP that holds the photos, by their stored names. */
const val BACKUP_PHOTOS = "photos/"

/**
 * Everything a backup holds besides the photos: the places and meals, with their ids, so each
 * meal keeps its place. Times are wall-clock text, as the database stores them.
 */
@Serializable
data class BackupFile(
    val format: Int,
    val exportedAt: String,
    val places: List<BackupPlace>,
    val meals: List<BackupMeal>,
)

@Serializable
data class BackupPlace(
    val id: Long,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class BackupMeal(
    val id: Long,
    val eatenAt: String,
    val photo: String? = null,
    val name: String? = null,
    val price: Int,
    val isFavorite: Boolean = false,
    val note: String? = null,
    val placeId: Long? = null,
)

/** Readable, and tolerant of fields a later version of the same format may add. */
val BackupJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

private val times = Converters()

/** The backup of [meals] and [places], made at [exportedAt]. */
fun backupOf(meals: List<Meal>, places: List<Place>, exportedAt: LocalDateTime) = BackupFile(
    format = BACKUP_FORMAT,
    exportedAt = times.fromLocalDateTime(exportedAt),
    places = places.map { BackupPlace(it.id, it.name, it.latitude, it.longitude) },
    meals = meals.map {
        BackupMeal(it.id, times.fromLocalDateTime(it.eatenAt), it.photo, it.name, it.price, it.isFavorite, it.note, it.placeId)
    },
)

/** The places to put back, once [checkBackup] has passed. */
fun BackupFile.restoredPlaces(): List<Place> = places.map { Place(it.id, it.name, it.latitude, it.longitude) }

/** The meals to put back, once [checkBackup] has passed. */
fun BackupFile.restoredMeals(): List<Meal> = meals.map {
    Meal(it.id, times.toLocalDateTime(it.eatenAt), it.photo, it.name, it.price, it.isFavorite, it.note, it.placeId)
}

/** When the backup was made, or null if that can't be read. */
fun BackupFile.exportDate(): LocalDate? = runCatching { times.toLocalDateTime(exportedAt).toLocalDate() }.getOrNull()

/** Why a file can't be imported. Nothing on the phone changes in any case. */
sealed interface BackupProblem {
    /** Not a ZIP, or no backup.json, or JSON that doesn't read as a backup. */
    data object NotABackup : BackupProblem

    /** Made by a later version of the app, with a format this one doesn't know. */
    data object NewerVersion : BackupProblem

    /** A backup, but its records break a rule or a photo is missing. [reason] is for the log. */
    data class Damaged(val reason: String) : BackupProblem

    /** The file couldn't be read through, such as when the phone runs out of room unpacking it. */
    data object Unreadable : BackupProblem
}

/** A backup that passed every check, or why it didn't. */
sealed interface BackupCheck {
    data class Passed(val file: BackupFile) : BackupCheck

    data class Failed(val problem: BackupProblem) : BackupCheck
}

/**
 * Reads [json], the backup.json found in a ZIP, or null when it had none, and checks it against
 * [photos], the photo names the ZIP holds. The format is read first, on its own, so a newer
 * backup whose records no longer read is still reported as newer.
 */
fun readBackup(json: String?, photos: Set<String>): BackupCheck {
    if (json == null) return BackupCheck.Failed(BackupProblem.NotABackup)
    val format = runCatching { BackupJson.parseToJsonElement(json).jsonObject["format"]?.jsonPrimitive?.intOrNull }
        .getOrNull()
        ?: return BackupCheck.Failed(BackupProblem.NotABackup)
    if (format > BACKUP_FORMAT) return BackupCheck.Failed(BackupProblem.NewerVersion)
    val file = try {
        BackupJson.decodeFromString(BackupFile.serializer(), json)
    } catch (_: IllegalArgumentException) {
        // SerializationException is an IllegalArgumentException.
        return BackupCheck.Failed(BackupProblem.NotABackup)
    }
    return checkBackup(file, photos)?.let(BackupCheck::Failed) ?: BackupCheck.Passed(file)
}

/** 999,999, the largest price the Meal screen takes. */
private val MAX_PRICE = "9".repeat(PRICE_MAX_DIGITS).toInt()

/**
 * Checks a backup before anything on the phone changes: its format, every meal's rules, unique
 * ids, the places meals name, unique place names, and that every photo a meal names is among
 * [photos], the photo names the file holds. Returns the first problem found, or null.
 */
fun checkBackup(file: BackupFile, photos: Set<String>): BackupProblem? {
    if (file.format > BACKUP_FORMAT) return BackupProblem.NewerVersion
    if (file.format < 1) return BackupProblem.NotABackup

    val placeIds = file.places.map { it.id }
    if (placeIds.size != placeIds.toSet().size) return damaged("a place id appears twice")
    val names = file.places.map { normalizePlaceName(it.name) }
    if (names.any { it.isEmpty() }) return damaged("a place has no name")
    if (names.map { it.lowercase(Locale.ROOT) }.toSet().size != names.size) return damaged("two places share a name")

    val mealIds = file.meals.map { it.id }
    if (mealIds.size != mealIds.toSet().size) return damaged("a meal id appears twice")
    val knownPlaces = placeIds.toSet()
    for (meal in file.meals) {
        if (meal.id <= 0) return damaged("meal ${meal.id} has no usable id")
        if (meal.price !in 0..MAX_PRICE) return damaged("meal ${meal.id} has price ${meal.price}")
        if (meal.photo == null && meal.name.isNullOrBlank()) return damaged("meal ${meal.id} has no photo or name")
        if (runCatching { times.toLocalDateTime(meal.eatenAt) }.isFailure) return damaged("meal ${meal.id} has time ${meal.eatenAt}")
        if (meal.placeId != null && meal.placeId !in knownPlaces) return damaged("meal ${meal.id} names a missing place")
        if (meal.photo != null && meal.photo !in photos) return damaged("meal ${meal.id}'s photo is missing")
    }
    if (file.places.any { it.id <= 0 }) return damaged("a place has no usable id")
    return null
}

private fun damaged(reason: String) = BackupProblem.Damaged(reason)

/**
 * The photo name inside a ZIP entry such as "photos/1f3e.jpg", or null for any other entry. A
 * name holding a folder, or "." or "..", is refused, so nothing unpacks outside its folder.
 */
fun photoEntryName(entry: String): String? {
    if (!entry.startsWith(BACKUP_PHOTOS)) return null
    val name = entry.removePrefix(BACKUP_PHOTOS)
    val unsafe = name.isEmpty() || name == "." || name == ".." || name.any { it == '/' || it == '\\' || it == '\u0000' }
    return name.takeUnless { unsafe }
}

/** The name Export suggests: "food-journal-2026-11-04.zip". */
fun backupFileName(date: LocalDate): String = "food-journal-$date.zip"
