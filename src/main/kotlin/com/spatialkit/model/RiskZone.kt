package com.spatialkit.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.locationtech.jts.geom.Polygon
import com.spatialkit.model.enums.RiskLevel
import com.spatialkit.model.enums.RiskZoneStatus
import com.spatialkit.model.enums.RiskZoneType
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity protected constructor() {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    var id: UUID? = null
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
        protected set

    @Version
    var version: Long? = null
        protected set

    fun deactivate() {
        isActive = false
    }

    fun activate() {
        isActive = true
    }
}

@Entity
@Table(name = "risk_zones")
class RiskZone protected constructor() : BaseEntity() {

    @Column(name = "name", nullable = false, length = 255)
    var name: String? = null
        protected set

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false, length = 50)
    var zoneType: RiskZoneType = RiskZoneType.RESTRICTED
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    var riskLevel: RiskLevel = RiskLevel.MEDIUM
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: RiskZoneStatus = RiskZoneStatus.ACTIVE
        protected set

    @Column(name = "geometry", columnDefinition = "geometry(Polygon,4326)", nullable = false)
    var geometry: Polygon? = null
        protected set

    @Column(name = "source_name", nullable = false, length = 100)
    var sourceName: String = "MANUAL"
        protected set

    @Column(name = "source_id", length = 255)
    var sourceId: String? = null
        protected set

    @Column(name = "source_url", length = 500)
    var sourceUrl: String? = null
        protected set

    @Column(name = "source_priority", nullable = false)
    var sourcePriority: Int = 50
        protected set

    @Column(name = "effective_from", nullable = false)
    var effectiveFrom: Instant = Instant.now()
        protected set

    @Column(name = "effective_until")
    var effectiveUntil: Instant? = null
        protected set

    @Column(name = "zone_version", nullable = false)
    var zoneVersion: Int = 1
        protected set

    @Column(name = "previous_version_id")
    var previousVersionId: UUID? = null
        protected set

    constructor(
        name: String,
        zoneType: RiskZoneType,
        riskLevel: RiskLevel,
        geometry: Polygon,
        sourceName: String = "MANUAL",
        sourceId: String? = null,
        sourceUrl: String? = null,
        sourcePriority: Int = 50,
        effectiveFrom: Instant = Instant.now(),
        effectiveUntil: Instant? = null,
        description: String? = null
    ) : this() {
        require(name.isNotBlank()) { "Name must not be blank" }
        require(sourceName.isNotBlank()) { "Source name must not be blank" }
        require(sourcePriority in 0..100) { "Source priority must be between 0 and 100" }
        require(effectiveUntil == null || effectiveUntil > effectiveFrom) {
            "effectiveUntil must be after effectiveFrom"
        }

        this.name = name
        this.zoneType = zoneType
        this.riskLevel = riskLevel
        this.geometry = geometry
        this.sourceName = sourceName
        this.sourceId = sourceId
        this.sourceUrl = sourceUrl
        this.sourcePriority = sourcePriority
        this.effectiveFrom = effectiveFrom
        this.effectiveUntil = effectiveUntil
        this.description = description
    }

    fun update(
        name: String? = null,
        description: String? = null,
        riskLevel: RiskLevel? = null,
        status: RiskZoneStatus? = null,
        effectiveUntil: Instant? = null
    ) {
        name?.let {
            require(it.isNotBlank()) { "Name must not be blank" }
            this.name = it
        }
        description?.let { this.description = it }
        riskLevel?.let { this.riskLevel = it }
        status?.let { this.status = it }
        effectiveUntil?.let {
            require(this.effectiveFrom.isBefore(it)) {
                "effectiveUntil must be after effectiveFrom"
            }
            this.effectiveUntil = it
        }
    }

    fun supersede(newVersion: RiskZone) {
        this.status = RiskZoneStatus.SUPERSEDED
        newVersion.previousVersionId = this.id
        newVersion.zoneVersion = this.zoneVersion + 1
    }

    fun expire() {
        this.status = RiskZoneStatus.EXPIRED
        this.effectiveUntil = Instant.now()
    }
}
