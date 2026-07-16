package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.Activity
import com.ashcroft.ripple.core.model.ActivityKind
import com.ashcroft.ripple.core.model.Commitment
import com.ashcroft.ripple.core.model.CommitmentKind
import com.ashcroft.ripple.core.model.GridCell
import com.ashcroft.ripple.core.model.Identity
import com.ashcroft.ripple.core.model.LifeStage
import com.ashcroft.ripple.core.model.LocationState
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.NeedState
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.Personality
import com.ashcroft.ripple.core.model.RoleKind
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.WorldPos
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityChooserTest {
    private val layout = AshcroftLayout.build()
    private val chooser = ActivityChooser(RoomLocator.from(layout))

    private fun person(
        needs: NeedState,
        schedule: List<Commitment> = emptyList(),
        role: RoleKind = RoleKind.GUEST,
    ) = Person(
        id = PersonId("t"),
        identity = Identity("Test", 30),
        lifeStage = LifeStage.ADULT,
        role = role,
        personality = Personality.of(),
        needs = needs,
        homeRoom = RoomId("room_101"),
        schedule = schedule,
        location = LocationState(WorldPos(0, GridCell(1, 3)), RoomId("lobby")),
        currentActivity = Activity.IDLE,
    )

    @Test
    fun choiceIsDeterministicForSameInputs() {
        val p = person(NeedState.of(NeedKind.HUNGER to 0.3f))
        val now = SimTime(9L * 60)
        assertEquals(chooser.choose(p, now, 42L), chooser.choose(p, now, 42L))
    }

    @Test
    fun anActiveShiftPullsStaffToWork() {
        val shift = Commitment(CommitmentKind.SHIFT, RoomId("reception"), 8 * 60, 18 * 60, 0.85f)
        val p = person(NeedState.full(), listOf(shift), RoleKind.RECEPTIONIST)
        val chosen = chooser.choose(p, SimTime(10L * 60), AshcroftScenario.DEFAULT_SEED)
        assertEquals(ActivityKind.WORK, chosen.kind)
        assertEquals(RoomId("reception"), chosen.targetRoom)
    }

    @Test
    fun astrongHungerPullsTowardTheRestaurant() {
        val p = person(NeedState.of(NeedKind.HUNGER to 0.02f))
        val chosen = chooser.choose(p, SimTime(13L * 60), AshcroftScenario.DEFAULT_SEED)
        assertEquals(ActivityKind.EAT, chosen.kind)
        assertEquals(RoomId("restaurant"), chosen.targetRoom)
    }

    @Test
    fun plannedDurationIsPositive() {
        val chosen = chooser.choose(person(NeedState.full()), SimTime(3L * 60), 1L)
        assertTrue(chosen.plannedMinutes > 0)
    }
}
