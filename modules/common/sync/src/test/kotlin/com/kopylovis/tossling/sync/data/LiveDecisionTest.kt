package com.kopylovis.tossling.sync.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDecisionTest {

    @Test
    fun withoutFirebaseTheConnectionIsOn() {
        assertTrue(liveDecision(choice = null, hasPush = false, serverPush = null))
        assertTrue(liveDecision(choice = null, hasPush = false, serverPush = true))
    }

    @Test
    fun aServerWithoutPushTurnsItOnEvenWithFirebase() {
        assertTrue(liveDecision(choice = null, hasPush = true, serverPush = false))
    }

    @Test
    fun firebaseThroughAPushServerKeepsItOff() {
        assertFalse(liveDecision(choice = null, hasPush = true, serverPush = true))
        assertFalse(liveDecision(choice = null, hasPush = true, serverPush = null))
    }

    @Test
    fun theUsersChoiceWins() {
        assertFalse(liveDecision(choice = false, hasPush = true, serverPush = false))
        assertFalse(liveDecision(choice = false, hasPush = false, serverPush = null))
        assertTrue(liveDecision(choice = true, hasPush = true, serverPush = true))
    }
}
