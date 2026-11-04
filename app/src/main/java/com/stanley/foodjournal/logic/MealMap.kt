package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.Place
import kotlin.math.cos
import kotlin.math.min

/**
 * Where a meal was eaten, for the Map tab and the district filter: its own location, or else its
 * place's coordinates. Null when it has neither.
 */
fun mealPosition(meal: Meal, places: Map<Long, Place>): LocationFix? {
    val latitude = meal.latitude
    val longitude = meal.longitude
    if (latitude != null && longitude != null) return LocationFix(latitude, longitude)
    val place = meal.placeId?.let(places::get) ?: return null
    val placeLatitude = place.latitude ?: return null
    val placeLongitude = place.longitude ?: return null
    return LocationFix(placeLatitude, placeLongitude)
}

/**
 * The district code of each meal with a position in Taiwan, by meal id. [districtCodeAt] finds the
 * code for a latitude and longitude, or null at sea and outside Taiwan.
 */
fun mealDistricts(
    meals: List<Meal>,
    places: Map<Long, Place>,
    districtCodeAt: (latitude: Double, longitude: Double) -> String?,
): Map<Long, String> = buildMap {
    for (meal in meals) {
        val position = mealPosition(meal, places) ?: continue
        districtCodeAt(position.latitude, position.longitude)?.let { put(meal.id, it) }
    }
}

/**
 * A pin on the map: a place, with [meals] eaten there, or a meal with a location but no place to
 * gather it, which has [placeId] null and [mealId] set.
 */
data class MapPin(
    val placeId: Long?,
    val mealId: Long?,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val meals: Int,
)

/**
 * What the Map tab shows: its [pins], how many meals with a position fall [outsideTaiwan], and how
 * many meals each district holds, by code.
 */
data class MealMap(
    val pins: List<MapPin>,
    val outsideTaiwan: Int,
    val mealsByDistrict: Map<String, Int>,
) {
    /** Whether any meal has a position at all; without one, the tab shows its empty state. */
    val isEmpty: Boolean get() = pins.isEmpty() && outsideTaiwan == 0
}

/**
 * The pins and counts for [meals]. Each place with coordinates in Taiwan gets one pin, counting all
 * its meals. A meal with a location whose place has no coordinates, or that has no place, gets a pin
 * of its own, named by [mealName]. A meal is counted toward the district its position falls in, and
 * a meal whose position is in no district counts as outside Taiwan and has no pin.
 */
fun mealMap(
    meals: List<Meal>,
    places: List<Place>,
    districtCodeAt: (latitude: Double, longitude: Double) -> String?,
    mealName: (Meal) -> String,
): MealMap {
    val byId = places.associateBy { it.id }
    val codes = HashMap<Pair<Double, Double>, String?>()
    fun codeAt(position: LocationFix) =
        codes.getOrPut(position.latitude to position.longitude) { districtCodeAt(position.latitude, position.longitude) }

    val pins = ArrayList<MapPin>()
    val byDistrict = HashMap<String, Int>()
    var outside = 0
    val atPlaces = meals.filter { meal -> meal.placeId?.let(byId::get)?.let { it.latitude != null && it.longitude != null } == true }
        .groupBy { it.placeId!! }
    for ((placeId, atPlace) in atPlaces) {
        val place = byId.getValue(placeId)
        val position = LocationFix(place.latitude!!, place.longitude!!)
        if (codeAt(position) != null) pins += MapPin(placeId, null, place.name, position.latitude, position.longitude, atPlace.size)
    }
    for (meal in meals) {
        val position = mealPosition(meal, byId) ?: continue
        val code = codeAt(position)
        if (code == null) {
            outside++
            continue
        }
        byDistrict[code] = (byDistrict[code] ?: 0) + 1
        if (meal.placeId == null || meal.placeId !in atPlaces) {
            pins += MapPin(null, meal.id, mealName(meal), position.latitude, position.longitude, 1)
        }
    }
    return MealMap(pins, outside, byDistrict)
}

/** A label's box on screen, in pixels. */
data class LabelBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlaps(other: LabelBox): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom
}

/**
 * The labels to draw from [boxes], given in order of importance: each one that overlaps none
 * already kept, so a name that would cover another is left out until there is room. Returns their
 * indices in [boxes].
 */
fun labelsThatFit(boxes: List<LabelBox>): List<Int> {
    val kept = ArrayList<Int>()
    for (index in boxes.indices) {
        val box = boxes[index]
        if (kept.none { boxes[it].overlaps(box) }) kept += index
    }
    return kept
}

/** A range of longitudes and latitudes. */
data class MapBounds(val west: Double, val south: Double, val east: Double, val north: Double)

/**
 * Taiwan drawn flat: longitude shortened by the cosine of the island's middle latitude, so shapes
 * near it keep their proportions. Map units are degrees of latitude; y grows southward, as on screen.
 */
object TaiwanProjection {
    private const val MIDDLE_LATITUDE = 23.7
    private val xScale = cos(Math.toRadians(MIDDLE_LATITUDE))

    /** The main island and Penghu, which the map opens on. */
    val Opening = MapBounds(west = 119.3, south = 21.85, east = 122.1, north = 25.35)

    /** Everywhere the map can be panned to: Kinmen and Matsu, near the mainland coast, too. */
    val Reach = MapBounds(west = 118.1, south = 21.8, east = 122.2, north = 26.4)

    fun x(longitude: Double): Double = longitude * xScale

    fun y(latitude: Double): Double = -latitude

    fun longitude(x: Double): Double = x / xScale

    fun latitude(y: Double): Double = -y

    /** The scale, in pixels per map unit, that fits [bounds] into a [width] by [height] view. */
    fun fit(bounds: MapBounds, width: Float, height: Float): Double = min(
        width / (x(bounds.east) - x(bounds.west)),
        height / (y(bounds.south) - y(bounds.north)),
    )
}
