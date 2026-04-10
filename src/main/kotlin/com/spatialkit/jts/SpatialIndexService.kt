package com.spatialkit.jts

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.locationtech.jts.geom.Coordinate as JtsCoordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.prep.PreparedGeometry
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import org.locationtech.jts.index.strtree.STRtree
import kotlin.math.cos
import kotlin.math.PI
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class SpatialIndexService(
    private val resourceLoader: ResourceLoader,
    private val objectMapper: ObjectMapper,
    private val geometryFactory: GeometryFactory,
    private val geoJsonParser: GeoJsonParser,
    private val polygonSimplifier: PolygonSimplifier
) {

    @Value("\${spatialkit.land-polygons.path:classpath:geodata/ne_10m_land.geojson}")
    private lateinit var landPolygonsPath: String

    @Value("\${spatialkit.land-polygons.lakes-path:classpath:geodata/ne_10m_lakes.geojson}")
    private lateinit var lakesPath: String

    @Value("\${spatialkit.land-polygons.enabled:true}")
    private var enabled: Boolean = true

    private var landIndex: STRtree? = null
    private var lakesIndex: STRtree? = null
    private var landGeometries: List<Geometry> = emptyList()
    private var lakeGeometries: List<Geometry> = emptyList()

    @Volatile
    private var dataLoaded = false
    private var loadMessage: String = "Not initialized"

    @Volatile
    private var cachedSimplifiedPolygons: List<SimplifiedPolygon> = emptyList()

    @PostConstruct
    fun init() {
        if (!enabled) {
            loadMessage = "Land polygon detection disabled by configuration"
            logger.info { loadMessage }
            return
        }
        loadIndex(landPolygonsPath, isLand = true)
        loadIndex(lakesPath, isLand = false)
        try {
            cachedSimplifiedPolygons = polygonSimplifier.buildSimplifiedPolygons(landGeometries)
            logger.info { "Cached ${cachedSimplifiedPolygons.size} simplified polygons for client delivery" }
        } catch (e: Exception) {
            logger.warn { "Failed to build simplified polygon cache: ${e.message}" }
        }
    }

    private fun loadIndex(path: String, isLand: Boolean) {
        try {
            val resource = resourceLoader.getResource(path)
            if (!resource.exists()) {
                val msg = "File not found: $path"
                if (isLand) {
                    loadMessage = msg; logger.error { msg }
                } else logger.warn { "$msg. Lake detection disabled." }
                return
            }
            val rootNode = resource.inputStream.bufferedReader().use { objectMapper.readTree(it) }
            val geometries = geoJsonParser.parseFeatureCollection(rootNode)
            if (geometries.isEmpty()) {
                if (isLand) {
                    loadMessage = "No valid geometries in $path"; logger.warn { loadMessage }
                }
                return
            }
            val pgFactory = PreparedGeometryFactory()
            val tree = STRtree().also { t ->
                geometries.forEach { geom ->
                    t.insert(geom.envelopeInternal, pgFactory.create(geom))
                }
            }
            if (isLand) {
                landIndex = tree
                landGeometries = geometries
                dataLoaded = true
                loadMessage = "Loaded ${geometries.size} land polygons"
            } else {
                lakesIndex = tree
                lakeGeometries = geometries
            }
            logger.info { "Loaded ${geometries.size} ${if (isLand) "land polygons" else "lakes"}" }
        } catch (e: Exception) {
            val msg = "Failed to load ${if (isLand) "land polygons" else "lakes"}: ${e.message}"
            if (isLand) loadMessage = msg
            logger.warn { msg }
        }
    }

    fun isOnLand(lat: Double, lon: Double): SpatialQueryResult<Boolean> {
        requireValidCoordinates(lat, lon)
        val index = landIndex
        if (!dataLoaded || index == null) {
            return SpatialQueryResult.Degraded(true, "Land polygon data not loaded")
        }
        val point = geometryFactory.createPoint(JtsCoordinate(lon, lat))

        @Suppress("UNCHECKED_CAST")
        val candidates = index.query(point.envelopeInternal) as List<PreparedGeometry>
        return SpatialQueryResult.Success(candidates.any { it.contains(point) })
    }

    fun isOnWater(lat: Double, lon: Double): Boolean {
        requireValidCoordinates(lat, lon)
        if (isInsideLake(lat, lon)) return true
        return !isOnLand(lat, lon).valueOrFallback()
    }

    fun crossesLand(
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double
    ): SpatialQueryResult<Boolean> {
        requireValidCoordinates(fromLat, fromLon)
        requireValidCoordinates(toLat, toLon)
        val index = landIndex
        if (!dataLoaded || index == null) {
            return SpatialQueryResult.Degraded(true, "Land polygon data not loaded")
        }
        val line = geometryFactory.createLineString(
            arrayOf(JtsCoordinate(fromLon, fromLat), JtsCoordinate(toLon, toLat))
        )

        @Suppress("UNCHECKED_CAST")
        val landCandidates = index.query(line.envelopeInternal) as List<PreparedGeometry>
        val landIntersections = landCandidates.mapNotNull { prepGeom ->
            if (!prepGeom.intersects(line)) return@mapNotNull null

            val intersection = line.intersection(prepGeom.geometry)
            if (!intersection.isEmpty && intersection.length > 0.00001) intersection else null
        }
        if (landIntersections.isEmpty()) return SpatialQueryResult.Success(false)
        val lakes = lakesIndex
        if (lakes != null) {
            @Suppress("UNCHECKED_CAST")
            val lakeCandidates = lakes.query(line.envelopeInternal) as List<PreparedGeometry>
            for (landIntersection in landIntersections) {
                val coveredByLake = lakeCandidates.any { lake ->
                    lake.contains(landIntersection) || lake.covers(landIntersection)
                }
                if (!coveredByLake) return SpatialQueryResult.Success(true)
            }
            return SpatialQueryResult.Success(false)
        }
        return SpatialQueryResult.Success(true)
    }

    fun approxDistanceToNearestLandNm(
        lat: Double,
        lon: Double,
        searchRadiusDegrees: Double = 5.0
    ): SpatialQueryResult<Double?> {
        requireValidCoordinates(lat, lon)
        require(searchRadiusDegrees > 0) { "searchRadiusDegrees must be positive" }
        val index = landIndex
        if (!dataLoaded || index == null) {
            return SpatialQueryResult.Degraded(null, "Land polygon data not loaded")
        }
        val point = geometryFactory.createPoint(JtsCoordinate(lon, lat))
        val searchEnvelope = point.envelopeInternal.apply { expandBy(searchRadiusDegrees) }

        @Suppress("UNCHECKED_CAST")
        val candidates = index.query(searchEnvelope) as List<PreparedGeometry>
        if (candidates.isEmpty()) return SpatialQueryResult.Success(null)
        val minDistDegrees = candidates.minOfOrNull { point.distance(it.geometry) }
            ?: return SpatialQueryResult.Success(null)
        val avgLatRad = lat * PI / 180.0
        val nmPerDegreeLon = 60.0 * cos(avgLatRad)
        return SpatialQueryResult.Success(minDistDegrees * ((60.0 + nmPerDegreeLon) / 2.0))
    }

    fun getSimplifiedPolygons(): List<SimplifiedPolygon> = cachedSimplifiedPolygons

    fun getStatus(): SpatialIndexStatus = SpatialIndexStatus(
        loaded = dataLoaded,
        polygonCount = landGeometries.size,
        lakeCount = lakeGeometries.size,
        message = loadMessage
    )

    private fun isInsideLake(lat: Double, lon: Double): Boolean {
        val index = lakesIndex ?: return false
        val point = geometryFactory.createPoint(JtsCoordinate(lon, lat))

        @Suppress("UNCHECKED_CAST")
        val candidates = index.query(point.envelopeInternal) as List<PreparedGeometry>
        return candidates.any { it.contains(point) }
    }

    private fun requireValidCoordinates(lat: Double, lon: Double) {
        require(lat in -90.0..90.0) { "Latitude must be in [-90, 90], got $lat" }
        require(lon in -180.0..180.0) { "Longitude must be in [-180, 180], got $lon" }
    }
}

data class SimplifiedPolygon(
    val name: String,
    val outerRing: List<List<Double>>
)

data class SpatialIndexStatus(
    val loaded: Boolean,
    val polygonCount: Int,
    val lakeCount: Int,
    val message: String
)
