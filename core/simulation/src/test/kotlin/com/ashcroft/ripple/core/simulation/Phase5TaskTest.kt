package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.HotelTask
import com.ashcroft.ripple.core.model.HotelTaskId
import com.ashcroft.ripple.core.model.HotelTaskStatus
import com.ashcroft.ripple.core.model.HotelTaskType
import com.ashcroft.ripple.core.model.RoleKind
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.world.AshcroftLayout
import com.ashcroft.ripple.core.world.AshcroftNav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5TaskTest {
    private val layout = AshcroftLayout.build()
    private val graph = NavGraph.from(layout, AshcroftNav.stairPortals())
    private val locator = RoomLocator.from(layout)
    private val engine = SimulationEngine(layout)

    private fun world(tasks: List<HotelTask>) =
        HotelWorldQueries(layout, graph, locator, AshcroftScenario.initial().people, tasks)

    private fun housekeepingTask() = HotelTask(
        id = HotelTaskId("t1"),
        type = HotelTaskType.CLEAN_ROOM,
        locationId = RoomId("room_101"),
        createdBy = "test",
        requiredRoles = HotelOperations.rolesFor(HotelTaskType.CLEAN_ROOM.department),
        priority = 0.5,
        createdAt = SimTime(0),
    )

    @Test
    fun tasksAriseFromWorldStateNotAtRandom() {
        var state = AshcroftScenario.initial()
        val typesSeen = HashSet<HotelTaskType>()
        repeat(24 * 60) {
            state = engine.step(state)
            state.tasks.forEach { typesSeen += it.type }
        }
        // Guests taking meals, sitting in the lobby, and rooms due cleaning all
        // generate work — none of it injected, all read off the day's state.
        assertTrue("ordinary hotel life should generate tasks", typesSeen.isNotEmpty())
        assertTrue("including daily room cleaning", typesSeen.contains(HotelTaskType.CLEAN_ROOM))
    }

    @Test
    fun rolePermissionsConstrainWhoCanTakeATask() {
        val people = AshcroftScenario.initial().people
        val housekeeper = people.first { it.role == RoleKind.HOUSEKEEPER }
        val chef = people.first { it.role == RoleKind.CHEF }
        val guest = people.first { it.role == RoleKind.GUEST }
        val w = world(listOf(housekeepingTask()))

        assertTrue("a housekeeper may take a cleaning task", w.openTasksFor(housekeeper).any { it.taskId.value == "t1" })
        assertFalse("a chef may not", w.openTasksFor(chef).any { it.taskId.value == "t1" })
        assertTrue("a guest is offered no staff work", w.openTasksFor(guest).isEmpty())
    }

    @Test
    fun unresolvedTasksPersistUntilAttendedOrExpired() {
        val start = listOf(housekeepingTask())
        val people = AshcroftScenario.initial().people
        // With no one attending, the task is still there a minute later.
        val next = HotelOperations.generate(layout, people, start, SimTime(1))
        assertTrue("an unattended task persists", next.any { it.id.value == "t1" && it.isOpen })

        // Past its deadline it expires rather than lingering forever.
        val overdue = housekeepingTask().copy(deadline = SimTime(10))
        val expired = HotelOperations.generate(layout, people, listOf(overdue), SimTime(20))
        assertTrue("an overdue task expires", expired.first { it.id.value == "t1" }.status == HotelTaskStatus.EXPIRED)
    }

    @Test
    fun completedTasksAlterTheWorldAndCreateGuestStaffContact() {
        // Over a day, guest-facing tasks completed while the guest is present should
        // leave the guest with a remembered moment of being helped by a staff member.
        var state = AshcroftScenario.initial()
        val staffIds = state.people.filter { it.role.isStaff }.map { it.id }.toSet()
        var contact = false
        var anyCompleted = false
        repeat(24 * 60) {
            state = engine.step(state)
            if (state.tasks.any { it.status == HotelTaskStatus.COMPLETED }) anyCompleted = true
            if (state.people.any { p -> p.role == RoleKind.GUEST && p.memories.any { it.subjectId in staffIds } }) contact = true
        }
        assertTrue("some tasks should be completed", anyCompleted)
        assertTrue("attending guests should create guest-staff contact", contact)
    }

    @Test
    fun taskGenerationIsDeterministic() {
        val start = AshcroftScenario.initial()
        assertEquals(engine.run(start, 720).tasks, engine.run(start, 720).tasks)
    }
}
