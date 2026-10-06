package com.kopylovis.tossling.sync.live

import com.kopylovis.tossling.sync.data.Endpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTargetsTest {

    @Test
    fun groupsTopicsByServerWithoutRepeats() {
        val home = Endpoint(server = "https://tossy.example.com", token = "tk_a")
        val other = Endpoint(server = "https://ntfy.example.org", token = "tk_b")
        val targets = liveTargets(
            topics = listOf(
                home to "tossy-room",
                home to "router",
                other to "backups",
                home to "axrock",
                home to "router",
                home to "",
            ),
        )
        assertEquals(mapOf(home to listOf("axrock", "router", "tossy-room"), other to listOf("backups")), targets)
    }

    @Test
    fun sameServerWithAnotherTokenIsAnotherConnection() {
        val old = Endpoint(server = "https://tossy.example.com", token = "tk_old")
        val new = Endpoint(server = "https://tossy.example.com", token = "tk_new")
        assertEquals(2, liveTargets(topics = listOf(old to "a", new to "b")).size)
    }

    @Test
    fun nothingToFollowWhenThereAreNoTopics() {
        assertEquals(emptyMap<Endpoint, List<String>>(), liveTargets(topics = emptyList()))
    }
}
