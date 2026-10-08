package com.kopylovis.tossling.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    @Test
    fun matchesSharedVectors() {
        val vectors = Json.parseToJsonElement(javaClass.classLoader!!.getResource("vectors.json")!!.readText()).jsonObject.getValue("device").jsonArray
        vectors.forEach { element ->
            val v = element.jsonObject.mapValues { it.value.jsonPrimitive.content }
            assertEquals(v.getValue("device_id"), deviceIdFrom(androidId = v.getValue("android_id")))
        }
    }
}
