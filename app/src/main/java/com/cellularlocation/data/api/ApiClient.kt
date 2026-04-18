package com.cellularlocation.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val openCellId: OpenCellIdApi =
        retrofit("https://opencellid.org/").create(OpenCellIdApi::class.java)

    val mozillaLocation: MozillaLocationApi =
        retrofit("https://beacondb.net/").create(MozillaLocationApi::class.java)
}
