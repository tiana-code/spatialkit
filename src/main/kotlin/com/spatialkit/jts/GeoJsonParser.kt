package com.spatialkit.jts

import com.fasterxml.jackson.databind.JsonNode
import io.github.oshai.kotlinlogging.KotlinLogging
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class GeoJsonParser(
    private val geometryFactory: GeometryFactory
) {

    fun parseFeatureCollection(rootNode: JsonNode): List<Geometry> {
        val features = rootNode.path("features")
        if (!features.isArray) {
            return listOfNotNull(parseGeometry(rootNode))
        }
        return features.mapNotNull { feature -> parseGeometry(feature.path("geometry")) }
    }

    private fun parseGeometry(geometryNode: JsonNode): Geometry? {
        if (geometryNode.isMissingNode) return null
        val type = geometryNode.path("type").asText()
        val coordinates = geometryNode.path("coordinates")
        return when (type) {
            "Polygon" -> parsePolygon(coordinates)
            "MultiPolygon" -> {
                if (!coordinates.isArray || coordinates.isEmpty) return null
                val polygons = coordinates.mapNotNull {
                    parsePolygon(it)
                }
                    .filterIsInstance<Polygon>()
                if (polygons.isEmpty()) return null
                geometryFactory.createMultiPolygon(polygons.toTypedArray())
            }

            else -> null
        }
    }

    private fun parsePolygon(coordinates: JsonNode): Geometry? {
        if (!coordinates.isArray || coordinates.isEmpty) return null
        val rings = coordinates.map { ring ->
            val coords = ring.map { coord ->
                Coordinate(coord[0].asDouble(), coord[1].asDouble())
            }
            ensureClosed(coords).toTypedArray()
        }
        if (rings.isEmpty()) return null
        for ((ringIndex, ring) in rings.withIndex()) {
            if (ring.size < 4) {
                logger.warn { "Skipping polygon: ring $ringIndex has ${ring.size} points (minimum 4)" }
                return null
            }
        }
        val shell = geometryFactory.createLinearRing(rings[0])
        val holes = if (rings.size > 1) {
            rings.drop(1).map { geometryFactory.createLinearRing(it) }.toTypedArray()
        } else {
            emptyArray()
        }

        val polygon = geometryFactory.createPolygon(shell, holes)
        if (!polygon.isValid) {
            val repaired = polygon.buffer(0.0)
            if (repaired.isValid && !repaired.isEmpty) {
                logger.debug { "Repaired invalid polygon via buffer(0)" }
                return repaired
            }
            logger.warn { "Skipping unrepairable polygon: ${polygon.toText().take(100)}" }
            return null
        }
        return polygon
    }

    private fun ensureClosed(coords: List<Coordinate>): List<Coordinate> {
        if (coords.size < 2) return coords
        return if (coords.first() == coords.last()) coords
        else coords + coords.first()
    }
}
