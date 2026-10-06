package com.kopylovis.tossling.sync.alerts

import com.kopylovis.tossling.sync.data.Endpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class MovedProjectsTest {

    private val old = Endpoint(server = "https://ntfy.example.com", token = "tk_old")
    private val new = Endpoint(server = "https://tossy.example.com", token = "tk_new")
    private val other = Endpoint(server = "https://other.example.org", token = "tk_other")

    @Test
    fun projectsOfAServerNoLongerPairedMoveToTheNewOneWhenItHasThem() {
        val projects = listOf(
            Project(topic = "authmeister", name = "Authmeister", endpoint = old, isSynced = true),
            Project(topic = "gone", name = "Gone", endpoint = old, isSynced = true),
            Project(topic = "router", name = "Router", endpoint = new, isSynced = true),
            Project(topic = "backups", name = "Backups", endpoint = other, isSynced = true),
        )
        val moved = movedProjects(
            projects = projects,
            paired = setOf(new.server, other.server),
            remoteTopics = setOf("authmeister", "router", "backups"),
        )
        assertEquals(listOf("authmeister"), moved.map { it.topic })
    }

    @Test
    fun nothingMovesWhileTheOldServerIsStillPaired() {
        val projects = listOf(Project(topic = "authmeister", name = "Authmeister", endpoint = old, isSynced = true))
        assertEquals(emptyList<Project>(), movedProjects(projects = projects, paired = setOf(old.server, new.server), remoteTopics = setOf("authmeister")))
    }
}
