package com.kopylovis.tossling.sync.network

import com.kopylovis.tossling.sync.data.SyncJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TossyProjectTest {

    @Test
    fun readsTheCreatedProjectWithItsToken() {
        val text = """{"topic":"home-nas","name":"Home NAS","publisher":"home-nas","token":"tk_abc","example":"curl -H \"Authorization: Bearer tk_abc\" https://tossy.example.com/home-nas","extra":1}"""
        val project = SyncJson.decodeFromString(TossyProject.serializer(), text)
        assertEquals("home-nas", project.topic)
        assertEquals("tk_abc", project.token)
        assertEquals("home-nas", project.publisher)
    }

    @Test
    fun aSharedPublisherBringsNoToken() {
        val project = SyncJson.decodeFromString(TossyProject.serializer(), """{"topic":"axrock","name":"Axrock","publisher":"backend"}""")
        assertNull(project.token)
        assertNull(project.example)
    }

    @Test
    fun recognisesTheServer() {
        assertEquals("tossy-server", SyncJson.decodeFromString(TossyHealth.serializer(), """{"server":"tossy-server","version":"0.1.0"}""").server)
    }

    @Test
    fun readsWhetherTheServerCanPush() {
        assertEquals(false, SyncJson.decodeFromString(TossyHealth.serializer(), """{"server":"tossy-server","version":"0.1.0","push":false}""").push)
        assertEquals(true, SyncJson.decodeFromString(TossyHealth.serializer(), """{"server":"tossy-server","push":true}""").push)
        assertNull(SyncJson.decodeFromString(TossyHealth.serializer(), """{"server":"tossy-server"}""").push)
    }

    @Test
    fun sendsOnlyTopicAndName() {
        assertEquals("""{"topic":"home-nas","name":"Home NAS"}""", SyncJson.encodeToString(TossyProjectRequest.serializer(), TossyProjectRequest(topic = "home-nas", name = "Home NAS")))
    }
}
