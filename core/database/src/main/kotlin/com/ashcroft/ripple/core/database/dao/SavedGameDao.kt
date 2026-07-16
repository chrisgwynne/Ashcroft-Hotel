package com.ashcroft.ripple.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ashcroft.ripple.core.database.entity.SavedGameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedGameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedGameEntity)

    @Query("SELECT * FROM saved_games ORDER BY updatedAtEpoch DESC")
    fun observeAll(): Flow<List<SavedGameEntity>>

    @Query("SELECT * FROM saved_games WHERE id = :id")
    suspend fun findById(id: String): SavedGameEntity?

    @Query("DELETE FROM saved_games WHERE id = :id")
    suspend fun deleteById(id: String)
}
