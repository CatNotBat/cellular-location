package com.cellularlocation.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CellTowerDao {

    @Query("SELECT * FROM cell_towers WHERE mcc = :mcc AND mnc = :mnc AND tac = :tac AND cid = :cid LIMIT 1")
    suspend fun findTower(mcc: Int, mnc: Int, tac: Int, cid: Long): CellTowerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTower(tower: CellTowerEntity)

    @Query("SELECT * FROM cell_towers ORDER BY timestampMs DESC LIMIT 100")
    fun getRecentTowers(): Flow<List<CellTowerEntity>>

    @Query("DELETE FROM cell_towers WHERE timestampMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)
}
