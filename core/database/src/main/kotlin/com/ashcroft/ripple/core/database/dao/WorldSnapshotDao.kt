package com.ashcroft.ripple.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ashcroft.ripple.core.database.entity.WorldSnapshotEntity

@Dao
interface WorldSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorldSnapshotEntity)

    @Query("SELECT * FROM world_snapshots WHERE saveId = :saveId")
    suspend fun findBySaveId(saveId: String): WorldSnapshotEntity?

    @Query("DELETE FROM world_snapshots WHERE saveId = :saveId")
    suspend fun deleteBySaveId(saveId: String)
}
