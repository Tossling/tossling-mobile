package com.kopylovis.tossling.sync.data

import com.kopylovis.tossling.protocol.Member
import com.kopylovis.tossling.protocol.Pairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomBackupTest {

    private fun room(n: Int) = Pairing(
        server = "https://tossling.example.com", token = "tk_" + "a".repeat(29), key = "k".repeat(44), room = "tossling-${"%024d".format(n)}",
        macName = "MacBook $n", macId = "mac$n", macKey = "p".repeat(44), owner = "mac$n",
        members = listOf(Member(id = "x", name = "Pixel", source = "android", seen = 1)),
    )

    @Test
    fun keptRoomsComeBackWithoutMembers() {
        val saved = SavedRooms(deviceId = "d1", identity = "i".repeat(44), name = "Pixel 9", rooms = listOf(room(1)))
        val back = savedRoomsOf(bytes = savedRoomsBytes(saved = saved)!!)
        assertNotNull(back)
        assertEquals("d1", back!!.deviceId)
        assertEquals("MacBook 1", back.title)
        assertEquals("tossling.example.com", back.host)
        assertTrue(back.rooms.single().members.isEmpty())
    }

    @Test
    fun tooManyRoomsAreTrimmedToFit() {
        val saved = SavedRooms(deviceId = "d1", identity = "i".repeat(44), rooms = (1..40).map(::room))
        val bytes = savedRoomsBytes(saved = saved)!!
        assertTrue(bytes.size <= 4_000)
        assertTrue(savedRoomsOf(bytes = bytes)!!.rooms.isNotEmpty())
    }

    @Test
    fun legacyPairingsAreNotKept() {
        val legacy = room(1).copy(room = "", inTopic = "in", outTopic = "out")
        assertNull(savedRoomsBytes(saved = SavedRooms(deviceId = "d1", identity = "i", rooms = listOf(legacy))))
    }
}
