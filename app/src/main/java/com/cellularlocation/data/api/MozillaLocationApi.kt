package com.cellularlocation.data.api

import retrofit2.http.Body
import retrofit2.http.POST

data class MlsCellTower(
    val radioType: String,
    val mobileCountryCode: Int,
    val mobileNetworkCode: Int,
    val locationAreaCode: Int,
    val cellId: Long,
    val signalStrength: Int,
)

data class MlsRequest(val cellTowers: List<MlsCellTower>)

data class MlsResponse(
    val location: MlsLocation?,
    val accuracy: Double?,
)

data class MlsLocation(val lat: Double, val lng: Double)

interface MozillaLocationApi {
    // beacondb.net is a free MLS-compatible replacement (MLS was shut down Sep 2024)
    @POST("v1/geolocate")
    suspend fun geolocate(@Body request: MlsRequest): MlsResponse
}
