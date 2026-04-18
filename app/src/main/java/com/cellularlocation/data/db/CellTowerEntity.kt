package com.cellularlocation.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cell_towers",
    indices = [Index(value = ["mcc", "mnc", "tac", "cid"], unique = true)]
)
data class CellTowerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val mcc: Int,
    val mnc: Int,
    val tac: Int,
    val cid: Long,
    val pci: Int,
    val rsrp: Int,
    val rsrq: Int,
    /** null until resolved via API */
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Double?,
    val timestampMs: Long,
)
