package com.cellularlocation.data.api

import retrofit2.http.GET
import retrofit2.http.Query

data class OpenCellIdResponse(
    // Success fields
    val lat: Double?,
    val lon: Double?,
    val accuracy: Double?,
    // Error fields
    val error: String?,
    val code: Int?,
)

interface OpenCellIdApi {
    @GET("cell/get")
    suspend fun getLocation(
        @Query("key") apiKey: String,
        @Query("mcc") mcc: Int,
        @Query("mnc") mnc: Int,
        @Query("lac") lac: Int,
        @Query("cellid") cellId: Long,
        @Query("format") format: String = "json",
    ): OpenCellIdResponse
}
