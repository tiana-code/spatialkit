package com.spatialkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.geom.prep.PreparedGeometry
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import org.locationtech.jts.index.strtree.STRtree

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpatialQueryTest {

    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)
    private lateinit var spatialIndex: STRtree
    private lateinit var lakesIndex: STRtree

    @BeforeAll
    fun setup() {
        val pgFactory = PreparedGeometryFactory()
        spatialIndex = STRtree()

        // Island: lon 10–20, lat 40–50
        val island = geometryFactory.createPolygon(
            arrayOf(
                Coordinate(10.0, 40.0), Coordinate(20.0, 40.0),
                Coordinate(20.0, 50.0), Coordinate(10.0, 50.0),
                Coordinate(10.0, 40.0)
            )
        )
        spatialIndex.insert(island.envelopeInternal, pgFactory.create(island))

        // Continent with interior lake: outer lon 30–40 lat 30–40, hole lon 33–37 lat 33–37
        val shell = geometryFactory.createLinearRing(
            arrayOf(
                Coordinate(30.0, 30.0), Coordinate(40.0, 30.0),
                Coordinate(40.0, 40.0), Coordinate(30.0, 40.0),
                Coordinate(30.0, 30.0)
            )
        )
        val hole = geometryFactory.createLinearRing(
            arrayOf(
                Coordinate(33.0, 33.0), Coordinate(37.0, 33.0),
                Coordinate(37.0, 37.0), Coordinate(33.0, 37.0),
                Coordinate(33.0, 33.0)
            )
        )
        val landWithLakeHole = geometryFactory.createPolygon(shell, arrayOf(hole))
        spatialIndex.insert(
            landWithLakeHole.envelopeInternal,
            pgFactory.create(landWithLakeHole)
        )

        // lake polygon matching the hole above
        lakesIndex = STRtree()
        val lake = geometryFactory.createPolygon(
            arrayOf(
                Coordinate(33.0, 33.0), Coordinate(37.0, 33.0),
                Coordinate(37.0, 37.0), Coordinate(33.0, 37.0),
                Coordinate(33.0, 33.0)
            )
        )
        lakesIndex.insert(lake.envelopeInternal, pgFactory.create(lake))
    }

    private fun isOnLand(lat: Double, lon: Double): Boolean {
        val point = geometryFactory.createPoint(Coordinate(lon, lat))

        @Suppress("UNCHECKED_CAST")
        val candidates = spatialIndex.query(point.envelopeInternal) as List<PreparedGeometry>
        return candidates.any { it.contains(point) }
    }

    private fun isOnWater(lat: Double, lon: Double): Boolean {
        return !isOnLand(lat, lon)
    }

    private fun crossesLand(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Boolean {
        val line = geometryFactory.createLineString(
            arrayOf(Coordinate(fromLon, fromLat), Coordinate(toLon, toLat))
        )

        @Suppress("UNCHECKED_CAST")
        val candidates = spatialIndex.query(line.envelopeInternal) as List<PreparedGeometry>
        val intersectingLand = candidates.filter { prepGeom ->
            if (!prepGeom.intersects(line)) return@filter false
            val intersection = line.intersection(prepGeom.geometry)
            !intersection.isEmpty && intersection.length > 0.00001
        }
        if (intersectingLand.isEmpty()) return false
        @Suppress("UNCHECKED_CAST")
        val lakeCandidates = lakesIndex.query(line.envelopeInternal) as List<PreparedGeometry>
        for (prepLandGeom in intersectingLand) {
            val landIntersection = line.intersection(prepLandGeom.geometry)
            if (!landIntersection.isEmpty) {
                val coveredByLake = lakeCandidates.any { lake ->
                    lake.contains(landIntersection) || lake.covers(landIntersection)
                }
                if (!coveredByLake) return true
            }
        }
        return false
    }

    @Nested
    inner class PointInPolygon {

        @Test
        fun `center of island is on land`() {
            assertTrue(isOnLand(45.0, 15.0))
            assertFalse(isOnWater(45.0, 15.0))
        }

        @Test
        fun `mid-atlantic is on water`() {
            assertFalse(isOnLand(30.0, -30.0))
            assertTrue(isOnWater(30.0, -30.0))
        }

        @Test
        fun `point inside polygon hole (lake) is on water`() {
            assertFalse(isOnLand(35.0, 35.0))
            assertTrue(isOnWater(35.0, 35.0))
        }

        @Test
        fun `land surrounding lake is on land`() {
            assertTrue(isOnLand(31.0, 31.0))
            assertFalse(isOnWater(31.0, 31.0))
        }

    }

    @Nested
    inner class LineIntersection {

        @Test
        fun `water-only route does not cross land`() {
            assertFalse(crossesLand(30.0, -20.0, 30.0, 5.0))
        }

        @Test
        fun `route crossing island returns true`() {
            assertTrue(crossesLand(45.0, 5.0, 45.0, 25.0))
        }

        @Test
        fun `route entirely inside land returns true`() {
            assertTrue(crossesLand(42.0, 12.0, 48.0, 18.0))
        }

        @Test
        fun `route entirely inside lake hole does not cross land`() {
            assertFalse(crossesLand(34.0, 34.0, 36.0, 36.0))
        }

        @Test
        fun `route from ocean to lake crosses surrounding land`() {
            assertTrue(crossesLand(25.0, 35.0, 35.0, 35.0))
        }
    }

    @Nested
    inner class STRtreeIndex {

        @Test
        fun `query returns candidates for point inside island`() {
            val point = geometryFactory.createPoint(Coordinate(15.0, 45.0))

            @Suppress("UNCHECKED_CAST")
            val candidates = spatialIndex.query(point.envelopeInternal) as List<PreparedGeometry>
            assertTrue(candidates.isNotEmpty())
            assertTrue(candidates.any { it.contains(point) })
        }

        @Test
        fun `query returns empty for point far from all polygons`() {
            val point = geometryFactory.createPoint(Coordinate(-100.0, -50.0))

            @Suppress("UNCHECKED_CAST")
            val candidates = spatialIndex.query(point.envelopeInternal) as List<PreparedGeometry>
            assertTrue(candidates.isEmpty())
        }

        @Test
        fun `wide envelope query returns both polygons`() {
            val envelope = geometryFactory.createPolygon(
                arrayOf(
                    Coordinate(5.0, 25.0), Coordinate(45.0, 25.0),
                    Coordinate(45.0, 55.0), Coordinate(5.0, 55.0),
                    Coordinate(5.0, 25.0)
                )
            ).envelopeInternal

            @Suppress("UNCHECKED_CAST")
            val candidates = spatialIndex.query(envelope) as List<PreparedGeometry>
            assertEquals(2, candidates.size)
        }
    }

    @Nested
    inner class CoordinatePrecision {

        @Test
        fun `high-precision coordinate inside island is on land`() {
            assertTrue(isOnLand(45.123456789, 15.987654321))
        }

        @Test
        fun `high-precision coordinate outside island is on water`() {
            assertTrue(isOnWater(45.123456789, 5.987654321))
        }

        @Test
        fun `coordinates near antimeridian are on water`() {
            assertTrue(isOnWater(0.0, 179.9))
            assertTrue(isOnWater(0.0, -179.9))
        }

        @Test
        fun `coordinates near poles are on water`() {
            assertTrue(isOnWater(89.9, 0.0))
            assertTrue(isOnWater(-89.9, 0.0))
        }
    }
}
