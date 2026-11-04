package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Place
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

/** A longitude and latitude, as the boundary file stores them. */
data class LngLat(val longitude: Double, val latitude: Double)

/**
 * One district (鄉鎮市區): its [code], its [county] and [district] names as the data spells them,
 * a [label] point inside it, and its closed [rings], each longitude, latitude, longitude, latitude.
 */
class District internal constructor(
    val code: String,
    val county: String,
    val district: String,
    val label: LngLat,
    val rings: List<DoubleArray>,
) {
    /** "臺北市 大安區": the county is always there, since some district names repeat across counties. */
    val name: String get() = "$county $district"

    /** The district's area, in square degrees, for ranking labels: larger areas are named first. */
    val area: Double = rings.sumOf { ring -> abs(shoelace(ring)) }

    private val minX = rings.minOf { ring -> ring.filterIndexed { i, _ -> i % 2 == 0 }.min() }
    private val maxX = rings.maxOf { ring -> ring.filterIndexed { i, _ -> i % 2 == 0 }.max() }
    private val minY = rings.minOf { ring -> ring.filterIndexed { i, _ -> i % 2 == 1 }.min() }
    private val maxY = rings.maxOf { ring -> ring.filterIndexed { i, _ -> i % 2 == 1 }.max() }

    /**
     * Whether the point is inside, by counting how many ring edges a line from it crosses. An odd
     * count is inside. Counting every ring together handles a district's islands and its holes alike.
     */
    fun contains(longitude: Double, latitude: Double): Boolean {
        val x = longitude
        val y = latitude
        if (x !in minX..maxX || y !in minY..maxY) return false
        var inside = false
        for (ring in rings) {
            for (i in 0 until ring.size / 2 - 1) {
                val x1 = ring[i * 2]
                val y1 = ring[i * 2 + 1]
                val x2 = ring[i * 2 + 2]
                val y2 = ring[i * 2 + 3]
                if ((y1 > y) != (y2 > y) && x < (x2 - x1) * (y - y1) / (y2 - y1) + x1) inside = !inside
            }
        }
        return inside
    }
}

/** A county's name and a point inside it, where the map names it. */
data class County(val name: String, val label: LngLat, val area: Double)

/**
 * Taiwan's districts (鄉鎮市區), read from a TopoJSON file, for finding which one holds a point
 * on the phone itself, and for drawing them. See tools/build-districts.sh for how the file is made.
 */
