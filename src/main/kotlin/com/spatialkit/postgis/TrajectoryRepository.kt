package com.spatialkit.postgis

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

// Raw JdbcTemplate because Hibernate cannot auto-map PostGIS geometry result sets
@Repository
class TrajectoryRepository(
    private val jdbcTemplate: JdbcTemplate
) {

    fun updatePlannedTrajectory(planId: UUID, wkt: String): Int {
        val rowsAffected = jdbcTemplate.update(
            """
            UPDATE voyage_plans
            SET planned_trajectory = validated.geom
            FROM (
                SELECT CASE
                    WHEN GeometryType(parsed.geom) IN ('LINESTRING', 'MULTILINESTRING')
                         AND ST_IsValid(parsed.geom)
                    THEN ST_Force2D(parsed.geom)
                    ELSE NULL
                END AS geom
                FROM (SELECT ST_GeomFromText(?, 4326) AS geom) parsed
            ) validated
            WHERE id = ? AND validated.geom IS NOT NULL
            """.trimIndent(),
            wkt, planId
        )
        if (rowsAffected == 0) {
            throw NoSuchElementException("No voyage plan found with id=$planId or invalid WKT geometry")
        }
        return rowsAffected
    }

    fun findPlannedTrajectoryGeoJson(planId: UUID): TrajectoryResult? {
        return jdbcTemplate.query(
            """
            SELECT ST_AsGeoJSON(planned_trajectory) AS geojson,
                   ST_NumPoints(planned_trajectory)  AS point_count
            FROM voyage_plans
            WHERE id = ? AND planned_trajectory IS NOT NULL
            """.trimIndent(),
            { rs, _ ->
                TrajectoryResult(
                    geojson = rs.getString("geojson"),
                    pointCount = rs.getInt("point_count")
                )
            },
            planId
        ).firstOrNull()
    }

    fun findActiveVoyageTrajectoryGeoJson(vesselId: UUID): List<VesselTrajectoryResult> {
        return jdbcTemplate.query(
            """
            SELECT vp.id                              AS plan_id,
                   ST_AsGeoJSON(vp.planned_trajectory) AS geojson,
                   ST_NumPoints(vp.planned_trajectory)  AS point_count
            FROM voyage_plans vp
            WHERE vp.vessel_id = ?
              AND vp.status    = 'ACTIVE'
              AND vp.planned_trajectory IS NOT NULL
            """.trimIndent(),
            { rs, _ ->
                VesselTrajectoryResult(
                    planId = UUID.fromString(rs.getString("plan_id")),
                    geojson = rs.getString("geojson"),
                    pointCount = rs.getInt("point_count")
                )
            },
            vesselId
        )
    }

    // Threshold 15 points: catches plans saved with only port-to-port straight lines
    fun findIdsNeedingTrajectoryRebuild(): List<UUID> {
        return jdbcTemplate.query(
            """
            SELECT id FROM voyage_plans
            WHERE planned_trajectory IS NULL
               OR ST_NumPoints(planned_trajectory) <= 15
            ORDER BY id
            """.trimIndent()
        ) { rs, _ -> UUID.fromString(rs.getString("id")) }
    }
}

data class TrajectoryResult(
    val geojson: String,
    val pointCount: Int
)

data class VesselTrajectoryResult(
    val planId: UUID,
    val geojson: String,
    val pointCount: Int
)
