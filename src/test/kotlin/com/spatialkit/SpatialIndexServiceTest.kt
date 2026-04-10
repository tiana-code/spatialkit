package com.spatialkit

import com.fasterxml.jackson.databind.ObjectMapper
import com.spatialkit.jts.GeoJsonParser
import com.spatialkit.jts.PolygonSimplifier
import com.spatialkit.jts.SpatialIndexService
import com.spatialkit.jts.SpatialQueryResult
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader

class SpatialIndexServiceTest {

    private fun buildService(geojson: String?): SpatialIndexService {
        val resourceLoader = mockk<ResourceLoader>()
        val objectMapper = ObjectMapper()
        val geometryFactory = GeometryFactory(PrecisionModel(), 4326)
        val geoJsonParser = GeoJsonParser(geometryFactory)
        val polygonSimplifier = PolygonSimplifier(resourceLoader, objectMapper, geoJsonParser)

        if (geojson != null) {
            val res = ByteArrayResource(geojson.toByteArray())
            every {
                resourceLoader.getResource(match {
                    it.contains("land")
                })
            } returns res
        } else {
            val res = mockk<Resource>()
            every { res.exists() } returns false
            every {
                resourceLoader.getResource(match {
                    it.contains("land")
                })
            } returns res
        }

        val missingResource = mockk<Resource>()
        every { missingResource.exists() } returns false
        every {
            resourceLoader.getResource(match {
                it.contains("50m")
            })
        } returns missingResource

        every {
            resourceLoader.getResource(match {
                it.contains("lakes")
            })
        } returns missingResource

        val service = SpatialIndexService(
            resourceLoader, objectMapper, geometryFactory, geoJsonParser,
            polygonSimplifier
        )

        fun setField(obj: Any, name: String, value: Any) {
            val field = obj::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.set(obj, value)
        }
        setField(service, "landPolygonsPath", "classpath:geodata/ne_10m_land.geojson")
        setField(service, "lakesPath", "classpath:geodata/ne_10m_lakes.geojson")
        setField(service, "enabled", true)
        setField(polygonSimplifier, "simplifiedPolygonsPath", "classpath:geodata/ne_50m_land.geojson")

        service.init()
        return service
    }

    @Nested
    inner class DegradedMode {

        @Test
        fun `isOnLand returns Degraded when data not loaded`() {
            val service = buildService(null)
            val result = service.isOnLand(45.0, 15.0)
            assertTrue(result.isDegraded)
            assertTrue(result.valueOrFallback())
        }

        @Test
        fun `crossesLand returns Degraded when data not loaded`() {
            val service = buildService(null)
            val result = service.crossesLand(45.0, 5.0, 45.0, 25.0)
            assertTrue(result.isDegraded)
            assertTrue(result.valueOrFallback())
        }

        @Test
        fun `approxDistanceToNearestLandNm returns Degraded when data not loaded`() {
            val service = buildService(null)
            val result = service.approxDistanceToNearestLandNm(45.0, 15.0)
            assertTrue(result.isDegraded)
            assertEquals(null, result.valueOrFallback())
        }

        @Test
        fun `isOnLand returns Success with loaded data`() {
            val geojson = """
                {"type":"FeatureCollection","features":[{"type":"Feature","geometry":{
                "type":"Polygon","coordinates":[[[10,40],[20,40],[20,50],[10,50],[10,40]]]
                },"properties":{}}]}
            """.trimIndent()
            val service = buildService(geojson)
            val result = service.isOnLand(45.0, 15.0)
            assertTrue(result is SpatialQueryResult.Success)
            assertTrue(result.valueOrFallback())
        }
    }

    @Nested
    inner class InputValidation {

        private val service = buildService(null)

        @Test
        fun `isOnLand rejects latitude out of range`() {
            assertThrows<IllegalArgumentException> { service.isOnLand(91.0, 0.0) }
            assertThrows<IllegalArgumentException> { service.isOnLand(-91.0, 0.0) }
        }

        @Test
        fun `isOnLand rejects longitude out of range`() {
            assertThrows<IllegalArgumentException> { service.isOnLand(0.0, 181.0) }
            assertThrows<IllegalArgumentException> { service.isOnLand(0.0, -181.0) }
        }

        @Test
        fun `crossesLand rejects invalid coordinates`() {
            assertThrows<IllegalArgumentException> {
                service.crossesLand(91.0, 0.0, 0.0, 0.0)
            }
            assertThrows<IllegalArgumentException> {
                service.crossesLand(0.0, 0.0, 0.0, 181.0)
            }
        }

        @Test
        fun `approxDistanceToNearestLandNm rejects invalid coordinates`() {
            assertThrows<IllegalArgumentException> {
                service.approxDistanceToNearestLandNm(91.0, 0.0)
            }
        }

        @Test
        fun `approxDistanceToNearestLandNm rejects non-positive search radius`() {
            assertThrows<IllegalArgumentException> {
                service.approxDistanceToNearestLandNm(0.0, 0.0, searchRadiusDegrees = -1.0)
            }
            assertThrows<IllegalArgumentException> {
                service.approxDistanceToNearestLandNm(0.0, 0.0, searchRadiusDegrees = 0.0)
            }
        }
    }

    @Nested
    inner class GeoJsonValidation {

        @Test
        fun `polygon with less than 4 points is skipped`() {
            val geojson = """
                {"type":"FeatureCollection","features":[{"type":"Feature","geometry":{
                "type":"Polygon","coordinates":[[[0,0],[1,1],[0,0]]]
                },"properties":{}}]}
            """.trimIndent()
            val service = buildService(geojson)
            val status = service.getStatus()
            assertFalse(status.loaded)
        }

        @Test
        fun `unclosed ring is auto-closed and loaded`() {
            val geojson = """
                {"type":"FeatureCollection","features":[{"type":"Feature","geometry":{
                "type":"Polygon","coordinates":[[[0,0],[10,0],[10,10],[0,10]]]
                },"properties":{}}]}
            """.trimIndent()
            val service = buildService(geojson)
            val status = service.getStatus()
            assertTrue(status.loaded)
            assertEquals(1, status.polygonCount)
        }

        @Test
        fun `valid polygon is loaded`() {
            val geojson = """
                {"type":"FeatureCollection","features":[{"type":"Feature","geometry":{
                "type":"Polygon","coordinates":[[[0,0],[10,0],[10,10],[0,10],[0,0]]]
                },"properties":{}}]}
            """.trimIndent()
            val service = buildService(geojson)
            val status = service.getStatus()
            assertTrue(status.loaded)
            assertEquals(1, status.polygonCount)
        }

        @Test
        fun `self-intersecting polygon is repaired via buffer(0)`() {
            val geojson = """
                {"type":"FeatureCollection","features":[{"type":"Feature","geometry":{
                "type":"Polygon","coordinates":[[[0,0],[10,10],[10,0],[0,10],[0,0]]]
                },"properties":{}}]}
            """.trimIndent()
            val service = buildService(geojson)
            val status = service.getStatus()
            assertTrue(status.loaded)
        }
    }
}
