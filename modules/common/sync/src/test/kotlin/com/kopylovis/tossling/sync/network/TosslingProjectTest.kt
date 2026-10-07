package com.kopylovis.tossling.sync.network

import com.kopylovis.tossling.sync.data.SyncJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TosslingProjectTest {

    @Test
    fun readsTheCreatedProjectWithItsToken() {
        val text = """{"topic":"home-nas","name":"Home NAS","publisher":"home-nas","token":"tk_abc","example":"curl -H \"Authorization: Bearer tk_abc\" https://tossling.example.com/home-nas","extra":1}"""
        val project = SyncJson.decodeFromString(TosslingProject.serializer(), text)
        assertEquals("home-nas", project.topic)
        assertEquals("tk_abc", project.token)
        assertEquals("home-nas", project.publisher)
    }

    @Test
    fun aSharedPublisherBringsNoToken() {
        val project = SyncJson.decodeFromString(TosslingProject.serializer(), """{"topic":"axrock","name":"Axrock","publisher":"backend"}""")
        assertNull(project.token)
        assertNull(project.example)
    }

    @Test
    fun recognisesTheServerUnderBothNames() {
        val current = SyncJson.decodeFromString(TosslingHealth.serializer(), """{"server":"tossling-server","version":"0.3.0"}""")
        val older = SyncJson.decodeFromString(TosslingHealth.serializer(), """{"server":"tossy-server","version":"0.1.0"}""")
        val other = SyncJson.decodeFromString(TosslingHealth.serializer(), """{"server":"ntfy"}""")
        assertTrue(current.isOurs && current.isTossling)
        assertTrue(older.isOurs)
        assertFalse(older.isTossling)
        assertFalse(other.isOurs)
    }

    @Test
    fun readsWhetherTheServerCanPush() {
        assertEquals(false, SyncJson.decodeFromString(TosslingHealth.serializer(), """{"server":"tossy-server","version":"0.1.0","push":false}""").push)
        assertEquals(true, SyncJson.decodeFromString(TosslingHealth.serializer(), """{"server":"tossy-server","push":true}""").push)
        assertNull(SyncJson.decodeFromString(TosslingHealth.serializer(), """{"server":"tossy-server"}""").push)
    }

    @Test
    fun sendsOnlyTopicAndName() {
        assertEquals("""{"topic":"home-nas","name":"Home NAS"}""", SyncJson.encodeToString(TosslingProjectRequest.serializer(), TosslingProjectRequest(topic = "home-nas", name = "Home NAS")))
    }
}
