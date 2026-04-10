package com.spatialkit.jts

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.simplify.TopologyPreservingSimplifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class PolygonSimplifier(
    private val resourceLoader: ResourceLoader,
    private val objectMapper: ObjectMapper,
    private val geoJsonParser: GeoJsonParser
) {

    @Value("\${spatialkit.land-polygons.simplified-path:classpath:geodata/ne_50m_land.geojson}")
    private lateinit var simplifiedPolygonsPath: String

    // Tolerance in degrees. 0.5 deg ~ 55 km at equator — suitable for client-side overview rendering
    @Value("\${spatialkit.land-polygons.simplify-tolerance:0.5}")
    private var toleranceDegrees: Double = 0.5

    fun buildSimplifiedPolygons(fallbackGeometries: List<Geometry>): List<SimplifiedPolygon> {
        if (fallbackGeometries.isEmpty()) return emptyList()
        val sourceGeometries = loadSourceGeometries(fallbackGeometries)
        val result = mutableListOf<SimplifiedPolygon>()
        for (geom in sourceGeometries) {
            try {
                val simplified = TopologyPreservingSimplifier.simplify(geom, toleranceDegrees)
                extractPolygons(simplified, result)
            } catch (e: Exception) {
                logger.warn { "Skipping geometry during simplification: ${e.message}" }
            }
        }
        return result
    }

    private fun loadSourceGeometries(fallbackGeometries: List<Geometry>): List<Geometry> {
        val resource = resourceLoader.getResource(simplifiedPolygonsPath)
        if (!resource.exists()) {
            logger.warn { "Simplified polygon file not found: $simplifiedPolygonsPath, falling back to main polygons" }
            return fallbackGeometries
        }

        val rootNode = resource.inputStream.bufferedReader().use { objectMapper.readTree(it) }
        val geometries = geoJsonParser.parseFeatureCollection(rootNode)
        return geometries.ifEmpty { fallbackGeometries }
    }

    private fun extractPolygons(geom: Geometry, result: MutableList<SimplifiedPolygon>) {
        when (geom) {
            is Polygon -> addOuterShell(geom, result)
            is MultiPolygon -> {
                for (i in 0 until geom.numGeometries) {
                    (geom.getGeometryN(i) as? Polygon)?.let { addOuterShell(it, result) }
                }
            }

            else -> logger.debug { "Skipping unsupported geometry type: ${geom.geometryType}" }
        }
    }

    private fun addOuterShell(polygon: Polygon, result: MutableList<SimplifiedPolygon>) {
        val coords = polygon.exteriorRing.coordinates.map { listOf(it.x, it.y) }
        if (coords.size >= 4) {
            result.add(SimplifiedPolygon(name = "land_${result.size}", outerRing = coords))
        }
    }
}
