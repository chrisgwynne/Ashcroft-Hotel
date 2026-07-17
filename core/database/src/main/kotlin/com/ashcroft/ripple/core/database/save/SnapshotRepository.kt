package com.ashcroft.ripple.core.database.save

import com.ashcroft.ripple.core.database.dao.WorldSnapshotDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes the durable world-snapshot payload behind a save slot. The
 * payload is an opaque, serialised [com.ashcroft.ripple.core.simulation.WorldState]
 * — the simulation layer encodes and decodes it; this repository only persists it.
 */
interface SnapshotRepository {
    suspend fun save(snapshot: WorldSnapshot)

    suspend fun load(saveId: String): WorldSnapshot?

    suspend fun delete(saveId: String)
}

@Singleton
class RoomSnapshotRepository
    @Inject
    constructor(
        private val dao: WorldSnapshotDao,
    ) : SnapshotRepository {
        override suspend fun save(snapshot: WorldSnapshot) = dao.upsert(snapshot.toEntity())

        override suspend fun load(saveId: String): WorldSnapshot? = dao.findBySaveId(saveId)?.toDomain()

        override suspend fun delete(saveId: String) = dao.deleteBySaveId(saveId)
    }
