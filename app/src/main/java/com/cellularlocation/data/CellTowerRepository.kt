package com.cellularlocation.data

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import com.cellularlocation.domain.CellTowerInfo
import com.cellularlocation.domain.CellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

private const val TAG = "CellTowerRepository"
private const val SCAN_INTERVAL_MS = 5_000L

class CellTowerRepository(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    val rootScanner = RootCellScanner()

    /**
     * Emits a fresh list of visible cell towers.
     *
     * Two scanning strategies run concurrently:
     * 1. Passive [TelephonyCallback] — fires immediately on any network change.
     * 2. Active [requestCellInfoUpdate] every [SCAN_INTERVAL_MS] — forces the modem
     *    to report ALL visible cells including neighbours, not just the serving cell.
     *
     * If both strategies return nothing (no SIM) and root is available, falls back
     * to [RootCellScanner] which invokes the telephony API as uid=0.
     */
    @SuppressLint("MissingPermission")
    fun observeCellTowers(): Flow<List<CellTowerInfo>> = callbackFlow {

        // --- 1. Passive callback -------------------------------------------
        val callback = object : TelephonyCallback(), TelephonyCallback.CellInfoListener {
            override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                val towers = cellInfo.mapNotNull { it.toDomain() }
                Log.d(TAG, "Passive callback: ${towers.size} towers")
                if (towers.isNotEmpty()) trySend(towers)
            }
        }
        telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)

        // --- 2. Periodic active scan ----------------------------------------
        launch(Dispatchers.IO) {
            while (isActive) {
                val towers = forceScan()
                Log.d(TAG, "Active scan: ${towers.size} towers")
                if (towers.isNotEmpty()) {
                    trySend(towers)
                } else if (rootScanner.isRootAvailable()) {
                    val rootTowers = rootScanner.scanCells()
                    Log.d(TAG, "Root scan: ${rootTowers.size} towers")
                    if (rootTowers.isNotEmpty()) trySend(rootTowers)
                }
                delay(SCAN_INTERVAL_MS)
            }
        }

        awaitClose { telephonyManager.unregisterTelephonyCallback(callback) }
    }

    /** One-shot snapshot, with root fallback. */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentCellTowers(): List<CellTowerInfo> {
        val standard = telephonyManager.allCellInfo?.mapNotNull { it.toDomain() } ?: emptyList()
        if (standard.isNotEmpty()) return standard
        return if (rootScanner.isRootAvailable()) rootScanner.scanCells() else emptyList()
    }

    // -----------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private suspend fun forceScan(): List<CellTowerInfo> =
        withTimeoutOrNull(4_000L) {
            suspendCancellableCoroutine { cont ->
                val exec = Executors.newSingleThreadExecutor()
                telephonyManager.requestCellInfoUpdate(
                    exec,
                    object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cellInfo: List<CellInfo>) {
                            exec.shutdown()
                            if (cont.isActive) cont.resume(cellInfo.mapNotNull { it.toDomain() })
                        }
                        override fun onError(errorCode: Int, detail: Throwable?) {
                            exec.shutdown()
                            Log.w(TAG, "requestCellInfoUpdate error $errorCode: ${detail?.message}")
                            if (cont.isActive) cont.resume(emptyList())
                        }
                    }
                )
            }
        } ?: emptyList()
}

// ---------------------------------------------------------------------------
// Extension: CellInfo → domain model
// ---------------------------------------------------------------------------

internal fun CellInfo.toDomain(): CellTowerInfo? {
    return when (this) {
        is CellInfoLte -> {
            val id = cellIdentity
            val sig = cellSignalStrength
            val mcc = id.mccString?.toIntOrNull() ?: return null
            val mnc = id.mncString?.toIntOrNull() ?: return null
            CellTowerInfo(
                type = CellType.LTE,
                mcc = mcc, mnc = mnc,
                tac = id.tac,
                cid = id.ci.toLong(),
                pci = id.pci,
                rsrp = sig.rsrp,
                rsrq = sig.rsrq,
                timestampMs = System.currentTimeMillis(),
                isRegistered = isRegistered,
            )
        }

        is CellInfoNr -> {
            val id = cellIdentity as CellIdentityNr
            val sig = cellSignalStrength as CellSignalStrengthNr
            val mcc = id.mccString?.toIntOrNull() ?: return null
            val mnc = id.mncString?.toIntOrNull() ?: return null
            CellTowerInfo(
                type = CellType.NR,
                mcc = mcc, mnc = mnc,
                tac = id.tac,
                cid = id.nci,
                pci = id.pci,
                rsrp = sig.ssRsrp,
                rsrq = sig.ssRsrq,
                timestampMs = System.currentTimeMillis(),
                isRegistered = isRegistered,
            )
        }

        is CellInfoGsm -> {
            val id = cellIdentity
            val sig = cellSignalStrength
            val mcc = id.mccString?.toIntOrNull() ?: return null
            val mnc = id.mncString?.toIntOrNull() ?: return null
            CellTowerInfo(
                type = CellType.GSM,
                mcc = mcc, mnc = mnc,
                tac = id.lac,
                cid = id.cid.toLong(),
                pci = -1,
                rsrp = sig.dbm,
                rsrq = Int.MIN_VALUE,
                timestampMs = System.currentTimeMillis(),
                isRegistered = isRegistered,
            )
        }

        is CellInfoWcdma -> {
            val id = cellIdentity
            val sig = cellSignalStrength
            val mcc = id.mccString?.toIntOrNull() ?: return null
            val mnc = id.mncString?.toIntOrNull() ?: return null
            CellTowerInfo(
                type = CellType.WCDMA,
                mcc = mcc, mnc = mnc,
                tac = id.lac,
                cid = id.cid.toLong(),
                pci = id.psc,
                rsrp = sig.dbm,
                rsrq = Int.MIN_VALUE,
                timestampMs = System.currentTimeMillis(),
                isRegistered = isRegistered,
            )
        }

        else -> null
    }
}
