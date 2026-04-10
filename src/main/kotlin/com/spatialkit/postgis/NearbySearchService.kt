package com.spatialkit.postgis

import com.spatialkit.model.SpatialResult
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.util.UUID

private const val METERS_PER_NM = 1852.0

// KNN search: ST_DWithin pre-filters via GIST index, `<->` sorts survivors, ST_Distance on LIMIT rows only
@Service
class NearbySearchService(
    private val jdbcTemplate: JdbcTemplate
) {
    // <-> operator uses sphere approximation for geography; ST_Distance uses spheroid
    // At radii below ~200 nm the ranking difference is negligible
    fun findNearby(
        lon: Double,
        lat: Double,
        radiusNm: Double = 100.0,
        limit: Int = 10
    ): List<SpatialResult> {
        requireValidCoordinates(lat, lon)
        require(radiusNm > 0) { "radiusNm must be positive" }
        require(limit > 0) { "limit must be positive" }
        val radiusM = radiusNm * METERS_PER_NM
        return jdbcTemplate.query(
            """
            SELECT
                p.id,
                p.name,
                ST_Distance(p.location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) / ? AS distance_nm,
                p.latitude,
                p.longitude
            FROM spatial_points p
            WHERE p.is_active = true
              AND p.location IS NOT NULL
              AND ST_DWithin(p.location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
            ORDER BY p.location <-> ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                SpatialResult(
                    id = UUID.fromString(rs.getString("id")),
                    name = rs.getString("name"),
                    distanceNm = rs.getDouble("distance_nm"),
                    latitude = rs.getDouble("latitude"),
                    longitude = rs.getDouble("longitude")
                )
            },
            lon, lat, METERS_PER_NM,
            lon, lat, radiusM,
            lon, lat,
            limit
        )
    }

    fun calculateDistanceNm(
        lon1: Double, lat1: Double,
        lon2: Double, lat2: Double
    ): Double? {
        requireValidCoordinates(lat1, lon1)
        requireValidCoordinates(lat2, lon2)
        return jdbcTemplate.queryForObject(
            """
            SELECT ST_Distance(
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography
            ) / ?
            """.trimIndent(),
            Double::class.java,
            lon1, lat1,
            lon2, lat2,
            METERS_PER_NM
        )
    }

    fun isWithinDistance(
        lon1: Double, lat1: Double,
        lon2: Double, lat2: Double,
        distanceNm: Double
    ): Boolean {
        requireValidCoordinates(lat1, lon1)
        requireValidCoordinates(lat2, lon2)
        require(distanceNm > 0) { "distanceNm must be positive" }
        return jdbcTemplate.queryForObject(
            """
            SELECT ST_DWithin(
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                ?
            )
            """.trimIndent(),
            Boolean::class.java,
            lon1, lat1,
            lon2, lat2,
            distanceNm * METERS_PER_NM
        ) == true
    }

    private fun requireValidCoordinates(lat: Double, lon: Double) {
        require(lat in -90.0..90.0) { "Latitude must be in [-90, 90], got $lat" }
        require(lon in -180.0..180.0) { "Longitude must be in [-180, 180], got $lon" }
    }

    fun isPostGISAvailable(): Boolean {
        return try {
            jdbcTemplate.queryForObject(
                "SELECT PostGIS_Version()",
                String::class.java
            ) != null
        } catch (e: Exception) {
            false
        }
    }
}
