package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.HotelLayout
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.RoomKind

/** Fast lookup of rooms by kind, derived once from the layout. */
class RoomLocator(private val byKind: Map<RoomKind, List<RoomId>>) {
    fun firstOfKind(kind: RoomKind): RoomId? = byKind[kind]?.firstOrNull()

    companion object {
        fun from(layout: HotelLayout): RoomLocator {
            val byKind = LinkedHashMap<RoomKind, MutableList<RoomId>>()
            for (room in layout.allRooms) {
                byKind.getOrPut(room.kind) { mutableListOf() }.add(room.id)
            }
            return RoomLocator(byKind)
        }
    }
}
