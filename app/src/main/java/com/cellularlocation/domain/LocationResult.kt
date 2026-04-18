package com.cellularlocation.domain

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val source: LocationSource,
    val timestampMs: Long,
    val towers: List<CellTowerInfo>,
)

enum class LocationSource {
    OPENCELLID_API,
    MOZILLA_LOCATION_SERVICE,
    LOCAL_DATABASE,
    TRILATERATION,
    UNKNOWN,
}
