package com.kopylovis.tossling.protocol

import com.kopylovis.tossling.protocol.crypto.ClipCipher
import com.kopylovis.tossling.protocol.network.NtfyAttachment
import com.kopylovis.tossling.protocol.network.NtfyEvent
import com.kopylovis.tossling.protocol.room.Incoming
import com.kopylovis.tossling.protocol.room.Outgoing
import com.kopylovis.tossling.protocol.room.PairingCode
import com.kopylovis.tossling.protocol.room.RoomConfig
import com.kopylovis.tossling.protocol.room.RoomCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomCoreTest {

    private val now = 1_800_000_000_000L
    private val key = ClipCipher.newKey()
    private val room = "tossling-0123456789abcdef01234567"

    private fun core(id: String, name: String, owner: String = "mac", source: String = "ios") = RoomCore(
        RoomConfig(server = "https://s", token = "t", room = room, key = key, deviceId = id, deviceName = name, owner = owner, identity = RoomCore.createIdentity(), source = source),
    )

    private fun Outgoing.event(id: String = "e1", ageSeconds: Long = 0, url: String? = null) =
        NtfyEvent(id = id, time = now / 1000 - ageSeconds, event = "message", topic = room, message = message, attachment = url?.let { NtfyAttachment(url = it) })

    @Test
    fun aHelloAddsTheDevice() {
        val mac = core(id = "mac", name = "Studio", source = "mac")
        val phone = core(id = "phone", name = "iPhone")
        val joined = assertIs<Incoming.Joined>(mac.open(phone.hello(renewed = true).event(), nowMs = now))
        assertTrue(joined.isNew)
        assertEquals("iPhone", mac.members.getValue("phone").name)
        assertEquals("ios", mac.members.getValue("phone").source)
        assertTrue(mac.members.getValue("phone").pk.isNotEmpty())
    }

    @Test
    fun shortTextGoesInlineAndLongTextAsAnAttachment() {
        val mac = core(id = "mac", name = "Studio", source = "mac")
        val phone = core(id = "phone", name = "iPhone")
        val short = phone.text("hello")
        assertNull(short.body)
        assertEquals("hello", assertIs<Incoming.Content>(mac.open(short.event(), nowMs = now)).meta.text)
        val long = phone.text("x".repeat(RoomCore.INLINE_LIMIT + 1))
        val body = assertNotNull(long.body)
        val content = assertIs<Incoming.Content>(mac.open(long.event(url = "https://s/file/a"), nowMs = now))
        assertNull(content.meta.text)
        assertEquals("https://s/file/a", content.attachmentUrl)
        assertEquals(RoomCore.INLINE_LIMIT + 1, mac.openAttachment(body).size)
    }

    @Test
    fun skipsItsOwnMessagesOthersAddressesAndStaleContent() {
        val phone = core(id = "phone", name = "iPhone")
        val mac = core(id = "mac", name = "Studio", source = "mac")
        assertIs<Incoming.Skipped>(phone.open(phone.text("me").event(), nowMs = now))
        assertIs<Incoming.Skipped>(phone.open(mac.text("not you", to = listOf("pixel")).event(), nowMs = now))
        assertIs<Incoming.Skipped>(phone.open(mac.text("old").event(ageSeconds = 16 * 60), nowMs = now))
        assertIs<Incoming.Content>(phone.open(mac.file(name = "a.pdf", size = 3, mime = "application/pdf").event(ageSeconds = 16 * 60, url = "u"), nowMs = now))
        assertIs<Incoming.FileGone>(phone.open(mac.file(name = "a.pdf", size = 3, mime = "application/pdf").event(ageSeconds = 4 * 3600, url = "u"), nowMs = now))
    }

    @Test
    fun answersAPingWithAHelloToThePinger() {
        val phone = core(id = "phone", name = "iPhone")
        val mac = core(id = "mac", name = "Studio", source = "mac")
        val pinged = assertIs<Incoming.Pinged>(phone.open(mac.ping().event(), nowMs = now))
        val reply = assertNotNull(pinged.reply)
        val hello = assertIs<Incoming.Joined>(mac.open(reply.event(id = "e2"), nowMs = now))
        assertEquals("phone", hello.memberId)
    }

    @Test
    fun byeForgetsTheDevice() {
        val phone = core(id = "phone", name = "iPhone")
        val mac = core(id = "mac", name = "Studio", source = "mac")
        phone.open(mac.hello().event(), nowMs = now)
        assertIs<Incoming.Left>(phone.open(mac.bye().event(id = "e2"), nowMs = now))
        assertNull(phone.members["mac"])
    }

    @Test
    fun removingADeviceMovesTheOthersToANewRoom() {
        val mac = core(id = "mac", name = "Studio", source = "mac")
        val phone = core(id = "phone", name = "iPhone")
        val pixel = core(id = "pixel", name = "Pixel", source = "android")
        mac.open(phone.hello(renewed = true).event(id = "h1"), nowMs = now)
        mac.open(pixel.hello(renewed = true).event(id = "h2"), nowMs = now)
        val newKey = ClipCipher.newKey()
        val messages = mac.revoke(id = "pixel", room = "tossling-new", key = newKey, token = "t2")
        assertEquals(2, messages.size)
        assertIs<Incoming.Skipped>(phone.open(messages[0].event(id = "k"), nowMs = now))
        val kicked = assertIs<Incoming.Kicked>(pixel.open(messages[0].event(id = "k"), nowMs = now))
        assertEquals(false, kicked.ignored)
        val rekeyed = assertIs<Incoming.Rekeyed>(phone.open(messages[1].event(id = "r"), nowMs = now))
        assertEquals("tossling-new", rekeyed.config.room)
        assertEquals(newKey, rekeyed.config.key)
        assertEquals("t2", rekeyed.config.token)
        assertIs<Incoming.Skipped>(pixel.open(messages[1].event(id = "r"), nowMs = now))
    }

    @Test
    fun theOwnerIgnoresAKick() {
        val mac = core(id = "mac", name = "Studio", owner = "mac", source = "mac")
        val phone = core(id = "phone", name = "iPhone")
        val kick = RoomCore(phone.config.copy(owner = "someone")).revoke(id = "mac", room = "r", key = ClipCipher.newKey(), token = null).first()
        assertTrue(assertIs<Incoming.Kicked>(mac.open(kick.event(), nowMs = now)).ignored)
    }

    @Test
    fun readsTheQrCodeOfAComputer() {
        val pk = "hSDwCYkwp1R0i33ctD73Wg2/Og0mOBr066SpjqqbTmo="
        val raw = """{"id":"a1b2c3d4e5f60718","k":"$key","n":"Studio","o":"a1b2c3d4e5f60718","pk":"$pk","r":"$room","s":"https://Tossling.example.com/","t":"tk","v":2}"""
        val paired = assertNotNull(PairingCode.read(raw = raw, deviceId = "phone", deviceName = "iPhone", identity = RoomCore.createIdentity(), nowMs = now))
        assertEquals("https://tossling.example.com", paired.config.server)
        assertEquals(room, paired.config.room)
        assertEquals("a1b2c3d4e5f60718", paired.config.owner)
        assertEquals(pk, paired.members.getValue("a1b2c3d4e5f60718").pk)
        assertNull(PairingCode.read(raw = "nonsense", deviceId = "p", deviceName = "n", identity = "", nowMs = now))
        assertNull(PairingCode.read(raw = raw.replace("\"v\":2", "\"v\":1").replace("\"r\":\"$room\",", ""), deviceId = "p", deviceName = "n", identity = "", nowMs = now))
    }

    @Test
    fun membersSurviveASaveAndLoad() {
        val mac = core(id = "mac", name = "Studio", source = "mac")
        mac.open(core(id = "phone", name = "iPhone").hello().event(), nowMs = now)
        assertEquals(mac.members, RoomCore.decodeMembers(mac.encodeMembers()))
        assertNotEquals(RoomCore.createDeviceId(), RoomCore.createDeviceId())
    }
}
