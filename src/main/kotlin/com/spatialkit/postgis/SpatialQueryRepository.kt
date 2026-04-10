package com.spatialkit.postgis

import com.spatialkit.model.RiskZone
import com.spatialkit.model.enums.RiskZoneStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface SpatialQueryRepository : JpaRepository<RiskZone, UUID> {

    fun findByStatusAndIsActiveTrue(status: RiskZoneStatus): List<RiskZone>

    @Query(
        """
        SELECT z FROM RiskZone z
        WHERE z.status = com.spatialkit.model.enums.RiskZoneStatus.ACTIVE AND z.isActive = true
        AND z.effectiveFrom <= :now
        AND (z.effectiveUntil IS NULL OR z.effectiveUntil > :now)
        """
    )
    fun findActiveZones(@Param("now") now: Instant): List<RiskZone>

    // Native SQL: 'ACTIVE' matches RiskZoneStatus.ACTIVE via @Enumerated(STRING)
    @Query(
        value = """
        SELECT z.* FROM risk_zones z
        WHERE z.status = 'ACTIVE' AND z.is_active = true
        AND z.effective_from <= :now
        AND (z.effective_until IS NULL OR z.effective_until > :now)
        AND ST_Intersects(z.geometry, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))
        """,
        nativeQuery = true
    )
    fun findActiveZonesInBbox(
        @Param("now") now: Instant,
        @Param("minLon") minLon: Double,
        @Param("minLat") minLat: Double,
        @Param("maxLon") maxLon: Double,
        @Param("maxLat") maxLat: Double
    ): List<RiskZone>

    @Query(
        value = """
        SELECT z.* FROM risk_zones z
        WHERE z.status = 'ACTIVE' AND z.is_active = true
        AND z.effective_from <= :now
        AND (z.effective_until IS NULL OR z.effective_until > :now)
        AND ST_Covers(z.geometry, ST_SetSRID(ST_Point(:lon, :lat), 4326))
        """,
        nativeQuery = true
    )
    fun findZonesContainingPoint(
        @Param("now") now: Instant,
        @Param("lon") lon: Double,
        @Param("lat") lat: Double
    ): List<RiskZone>

    fun findBySourceNameAndSourceId(sourceName: String, sourceId: String): RiskZone?

    fun findBySourceName(sourceName: String): List<RiskZone>

    fun countBySourceName(sourceName: String): Long

    @Query("SELECT z.sourceId FROM RiskZone z WHERE z.sourceName = :sourceName")
    fun findSourceIdsBySourceName(@Param("sourceName") sourceName: String): Set<String>

    @Query("SELECT DISTINCT z.sourceName FROM RiskZone z WHERE z.isActive = true")
    fun findDistinctSourceNames(): List<String>
}
