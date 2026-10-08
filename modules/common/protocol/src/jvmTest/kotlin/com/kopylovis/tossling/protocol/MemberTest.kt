package com.kopylovis.tossling.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class MemberTest {

    @Test
    fun desktopsAreComputersAndTheRestArePhones() {
        val computers = listOf("mac", "windows", "linux", "android", "ios", "").filter { Member(id = it, name = it, source = it, seen = 0).isComputer }
        assertEquals(listOf("mac", "windows", "linux"), computers)
    }
}
