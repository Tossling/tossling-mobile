package com.kopylovis.tossling.sync.data

import com.kopylovis.tossling.protocol.Member
import org.junit.Assert.assertEquals
import org.junit.Test

class MemberMergeTest {

    private val known = Member(id = "mac", name = "Studio", source = "mac", seen = 1, since = 1, alias = "Work", pk = "first")

    private fun seen(pk: String) = Member(id = "mac", name = "Studio", source = "mac", seen = 2, pk = pk)

    @Test
    fun theFirstKeyStaysWithoutARenewedHello() {
        val merged = mergeMember(known = known, member = seen(pk = "other"), renewed = false, trusted = false, now = 10)
        assertEquals("first", merged.pk)
        assertEquals(0L, merged.keyChanged)
        assertEquals("Work", merged.alias)
    }

    @Test
    fun aRenewedHelloWithAnotherKeyIsTakenAndMarked() {
        val merged = mergeMember(known = known, member = seen(pk = "other"), renewed = true, trusted = false, now = 10)
        assertEquals("other", merged.pk)
        assertEquals(10L, merged.keyChanged)
        val again = mergeMember(known = merged, member = seen(pk = "other"), renewed = true, trusted = false, now = 20)
        assertEquals(10L, again.keyChanged)
    }

    @Test
    fun theSameKeyOrTheFirstKeyOrAScannedKeyIsNotMarked() {
        assertEquals(0L, mergeMember(known = known, member = seen(pk = "first"), renewed = true, trusted = false, now = 10).keyChanged)
        assertEquals(0L, mergeMember(known = known.copy(pk = ""), member = seen(pk = "new"), renewed = true, trusted = false, now = 10).keyChanged)
        assertEquals(0L, mergeMember(known = known.copy(keyChanged = 5), member = seen(pk = "qr"), renewed = true, trusted = true, now = 10).keyChanged)
    }
}
