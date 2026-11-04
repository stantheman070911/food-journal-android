package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DistrictsTest {
    private val map = DistrictMap.parse(File("src/main/assets/districts.json").readText())

    @Test
    fun `known points fall in their county and district`() {
        assertEquals("臺北市 信義區", map.districtAt(25.0339, 121.5645)) // Taipei 101
        assertEquals("臺北市 中正區", map.districtAt(25.0478, 121.5170)) // Taipei Main Station
        assertEquals("臺北市 大安區", map.districtAt(25.0174, 121.5340)) // NTU main gate
    }

    @Test
    fun `districts with the same name are told apart by their county`() {
        assertEquals("臺北市 大安區", map.districtAt(25.0264, 121.5436))
        assertEquals("臺中市 大安區", map.districtAt(24.3637, 120.5875))
    }

    @Test
    fun `the outlying islands are found`() {
        assertEquals("金門縣 金城鎮", map.districtAt(24.4326, 118.3177))
        assertEquals("澎湖縣 馬公市", map.districtAt(23.5655, 119.5793))
        assertEquals("連江縣 南竿鄉", map.districtAt(26.1597, 119.9490))
    }

    @Test
    fun `a point at sea or outside Taiwan has no district`() {
        assertNull(map.districtAt(24.5, 119.5)) // The Taiwan Strait
        assertNull(map.districtAt(35.68, 139.76)) // Tokyo
        assertNull(map.districtAt(0.0, 0.0))
    }

    @Test
    fun `only places with coordinates in Taiwan get a district`() {
        val places = listOf(
            Place(1, "Taipei 101 Food Court", 25.0339, 121.5645),
            Place(2, "No coordinates"),
            Place(3, "Tokyo Ramen", 35.68, 139.76),
        )

        assertEquals(mapOf(1L to "臺北市 信義區"), map.districtsOf(places))
    }

    @Test
    fun `every district has a unique code and a label point inside it`() {
        assertEquals(368, map.districts.size)
        assertEquals(368, map.districts.map { it.code }.toSet().size)
        for (district in map.districts) {
            assertTrue(district.name, district.contains(district.label.longitude, district.label.latitude))
        }
    }

    @Test
    fun `a district is found again by its code`() {
        val daan = map.districtContaining(25.0174, 121.5340)!!

        assertEquals("臺北市 大安區", map.district(daan.code)?.name)
        assertNull(map.district("not a code"))
    }

    @Test
    fun `every county has a label point inside one of its districts`() {
        assertEquals(22, map.counties.size)
        for (county in map.counties) {
            val district = map.districtContaining(county.label.latitude, county.label.longitude)
            assertEquals(county.name, district?.county)
            assertTrue(county.area > 0)
        }
    }

    @Test
    fun `county lines are fewer than all the lines`() {
        assertTrue(map.countyLines.isNotEmpty())
        assertTrue(map.countyLines.size < map.lines.size)
    }

    @Test
    fun `a small map reads polygons, multipolygons, holes, and arcs run backwards`() {
        // Two squares sharing an edge: arc 0 is that edge, which B runs backwards. B has a hole,
        // and C has two parts.
        val small = DistrictMap.parse(
            """
            {
              "type": "Topology",
              "transform": { "scale": [1, 1], "translate": [100, 20] },
              "arcs": [
                [[10, 0], [0, 10]],
                [[10, 10], [-10, 0], [0, -10], [10, 0]],
                [[10, 10], [10, 0], [0, -10], [-10, 0]],
                [[14, 4], [2, 0], [0, 2], [-2, 0], [0, -2]],
                [[30, 0], [2, 0], [0, 2], [-2, 0], [0, -2]],
                [[40, 0], [2, 0], [0, 2], [-2, 0], [0, -2]]
              ],
              "objects": {
                "districts": {
                  "type": "GeometryCollection",
                  "geometries": [
                    { "type": "Polygon", "arcs": [[0, 1]], "properties": { "county": "C", "district": "A" } },
                    { "type": "Polygon", "arcs": [[-1, 2], [3]], "properties": { "county": "C", "district": "B" } },
                    { "type": "MultiPolygon", "arcs": [[[4]], [[5]]], "properties": { "county": "D", "district": "E" } }
                  ]
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("C A", small.districtAt(25.0, 105.0))
        assertEquals("C B", small.districtAt(22.0, 112.0))
        assertNull("The hole in B", small.districtAt(25.0, 115.0))
        assertEquals("D E", small.districtAt(21.0, 131.0))
        assertEquals("D E", small.districtAt(21.0, 141.0))
        assertNull("Between E's parts", small.districtAt(21.0, 135.0))

        // A and B share arc 0 within one county; every other arc is someone's edge alone.
        assertFalse(0 in small.countyLines)
        assertEquals(setOf(1, 2, 3, 4, 5), small.countyLines)
        // Without codes or label points, the name stands in for the code, and the middle for the label.
        assertEquals("C A", small.district("C A")?.name)
        assertEquals(LngLat(105.0, 25.0), small.district("C A")?.label)
        assertTrue(small.counties.isEmpty())
    }
}
