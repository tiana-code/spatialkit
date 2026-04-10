package com.spatialkit.model.enums

enum class RiskZoneType {
    TSS,
    CANAL,
    RESTRICTED,
    CAUTION,
    PIRACY,
    WAR_RISK,
    SANCTIONS,
    CONFLICT,
    POLITICAL_CLOSURE
}

enum class RiskLevel {
    HIGH,
    MEDIUM,
    LOW
}

enum class RiskZoneStatus {
    ACTIVE,
    EXPIRED,
    SUPERSEDED,
    DRAFT
}
