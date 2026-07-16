package com.ashcroft.ripple.core.decision

import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RoleKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateProviderTest {
    private fun provider(world: StubWorld) = ActionCandidateProvider(world)

    @Test
    fun candidatesDependOnRoleAndKnownOpportunities() {
        val world = StubWorld()
        val actor = testPerson(role = RoleKind.RECEPTIONIST, currentRoom = "reception", schedule = listOf(shift("reception", 8, 18)))
        val opportunities = provider(world).knownOpportunities(actor, actor.schedule)
        val candidates = provider(world).candidates(
            actor,
            currentRoom = actor.location.roomId,
            perceivedPeople = emptyList(),
            opportunities = opportunities,
        )
        val verbs = candidates.map { it.verb }.toSet()
        assertTrue(
            "a receptionist should be able to work at reception",
            candidates.any { it.verb == ActionVerb.WORK && it.targetRoom?.value == "reception" },
        )
        assertTrue(verbs.contains(ActionVerb.EAT))
        assertTrue(verbs.contains(ActionVerb.SLEEP))
    }

    @Test
    fun guestsAreNeverOfferedStaffOnlyWork() {
        val world = StubWorld()
        val guest = testPerson(role = RoleKind.GUEST, currentRoom = "reception")
        val opportunities = provider(world).knownOpportunities(guest, emptyList())
        val candidates = provider(world).candidates(guest, guest.location.roomId, emptyList(), opportunities)
        assertFalse("a guest has no workplace to work at", candidates.any { it.verb == ActionVerb.WORK })
    }

    @Test
    fun unreachableOpportunitiesAreExcluded() {
        val world = StubWorld(reachable = StubWorld.DEFAULT_ROOMS.keys - "bar")
        val actor = testPerson(role = RoleKind.GUEST, currentRoom = "reception")
        val opportunities = provider(world).knownOpportunities(actor, emptyList())
        val candidates = provider(world).candidates(actor, actor.location.roomId, emptyList(), opportunities)
        assertFalse("the bar is unreachable, so socialising there is not an option", candidates.any { it.targetRoom?.value == "bar" })
    }

    @Test
    fun nearbyPeopleCreateSocialCandidates() {
        val world = StubWorld()
        val actor = testPerson(id = "a", currentRoom = "reception")
        val perceived = listOf(PerceivedPerson(PersonId("b"), "Bee", RoleKind.GUEST, sentiment = 0.0, alreadyKnown = false))
        val candidates = provider(world).candidates(actor, actor.location.roomId, perceived, opportunities = emptyList())
        assertTrue("a stranger nearby can be greeted", candidates.any { it.verb == ActionVerb.GREET && it.targetPerson == PersonId("b") })
    }

    @Test
    fun acquaintancesAreConversedWithRatherThanGreeted() {
        val world = StubWorld()
        val actor = testPerson(id = "a", currentRoom = "reception")
        val perceived = listOf(PerceivedPerson(PersonId("b"), "Bee", RoleKind.GUEST, sentiment = 0.2, alreadyKnown = true))
        val candidates = provider(world).candidates(actor, actor.location.roomId, perceived, opportunities = emptyList())
        assertTrue(candidates.any { it.verb == ActionVerb.CONVERSE && it.targetPerson == PersonId("b") })
        assertFalse(candidates.any { it.verb == ActionVerb.GREET })
    }
}
