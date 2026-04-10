package com.spatialkit.jts

sealed class SpatialQueryResult<out T> {
    data class Success<T>(val value: T) : SpatialQueryResult<T>()
    data class Degraded<T>(val fallback: T, val reason: String) : SpatialQueryResult<T>()

    fun valueOrFallback(): T = when (this) {
        is Success -> value
        is Degraded -> fallback
    }

    val isDegraded: Boolean get() = this is Degraded
}