class DistrictMap private constructor(
    /** Every district, in the file's order. */
    val districts: List<District>,
    /** Every county, with where to name it. */
    val counties: List<County>,
    /**
     * Each boundary line once, longitude, latitude, longitude, latitude, so a border two districts
     * share is drawn a single time.
     */
    val lines: List<DoubleArray>,
    /** The indices in [lines] that are a county's border or the coast. */
    val countyLines: Set<Int>,
) {
    private val byCode = districts.associateBy { it.code }

    /** The district holding a point, or null at sea and outside Taiwan. */
    fun districtContaining(latitude: Double, longitude: Double): District? =
        districts.firstOrNull { it.contains(longitude, latitude) }

    /**
     * The county and district holding a point, spelled as the data spells them: "臺北市 大安區". The
     * county is always there, since some district names repeat across counties. Null at sea, and
     * outside Taiwan.
     */
    fun districtAt(latitude: Double, longitude: Double): String? = districtContaining(latitude, longitude)?.name

    /** The district with [code], or null when the data has no such district. */
    fun district(code: String): District? = byCode[code]

    /** The county and district of each of [places] that has coordinates in Taiwan, by place id. */
    fun districtsOf(places: List<Place>): Map<Long, String> = buildMap {
        for (place in places) {
            val latitude = place.latitude ?: continue
            val longitude = place.longitude ?: continue
            districtAt(latitude, longitude)?.let { put(place.id, it) }
        }
    }

    companion object {
        private val TopoJson = Json { ignoreUnknownKeys = true }

        /** Reads the TopoJSON file's text. Throws if it can't be read as one. */
        fun parse(json: String): DistrictMap {
            val topology = TopoJson.decodeFromString(Topology.serializer(), json)
            val arcs = topology.arcs.map { arc -> decodeArc(arc, topology.transform) }
            val geometries = topology.objects.getValue(DISTRICTS).geometries
            val districts = geometries.map { geometry ->
                val names = geometry.properties
                val rings = ringsOf(geometry.arcs).map { ring -> joinArcs(ring, arcs) }
                District(
                    code = names.code ?: "${names.county} ${names.district}",
                    county = names.county,
                    district = names.district,
                    label = names.label() ?: middleOf(rings),
                    rings = rings,
                )
            }
            val areas = districts.groupBy { it.county }.mapValues { (_, inCounty) -> inCounty.sumOf { it.area } }
            val counties = topology.objects[COUNTIES]?.geometries.orEmpty().mapNotNull { point ->
                val label = point.properties.label() ?: return@mapNotNull null
                County(point.properties.county, label, areas[point.properties.county] ?: 0.0)
            }
            return DistrictMap(districts, counties, arcs, countyLines(geometries))
        }

        /**
         * The arcs on a county's border: those the districts of two counties share, and those only
         * one district has, which run along the coast or the edge of the data.
         */
        private fun countyLines(geometries: List<Geometry>): Set<Int> {
            val counties = HashMap<Int, MutableSet<String>>()
            val uses = HashMap<Int, Int>()
            for (geometry in geometries) {
                for (index in ringsOf(geometry.arcs).flatten()) {
                    val arc = if (index >= 0) index else index.inv()
                    counties.getOrPut(arc) { HashSet() } += geometry.properties.county
                    uses[arc] = (uses[arc] ?: 0) + 1
                }
            }
            return uses.keys.filterTo(HashSet()) { arc -> uses.getValue(arc) == 1 || counties.getValue(arc).size > 1 }
        }

        /** An arc's points as x, y, x, y, from TopoJSON's quantized steps between them. */
        private fun decodeArc(arc: List<List<Int>>, transform: Transform): DoubleArray {
            val points = DoubleArray(arc.size * 2)
            var x = 0
            var y = 0
            arc.forEachIndexed { index, (dx, dy) ->
                x += dx
                y += dy
                points[index * 2] = x * transform.scale[0] + transform.translate[0]
                points[index * 2 + 1] = y * transform.scale[1] + transform.translate[1]
            }
            return points
        }

        /** The rings of a polygon's or a multipolygon's arcs, which only nest differently. */
        private fun ringsOf(arcs: JsonArray): List<List<Int>> =
            if (arcs.firstOrNull() is JsonPrimitive) {
                listOf(arcs.map { it.jsonPrimitive.int })
            } else {
                arcs.flatMap { ringsOf(it.jsonArray) }
            }

        /**
         * A ring's points. A negative index, ~i, means arc i backwards. Each arc starts where the
         * last one ended, so that point is left out.
         */
        private fun joinArcs(ring: List<Int>, arcs: List<DoubleArray>): DoubleArray {
            val points = ArrayList<Double>()
            for (index in ring) {
                val arc = arcs[if (index >= 0) index else index.inv()]
                val order = if (index >= 0) 0 until arc.size / 2 else (arc.size / 2 - 1 downTo 0)
                order.forEachIndexed { step, point ->
                    if (step > 0 || points.isEmpty()) {
                        points += arc[point * 2]
                        points += arc[point * 2 + 1]
                    }
                }
            }
            return points.toDoubleArray()
        }

        /** The middle of [rings]' bounds, for a file without label points. */
        private fun middleOf(rings: List<DoubleArray>): LngLat {
            val xs = rings.flatMap { ring -> ring.filterIndexed { i, _ -> i % 2 == 0 } }
            val ys = rings.flatMap { ring -> ring.filterIndexed { i, _ -> i % 2 == 1 } }
            return LngLat((xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2)
        }
    }
}

/** Twice a ring's signed area, by the shoelace formula. */
private fun shoelace(ring: DoubleArray): Double {
    var sum = 0.0
    for (i in 0 until ring.size / 2 - 1) {
        sum += ring[i * 2] * ring[i * 2 + 3] - ring[i * 2 + 2] * ring[i * 2 + 1]
    }
    return sum / 2
}

/** The layers in the file: the districts' outlines, and a point to name each county at. */
private const val DISTRICTS = "districts"
private const val COUNTIES = "counties"

@Serializable
private class Topology(val transform: Transform, val objects: Map<String, Layer>, val arcs: List<List<List<Int>>>)

@Serializable
private class Transform(val scale: List<Double>, val translate: List<Double>)

@Serializable
private class Layer(val geometries: List<Geometry>)

@Serializable
private class Geometry(val arcs: JsonArray = JsonArray(emptyList()), val properties: Names)

@Serializable
private class Names(
    val county: String,
    val district: String = "",
    val code: String? = null,
    val lx: Double? = null,
    val ly: Double? = null,
) {
    fun label(): LngLat? = if (lx != null && ly != null) LngLat(lx, ly) else null
}
