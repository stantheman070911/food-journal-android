package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.data.PlaceSummary
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** A position, from the phone or from a photo. A photo's position has no [accuracyMeters]. */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
)

/** A saved place counts as "here" within this distance. */
const val NEARBY_METERS = 150.0

/** A fix less accurate than this is too vague to pick or create a place from. */
const val USABLE_ACCURACY_METERS = 100f

private const val EARTH_RADIUS_METERS = 6_371_008.8

/** Great-circle distance between two points, in meters. */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).let { it * it }
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/**
 * A photo's location from its metadata, or null when it has none worth using. A picker that
 * withholds the location may zero the coordinates instead of removing them, and no meal is
 * eaten at exactly 0°, 0°.
 */
fun photoLocation(latitude: Double, longitude: Double): LocationFix? = when {
    latitude == 0.0 && longitude == 0.0 -> null
    latitude !in -90.0..90.0 || longitude !in -180.0..180.0 -> null
    else -> LocationFix(latitude, longitude)
}

/** A phone fix older than this may be from somewhere the phone has since left. */
val MAX_FIX_AGE: Duration = 2.minutes

/** Whether a phone fix taken at [fixElapsedNanos] is recent enough to use at [nowElapsedNanos]. */
fun isRecentFix(fixElapsedNanos: Long, nowElapsedNanos: Long): Boolean =
    nowElapsedNanos - fixElapsedNanos < MAX_FIX_AGE.inWholeNanoseconds

/**
 * The phone's position as a fix. One without an accuracy can't be trusted to pick a place
 * 150 m away, so it counts as vague; only a photo's position is trusted without one.
 */
fun deviceFix(latitude: Double, longitude: Double, accuracyMeters: Float?): LocationFix =
    LocationFix(latitude, longitude, accuracyMeters ?: Float.POSITIVE_INFINITY)

/** Whether [fix] is precise enough to match or create places. */
fun LocationFix.isUsable(): Boolean = accuracyMeters == null || accuracyMeters <= USABLE_ACCURACY_METERS

/** Distance from [fix] to [place], or null when the place was saved without a location. */
fun distanceTo(place: Place, fix: LocationFix): Double? {
    val lat = place.latitude ?: return null
    val lng = place.longitude ?: return null
    return distanceMeters(fix.latitude, fix.longitude, lat, lng)
}

/**
 * The place to fill in for a new meal: the saved place within [NEARBY_METERS] of [fix], when
 * there is exactly one. Nothing when there are none or several, or when the fix is too vague.
 */
fun prefillPlace(places: List<Place>, fix: LocationFix?): Place? {
    if (fix == null || !fix.isUsable()) return null
    val nearby = places.filter { place -> distanceTo(place, fix)?.let { it <= NEARBY_METERS } == true }
    return nearby.singleOrNull()
}

/**
 * Places in picker order. With a usable [fix], nearest first, then places saved without a
 * location. Without one, most recently visited first. Ties fall back to the name.
 */
fun orderForPicker(places: List<PlaceSummary>, fix: LocationFix?): List<PlaceSummary> {
    val byRecency = compareByDescending<PlaceSummary> { it.lastVisit }.thenBy { it.place.name }
    if (fix == null || !fix.isUsable()) return places.sortedWith(byRecency)
    return places.sortedWith(
        compareBy<PlaceSummary, Double?>(nullsLast()) { distanceTo(it.place, fix) }.then(byRecency),
    )
}

/** A distance as the picker shows it: "80 m", "1.2 km", "15 km", "1,240 km". */
fun formatDistance(meters: Double): String = when {
    meters < 1_000 -> "${(meters / 10).roundToInt() * 10} m"
    meters < 10_000 -> "%.1f km".format(java.util.Locale.ROOT, meters / 1_000)
    else -> "%,d km".format(java.util.Locale.ENGLISH, (meters / 1_000).roundToInt())
}

/** What renaming a place to a typed name does. */
sealed interface RenamePlan {
    /** Nothing to save: the name is blank or identical. */
    data object Unchanged : RenamePlan

    /** A plain rename, which includes changing only the capitalization. */
    data class Rename(val name: String) : RenamePlan

    /** Another place already has the name, so the renamed place merges into it. */
    data class Merge(val into: Place) : RenamePlan
}

/** Decides what renaming [place] to [typed] does. Only other places count as a clash. */
fun planRename(places: List<Place>, place: Place, typed: String): RenamePlan {
    val name = normalizePlaceName(typed)
    if (name.isEmpty() || name == place.name) return RenamePlan.Unchanged
    val clash = places.firstOrNull { it.id != place.id && sameName(it.name, name) }
    return if (clash != null) RenamePlan.Merge(into = clash) else RenamePlan.Rename(name)
}

/**
 * The place that survives a merge: [survivor] keeps its name and coordinates, and takes
 * [mergedAway]'s coordinates only when it has none of its own.
 */
fun mergedPlace(survivor: Place, mergedAway: Place): Place =
    if (survivor.latitude != null && survivor.longitude != null) {
        survivor
    } else {
        survivor.copy(latitude = mergedAway.latitude, longitude = mergedAway.longitude)
    }

/**
 * Where a meal is, for matching and creating places. A meal from the camera or Skip uses the
 * phone's fix, when it is precise enough. A gallery meal uses only its photo's location, and
 * none without one. A saved meal has none: the phone is no longer where the meal was eaten.
 */
fun mealLocation(
    isNew: Boolean,
    fromGallery: Boolean,
    photoLocation: LocationFix?,
    deviceFix: LocationFix?,
): LocationFix? = when {
    !isNew -> null
    fromGallery -> photoLocation
    else -> deviceFix?.takeIf { it.isUsable() }
}
