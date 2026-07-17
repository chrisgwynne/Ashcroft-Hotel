package com.ashcroft.ripple.core.decision

import com.ashcroft.ripple.core.model.ActionPhase
import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.RoleKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AwarenessTest {
    private fun doing(verb: ActionVerb) =
        testPerson(role = RoleKind.CHEF).let {
            it.copy(action = it.action.copy(verb = verb, phase = ActionPhase.IN_PROGRESS))
        }

    @Test
    fun aFocusedWorkerNoticesLessThanAnIdleOne() {
        val working = doing(ActionVerb.WORK)
        val idle = testPerson(role = RoleKind.CHEF) // IDLE action
        val busyAwareness = Awareness.of(working, RoleKind.CHEF, alreadyKnown = false, crowding = 1)
        val idleAwareness = Awareness.of(idle, RoleKind.CHEF, alreadyKnown = false, crowding = 1)
        assertTrue("an idle person notices more than a focused one", idleAwareness > busyAwareness)
    }

    @Test
    fun sharingARoomWhileWorkingDoesNotGuaranteeNotice() {
        val working = doing(ActionVerb.WORK)
        val noticed = Awareness.notices(working, RoleKind.CHEF, alreadyKnown = false, crowding = 1)
        assertFalse("head-down at work, a stranger goes unnoticed", noticed)
    }

    @Test
    fun aFamiliarFaceIsMoreLikelyNoticed() {
        val working = doing(ActionVerb.WORK)
        val stranger = Awareness.of(working, RoleKind.CHEF, alreadyKnown = false, crowding = 1)
        val friend = Awareness.of(working, RoleKind.CHEF, alreadyKnown = true, crowding = 1)
        assertTrue("a known colleague catches the eye where a stranger does not", friend > stranger)
    }

    @Test
    fun staffArealertToGuestsTheyMightServe() {
        val busyReceptionist = testPerson(role = RoleKind.RECEPTIONIST).let {
            it.copy(action = it.action.copy(verb = ActionVerb.WORK, phase = ActionPhase.IN_PROGRESS))
        }
        val toGuest = Awareness.of(busyReceptionist, RoleKind.GUEST, alreadyKnown = false, crowding = 1)
        val toColleague = Awareness.of(busyReceptionist, RoleKind.CHEF, alreadyKnown = false, crowding = 1)
        assertTrue("a receptionist is primed to notice a guest", toGuest > toColleague)
    }
}
