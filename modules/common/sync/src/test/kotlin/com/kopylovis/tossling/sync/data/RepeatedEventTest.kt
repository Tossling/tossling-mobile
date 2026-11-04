package com.kopylovis.tossling.sync.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatedEventTest {

    @Test
    fun aRepeatedEventIsSkippedAndOnlyTheLatestAreKept() {
        val first = afterHandling(handled = emptyList(), id = "a", keep = 2)!!
        val second = afterHandling(handled = first, id = "b", keep = 2)!!
        assertNull(afterHandling(handled = second, id = "a", keep = 2))
        assertEquals(listOf("b", "c"), afterHandling(handled = second, id = "c", keep = 2))
    }
}
