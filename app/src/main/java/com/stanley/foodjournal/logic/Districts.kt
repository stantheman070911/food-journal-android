package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Place
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive

/**
 * Taiwan's districts (鄉鎮市區), read from a TopoJSON file, for finding which one holds a point
 * on the phone itself. See tools/build-districts.sh for how the file is made.
 */
class DistrictMap private constructor(private val districts: List<District>) {
    /**
     * The county and district holding a point, spelled as the data spells them: "臺北市 大安區". The
     * county is always there, since some district names repeat across counties. Null at sea, and
     * outside Taiwan.
     */
    fun districtAt(latitude: Double, longitude: Double): String? =
        districts.firstOrNull { it.contains(longitude, latitude) }?.name

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
            return DistrictMap(
                topology.objects.getValue(LAYER).geometries.map { geometry ->
                    District(
                        name = "${geometry.properties.county} ${geometry.properties.district}",
                        rings = ringsOf(geometry.arcs).map { ring -> joinArcs(ring, arcs) },
                    )
                },
            )
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
    }
}

/** One district's closed rings, each x, y, x, y, with its bounds to skip most of the checks. */
private class District(val name: String, private val rings: List<DoubleArray>) {
    private val minX = rings.minOf { ring -> ring.filterIndexed { i, _ -> i % 2 == 0 }.min() }
    private val maxX = rings.maxOf { ring -> ring.filterIndexed { i, _ -> i % 2 == 0 }.max() }
    private val minY = rings.minOf { ring -> ring.filterIndexed { i, _ -> i % 2 == 1 }.min() }
    private val maxY = rings.maxOf { ring -> ring.filterIndexed { i, _ -> i % 2 == 1 }.max() }

    /**
     * Whether the point is inside, by counting how many ring edges a line from it crosses. An odd
     * count is inside. Counting every ring together handles a district's islands and its holes alike.
     */
    fun contains(x: Double, y: Double): Boolean {
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

/** The layer in the file that holds the districts. */
private const val LAYER = "districts"

@Serializable
private class Topology(val transform: Transform, val objects: Map<String, Layer>, val arcs: List<List<List<Int>>>)

@Serializable
private class Transform(val scale: List<Double>, val translate: List<Double>)

@Serializable
private class Layer(val geometries: List<Geometry>)

@Serializable
private class Geometry(val arcs: JsonArray, val properties: Names)

@Serializable
private class Names(val county: String, val district: String)
