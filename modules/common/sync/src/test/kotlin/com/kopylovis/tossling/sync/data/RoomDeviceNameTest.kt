package com.kopylovis.tossling.sync.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomDeviceNameTest {

    private fun device(name: String, ownName: String, hasAlias: Boolean) = RoomDevice(
        id = "a", pairingId = "p", name = name, kind = DeviceKind.PHONE, isSelf = false, isOnline = true,
        seen = 0, since = 0, host = "tossy.example.com", isRoom = true, ownName = ownName, hasAlias = hasAlias,
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
        val raw = """{"s":"https:\/\/tossy.example.com","t":"tk_x","r":"tossy-abc","k":"a2V5","v":2}"""
        assertEquals("tossy.example.com", pairingHost(raw = raw))
        assertEquals("tossy.example.com", pairingHost(raw = """{"s":"https://tossy.example.com/","k":"a2V5"}"""))
        assertEquals("", pairingHost(raw = "not a code"))
    }
}
