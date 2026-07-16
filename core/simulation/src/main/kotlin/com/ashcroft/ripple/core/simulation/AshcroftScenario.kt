package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.ActionState
import com.ashcroft.ripple.core.model.BehaviourHistory
import com.ashcroft.ripple.core.model.Commitment
import com.ashcroft.ripple.core.model.CommitmentKind
import com.ashcroft.ripple.core.model.GridCell
import com.ashcroft.ripple.core.model.HotelLayout
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
import com.ashcroft.ripple.core.model.TraitKind
import com.ashcroft.ripple.core.model.WorldPos
import com.ashcroft.ripple.core.world.AshcroftLayout

/**
 * The deterministic opening state of The Ashcroft: a small cast of staff,
 * residents and guests with distinct personalities, needs, schedules and
 * starting places. These are *starting conditions only* — no outcomes are
 * scripted. What happens next emerges from needs, commitments and the chooser.
 */
object AshcroftScenario {
    const val DEFAULT_SEED = 1924L

    /** Simulation begins at 06:00 on day 0. */
    private val START = SimTime(6L * SimTime.MINUTES_PER_HOUR)

    fun initial(seed: Long = DEFAULT_SEED, layout: HotelLayout = AshcroftLayout.build()): WorldState {
        val people = listOf(
            staff(
                "evelyn", "Evelyn Price", 54, RoleKind.GENERAL_MANAGER, "managers_office", shift("managers_office", 8, 18), layout,
                Personality.of(TraitKind.CONSCIENTIOUSNESS to 0.9f, TraitKind.AMBITION to 0.7f, TraitKind.PATIENCE to 0.75f),
            ),
            staff(
                "maya", "Maya Bennett", 27, RoleKind.RECEPTIONIST, "staff_room", shift("reception", 7, 15), layout,
                Personality.of(TraitKind.AMBITION to 0.9f, TraitKind.EXTRAVERSION to 0.6f, TraitKind.CONSCIENTIOUSNESS to 0.7f),
            ),
            staff(
                "daniel", "Daniel Reed", 34, RoleKind.DUTY_MANAGER, "staff_room", shift("reception", 14, 22), layout,
                Personality.of(TraitKind.CONSCIENTIOUSNESS to 0.8f, TraitKind.AGREEABLENESS to 0.55f),
            ),
            staff(
                "arthur", "Arthur Cole", 61, RoleKind.CONCIERGE, "staff_room", shift("lobby", 8, 16), layout,
                Personality.of(TraitKind.SOCIABILITY to 0.85f, TraitKind.PATIENCE to 0.9f, TraitKind.EXTRAVERSION to 0.7f),
            ),
            staff(
                "lena", "Lena Morris", 29, RoleKind.HOUSEKEEPER, "staff_room", shift("housekeeping", 6, 14), layout,
                Personality.of(TraitKind.CONSCIENTIOUSNESS to 0.85f, TraitKind.OPENNESS to 0.7f),
            ),
            staff(
                "theo", "Theo Ward", 23, RoleKind.CHEF, "staff_room", shift("kitchen", 10, 22), layout,
                Personality.of(TraitKind.CONSCIENTIOUSNESS to 0.4f, TraitKind.EXTRAVERSION to 0.7f, TraitKind.PATIENCE to 0.3f),
            ),
            staff(
                "sam", "Sam Okafor", 31, RoleKind.BARTENDER, "staff_room", shift("bar", 17, 24), layout,
                Personality.of(TraitKind.SOCIABILITY to 0.9f, TraitKind.EXTRAVERSION to 0.8f),
            ),
            resident(
                "george", "George Ashcroft", 82, "suite_1", layout,
                Personality.of(TraitKind.PATIENCE to 0.8f, TraitKind.OPENNESS to 0.4f),
            ),
            guest(
                "ethan", "Ethan Carter", 41, "room_102", layout, listOf(mealCommitment()),
                Personality.of(TraitKind.AMBITION to 0.7f, TraitKind.SOCIABILITY to 0.5f),
            ),
            guest(
                "sophie", "Sophie Bell", 26, "room_101", layout, listOf(appointment("lobby", 10, 11), mealCommitment()),
                Personality.of(TraitKind.CONSCIENTIOUSNESS to 0.8f, TraitKind.EXTRAVERSION to 0.4f),
            ),
            guest(
                "naomi", "Naomi Harris", 38, "room_104", layout, listOf(mealCommitment()),
                Personality.of(TraitKind.AGREEABLENESS to 0.6f, TraitKind.SOCIABILITY to 0.55f),
            ),
            guest(
                "paul", "Paul Harris", 40, "room_104", layout, listOf(mealCommitment()),
                Personality.of(TraitKind.PATIENCE to 0.35f, TraitKind.AGREEABLENESS to 0.5f),
            ),
        )
        return WorldState(seed = seed, clock = START, people = people)
    }

