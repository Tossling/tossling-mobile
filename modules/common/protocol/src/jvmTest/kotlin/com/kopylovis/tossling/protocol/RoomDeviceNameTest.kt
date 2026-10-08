package com.kopylovis.tossling.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomDeviceNameTest {

    private fun device(name: String, ownName: String, hasAlias: Boolean) = RoomDevice(
        id = "a", pairingId = "p", name = name, kind = DeviceKind.PHONE, isSelf = false, isOnline = true,
        seen = 0, since = 0, host = "tossling.example.com", isRoom = true, ownName = ownName, hasAlias = hasAlias,
    )

    @Test
    fun aliasedDeviceShowsItsOwnName() {
        assertEquals("Pixel 9", device(name = "Work phone", ownName = "Pixel 9", hasAlias = true).shownOwnName)
    }

    @Test
    fun deviceWithoutAliasHasNoSecondName() {
        assertNull(device(name = "Pixel 9", ownName = "Pixel 9", hasAlias = false).shownOwnName)
    }

    @Test
    fun historyNamesUseTheNamesGivenOnThisPhone() {
        val devices = listOf(device(name = "MacBook Ozon", ownName = "mbp-OZON", hasAlias = true), device(name = "Pixel 9", ownName = "Pixel 9", hasAlias = false))
        assertEquals("MacBook Ozon", localDeviceNames(names = "mbp-OZON", devices = devices))
        assertEquals("MacBook Ozon, Pixel 9", localDeviceNames(names = "mbp-OZON, Pixel 9", devices = devices))
        assertEquals("Other Mac", localDeviceNames(names = "Other Mac", devices = devices))
    }

    @Test
    fun pairingHostReadsEscapedSlashes() {
        val raw = """{"s":"https:\/\/tossling.example.com","t":"tk_x","r":"tossling-abc","k":"a2V5","v":2}"""
        assertEquals("tossling.example.com", pairingHost(raw = raw))
        assertEquals("tossling.example.com", pairingHost(raw = """{"s":"https://tossling.example.com/","k":"a2V5"}"""))
        assertEquals("", pairingHost(raw = "not a code"))
    }

    @Test
    fun roomsUseEitherPrefix() {
        assertTrue(isRoomTopic(topic = "tossling-0123"))
        assertTrue(isRoomTopic(topic = "tossy-0123"))
        assertFalse(isRoomTopic(topic = "axrock"))
        assertFalse(isRoomTopic(topic = "tossling"))
    }
}
