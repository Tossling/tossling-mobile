package com.kopylovis.tossling.protocol

import com.kopylovis.tossling.protocol.network.NtfyTokenRequest
import com.kopylovis.tossling.protocol.network.RefusalGate
import com.kopylovis.tossling.protocol.network.ServerRefusal
import com.kopylovis.tossling.protocol.network.encodeTokenRequest
import com.kopylovis.tossling.protocol.network.refusalPauseMs
import com.kopylovis.tossling.protocol.network.serverRefusal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RefusalGateTest {

    private var time = 1_000_000L
    private val gate = RefusalGate(now = { time })
    private val room = Endpoint(server = "https://tossling.example", token = "tk_old")

    @Test
    fun tokenRequestsAskForATokenThatNeverExpires() {
        assertEquals("""{"label":"tossling","expires":0}""", encodeTokenRequest(label = "tossling"))
        assertEquals(0L, NtfyTokenRequest.NEVER_EXPIRES)
    }

    @Test
    fun refusalsByStatus() {
        assertEquals(ServerRefusal.TOKEN, serverRefusal(code = 401))
        assertEquals(ServerRefusal.TOKEN, serverRefusal(code = 403, isAccount = true))
        assertNull(serverRefusal(code = 403))
        assertEquals(ServerRefusal.BUSY, serverRefusal(code = 429))
        assertNull(serverRefusal(code = 500))
    }

    @Test
    fun pausesDoubleFromAMinuteUpToHalfAnHour() {
        assertEquals(listOf(1L, 2L, 4L, 8L, 16L, 30L, 30L), (1..7).map { refusalPauseMs(failures = it) / 60_000L })
    }

    @Test
    fun aRefusedTokenWaitsAndTheWaitGrowsOnlyWhenTheServerIsAskedAgain() {
        gate.refused(endpoint = room, kind = ServerRefusal.TOKEN)
        gate.refused(endpoint = room, kind = ServerRefusal.TOKEN)
        assertEquals(60_000L, gate.pauseLeft(endpoint = room))
        time += 60_000L
        assertNull(gate.held(endpoint = room))
        assertNotNull(gate.refusal(endpoint = room))
        gate.refused(endpoint = room, kind = ServerRefusal.TOKEN)
        assertEquals(2, gate.held(endpoint = room)?.failures)
        assertEquals(120_000L, gate.pauseLeft(endpoint = room))
    }

    @Test
    fun anotherTokenOnTheSameServerIsNotHeldBack() {
        gate.refused(endpoint = room, kind = ServerRefusal.TOKEN)
        assertNull(gate.held(endpoint = room.copy(token = "tk_new")))
    }

    @Test
    fun tooManyRequestsHoldsBackTheWholeServer() {
        gate.refused(endpoint = room, kind = ServerRefusal.BUSY)
        assertEquals(ServerRefusal.BUSY, gate.held(endpoint = room.copy(token = "tk_new"))?.kind)
        assertNull(gate.held(endpoint = Endpoint(server = "https://other.example", token = "tk_old")))
    }

    @Test
    fun anAnswerClearsTheRefusal() {
        gate.refused(endpoint = room, kind = ServerRefusal.TOKEN)
        gate.refused(endpoint = room.copy(token = "tk_new"), kind = ServerRefusal.BUSY)
        gate.passed(endpoint = room)
        assertNull(gate.refusal(endpoint = room))
        assertEquals(emptyMap(), gate.tokens.value)
        assertEquals(emptyMap(), gate.servers.value)
    }
}
