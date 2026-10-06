package com.kopylovis.tossling.sync.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdTest {

    @Test
    fun sameAndroidIdGivesTheSameDeviceId() {
        assertEquals(deviceIdFrom(androidId = "9774d56d682e549c"), deviceIdFrom(androidId = "9774d56d682e549c"))
        assertEquals(16, deviceIdFrom(androidId = "9774d56d682e549c").length)
        assertTrue(deviceIdFrom(androidId = "9774d56d682e549c").all { it in "0123456789abcdef" })
    }

    @Test
    fun theAndroidIdItselfIsNotSent() {
        assertNotEquals("9774d56d682e549c", deviceIdFrom(androidId = "9774d56d682e549c"))
        assertNotEquals(deviceIdFrom(androidId = "a"), deviceIdFrom(androidId = "b"))
    }
}