    private fun shift(room: String, startHour: Int, endHour: Int): List<Commitment> = listOf(
        Commitment(
            kind = CommitmentKind.SHIFT,
            location = RoomId(room),
            startMinuteOfDay = startHour * 60,
            endMinuteOfDay = endHour * 60,
            strength = 0.85f,
        ),
    )

    private fun mealCommitment(): Commitment = Commitment(
        kind = CommitmentKind.MEAL,
        location = RoomId("restaurant"),
        startMinuteOfDay = 19 * 60,
        endMinuteOfDay = 20 * 60,
        strength = 0.6f,
    )

    private fun appointment(room: String, startHour: Int, endHour: Int): Commitment = Commitment(
        kind = CommitmentKind.APPOINTMENT,
        location = RoomId(room),
        startMinuteOfDay = startHour * 60,
        endMinuteOfDay = endHour * 60,
        strength = 0.9f,
    )

    private fun staff(
        id: String,
        name: String,
        age: Int,
        role: RoleKind,
        homeRoom: String,
        schedule: List<Commitment>,
        layout: HotelLayout,
        personality: Personality,
    ): Person = person(id, name, age, LifeStage.ADULT, role, homeRoom, schedule, layout, personality, startRoom = "staff_room")

    private fun resident(id: String, name: String, age: Int, homeRoom: String, layout: HotelLayout, personality: Personality): Person =
        person(id, name, age, LifeStage.ELDER, RoleKind.RESIDENT, homeRoom, emptyList(), layout, personality, startRoom = homeRoom)

    private fun guest(
        id: String,
        name: String,
        age: Int,
        homeRoom: String,
        layout: HotelLayout,
        schedule: List<Commitment>,
        personality: Personality,
    ): Person = person(id, name, age, LifeStage.ADULT, RoleKind.GUEST, homeRoom, schedule, layout, personality, startRoom = homeRoom)

    private fun person(
        id: String,
        name: String,
        age: Int,
        lifeStage: LifeStage,
        role: RoleKind,
        homeRoom: String,
        schedule: List<Commitment>,
        layout: HotelLayout,
        personality: Personality,
        startRoom: String,
    ): Person {
        val start = startLocation(layout, startRoom)
        return Person(
            id = PersonId(id),
            identity = Identity(name, age),
            lifeStage = lifeStage,
            role = role,
            personality = personality,
            needs = NeedState.of(
                NeedKind.HUNGER to 0.65f,
                NeedKind.REST to 0.75f,
                NeedKind.SOCIAL to 0.6f,
                NeedKind.HYGIENE to 0.8f,
            ),
            homeRoom = RoomId(homeRoom),
            schedule = schedule,
            goals = emptyList(),
            memories = emptyList(),
            acquaintances = emptySet(),
            behaviour = BehaviourHistory(),
            money = if (role.isStaff) 120 else 260,
            location = start,
            action = ActionState.IDLE,
            lastDecision = null,
        )
    }

    private fun startLocation(layout: HotelLayout, roomId: String): LocationState {
        val room = layout.room(RoomId(roomId)) ?: layout.allRooms.first()
        val floor = layout.floors.first { it.id == room.floorId }.level
        val centre = GridCell(room.origin.col + room.size.cols / 2, room.origin.row + room.size.rows / 2)
        return LocationState(pos = WorldPos(floor, centre), roomId = room.id, path = emptyList())
    }
}
