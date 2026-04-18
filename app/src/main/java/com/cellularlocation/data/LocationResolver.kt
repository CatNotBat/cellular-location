package com.cellularlocation.data

import android.util.Log
import com.cellularlocation.BuildConfig
import com.cellularlocation.data.api.ApiClient
import com.cellularlocation.data.api.MlsCellTower
import com.cellularlocation.data.api.MlsRequest
import com.cellularlocation.data.db.AppDatabase
import com.cellularlocation.data.db.CellTowerEntity
import com.cellularlocation.domain.CellTowerInfo
import com.cellularlocation.domain.LocationResult
import com.cellularlocation.domain.LocationSource
import kotlin.math.log10
import kotlin.math.pow

private const val TAG = "LocationResolver"

class LocationResolver(private val db: AppDatabase) {

    /**
     * Attempts to resolve a geographic position from the given list of visible towers.
     * Tries sources in priority order: OpenCellID API → Mozilla Location Service →
     * local DB cache → weighted centroid trilateration.
     */
    suspend fun resolve(towers: List<CellTowerInfo>): LocationResult? {
        if (towers.isEmpty()) return null

        val primary = towers.maxByOrNull { it.rsrp }!!
        Log.d(TAG, "Resolving ${towers.size} towers. Primary: MCC=${primary.mcc} MNC=${primary.mnc} TAC=${primary.tac} CID=${primary.cid} RSRP=${primary.rsrp}")

        return tryOpenCellId(primary)
            ?: tryMozillaLocationService(towers)
            ?: tryLocalDatabase(primary)
            ?: tryTrilateration(towers)
    }

    // -----------------------------------------------------------------------

    private suspend fun tryOpenCellId(tower: CellTowerInfo): LocationResult? {
        val apiKey = BuildConfig.OPENCELLID_API_KEY
        if (apiKey.isBlank()) return null
        return try {
            val r = ApiClient.openCellId.getLocation(
                apiKey = apiKey,
                mcc = tower.mcc,
                mnc = tower.mnc,
                lac = tower.tac,
                cellId = tower.cid,
            )
            if (r.error != null || r.lat == null || r.lon == null) {
                Log.w(TAG, "OpenCellID: error=${r.error} code=${r.code}")
                return null
            }

            Log.d(TAG, "OpenCellID resolved: ${r.lat}, ${r.lon} ±${r.accuracy}m")
            cacheResolved(tower, r.lat, r.lon, r.accuracy)
            LocationResult(
                latitude = r.lat,
                longitude = r.lon,
                accuracyMeters = r.accuracy ?: 1000.0,
                source = LocationSource.OPENCELLID_API,
                timestampMs = System.currentTimeMillis(),
                towers = listOf(tower),
            )
        } catch (e: Exception) {
            Log.w(TAG, "OpenCellID failed: ${e.message}")
            null
        }
    }

    private suspend fun tryMozillaLocationService(towers: List<CellTowerInfo>): LocationResult? {
        return try {
            val mlsTowers = towers.map { t ->
                MlsCellTower(
                    radioType = t.type.name.lowercase(),
                    mobileCountryCode = t.mcc,
                    mobileNetworkCode = t.mnc,
                    locationAreaCode = t.tac,
                    cellId = t.cid,
                    signalStrength = t.rsrp,
                )
            }
            val r = ApiClient.mozillaLocation.geolocate(MlsRequest(mlsTowers))
            val loc = r.location ?: run {
                Log.w(TAG, "beacondb returned null location")
                return null
            }

            Log.d(TAG, "beacondb resolved: ${loc.lat}, ${loc.lng} ±${r.accuracy}m")
            LocationResult(
                latitude = loc.lat,
                longitude = loc.lng,
                accuracyMeters = r.accuracy ?: 2000.0,
                source = LocationSource.MOZILLA_LOCATION_SERVICE,
                timestampMs = System.currentTimeMillis(),
                towers = towers,
            )
        } catch (e: Exception) {
            Log.w(TAG, "MLS failed: ${e.message}")
            null
        }
    }

    private suspend fun tryLocalDatabase(tower: CellTowerInfo): LocationResult? {
        val entity = db.cellTowerDao().findTower(tower.mcc, tower.mnc, tower.tac, tower.cid)
        if (entity?.latitude == null || entity.longitude == null) return null
        return LocationResult(
            latitude = entity.latitude,
            longitude = entity.longitude,
            accuracyMeters = entity.accuracyMeters ?: 2000.0,
            source = LocationSource.LOCAL_DATABASE,
            timestampMs = System.currentTimeMillis(),
            towers = listOf(tower),
        )
    }

    private suspend fun tryTrilateration(towers: List<CellTowerInfo>): LocationResult? {
        // Only towers whose position is cached locally can participate
        data class KnownTower(val lat: Double, val lon: Double, val distMeters: Double)

        val known = towers.mapNotNull { t ->
            val e = db.cellTowerDao().findTower(t.mcc, t.mnc, t.tac, t.cid)
            if (e?.latitude != null && e.longitude != null) {
                KnownTower(e.latitude, e.longitude, rsrpToDistance(t.rsrp))
            } else null
        }
        if (known.size < 2) return null

        // Weighted centroid: weight = 1 / distance
        val totalWeight = known.sumOf { 1.0 / maxOf(it.distMeters, 1.0) }
        val lat = known.sumOf { it.lat / maxOf(it.distMeters, 1.0) } / totalWeight
        val lon = known.sumOf { it.lon / maxOf(it.distMeters, 1.0) } / totalWeight
        val accuracy = known.minOf { it.distMeters } * 2.0

        return LocationResult(
            latitude = lat,
            longitude = lon,
            accuracyMeters = accuracy,
            source = LocationSource.TRILATERATION,
            timestampMs = System.currentTimeMillis(),
            towers = towers,
        )
    }

    // -----------------------------------------------------------------------

    private suspend fun cacheResolved(
        tower: CellTowerInfo,
        lat: Double,
        lon: Double,
        accuracy: Double?,
    ) {
        db.cellTowerDao().insertTower(
            CellTowerEntity(
                type = tower.type.name,
                mcc = tower.mcc, mnc = tower.mnc, tac = tower.tac, cid = tower.cid,
                pci = tower.pci, rsrp = tower.rsrp, rsrq = tower.rsrq,
                latitude = lat, longitude = lon, accuracyMeters = accuracy,
                timestampMs = System.currentTimeMillis(),
            )
        )
    }

    /**
     * Rough free-space path-loss estimate for LTE Band 3 (1800 MHz).
     * Returns an approximate distance in metres from signal strength.
     */
    private fun rsrpToDistance(rsrp: Int): Double {
        val pathLossDb = -rsrp.toDouble() + 40.0
        return 10.0.pow((pathLossDb - 92.45 - 20.0 * log10(1800.0)) / 20.0) * 1000.0
    }
}
