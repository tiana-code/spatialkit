package com.spatialkit.model

import java.util.UUID

data class SpatialResult(
    val id: UUID,
    val name: String,
    val distanceNm: Double,
    val latitude: Double,
    val longitude: Double,
    val metadata: Map<String, Any?> = emptyMap()
)

data class GeoJsonFeature(
    val type: String = "Feature",
    val geometry: GeoJsonGeometry,
    val properties: Map<String, Any?>
)

data class GeoJsonGeometry(
    val type: String,
    val coordinates: Any
)

data class GeoJsonFeatureCollection(
    val type: String = "FeatureCollection",
    val features: List<GeoJsonFeature>
)
