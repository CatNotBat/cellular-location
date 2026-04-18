package com.cellularlocation.domain

data class CellTowerInfo(
    val type: CellType,
    val mcc: Int,
    val mnc: Int,
    /** Tracking Area Code (LTE/NR) or Location Area Code (GSM/WCDMA) */
    val tac: Int,
    val cid: Long,
    /** Physical Cell ID — LTE/NR only, -1 if not applicable */
    val pci: Int,
    /** dBm: RSRP for LTE/NR, RSSI for GSM/WCDMA */
    val rsrp: Int,
    /** dB: RSRQ for LTE/NR, -1 if not available */
    val rsrq: Int,
    val timestampMs: Long,
    val isRegistered: Boolean,
)

enum class CellType { GSM, WCDMA, LTE, NR, UNKNOWN }
