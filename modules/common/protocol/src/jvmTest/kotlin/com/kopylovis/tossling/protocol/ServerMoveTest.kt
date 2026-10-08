package com.kopylovis.tossling.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerMoveTest {

    @Test
    fun normalizesSchemeHostAndPort() {
        assertEquals("https://tossling.example.com", normalizedServer("https://Tossling.Example.com/"))
        assertEquals("https://tossling.example.com", normalizedServer(" https://tossling.example.com:443 "))
        assertEquals("https://tossling.example.com:8443", normalizedServer("https://tossling.example.com:8443/"))
        assertEquals("http://10.0.2.2:8090", normalizedServer("http://10.0.2.2:8090"))
        assertEquals("http://10.0.2.2", normalizedServer("http://10.0.2.2:80/"))
        assertNull(normalizedServer("ftp://tossling.example.com"))
        assertNull(normalizedServer("tossling.example.com"))
        assertNull(normalizedServer(""))
    }

    @Test
    fun movesToADifferentHttpsAddress() {
        assertEquals("https://tossling.example.com", serverMoveTarget(current = "https://tossy.example.com", url = "https://tossling.example.com/"))
    }

    @Test
    fun staysWhenTheAddressIsTheSame() {
        assertNull(serverMoveTarget(current = "https://tossling.example.com", url = "https://TOSSLING.example.com:443/"))
    }

    @Test
    fun staysWithoutAnAddress() {
        assertNull(serverMoveTarget(current = "https://tossy.example.com", url = ""))
        assertNull(serverMoveTarget(current = "https://tossy.example.com", url = null))
        assertNull(serverMoveTarget(current = "https://tossy.example.com", url = "not a url"))
    }

    @Test
    fun neverMovesFromHttpsToHttp() {
        assertNull(serverMoveTarget(current = "https://tossy.example.com", url = "http://tossling.example.com"))
    }

    @Test
    fun allowsHttpWhenTheCurrentServerIsHttp() {
        assertEquals("http://10.0.2.2:8091", serverMoveTarget(current = "http://10.0.2.2:8090", url = "http://10.0.2.2:8091"))
        assertEquals("https://tossling.example.com", serverMoveTarget(current = "http://10.0.2.2:8090", url = "https://tossling.example.com"))
    }

    @Test
    fun switchesOnlyWhenTheNewAddressAnswers() {
        assertTrue(acceptsServerMove(isTossling = true, accountOk = true))
        assertFalse(acceptsServerMove(isTossling = true, accountOk = false))
        assertFalse(acceptsServerMove(isTossling = false, accountOk = true))
    }
}
