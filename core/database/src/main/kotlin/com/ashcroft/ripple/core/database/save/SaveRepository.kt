package com.ashcroft.ripple.core.database.save

import com.ashcroft.ripple.core.database.dao.SavedGameDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and writes save slots. The single entry point for the save system. */
interface SaveRepository {
    fun observeSaves(): Flow<List<SavedGame>>

    suspend fun save(game: SavedGame)

    suspend fun load(id: String): SavedGame?

    suspend fun delete(id: String)
}

@Singleton
class RoomSaveRepository
    @Inject
    constructor(
        private val dao: SavedGameDao,
    ) : SaveRepository {
        override fun observeSaves(): Flow<List<SavedGame>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

        override suspend fun save(game: SavedGame) = dao.upsert(game.toEntity())

        override suspend fun load(id: String): SavedGame? = dao.findById(id)?.toDomain()

        override suspend fun delete(id: String) = dao.deleteById(id)
    }
