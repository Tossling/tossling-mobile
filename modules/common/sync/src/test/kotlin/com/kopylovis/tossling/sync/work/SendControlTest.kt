package com.kopylovis.tossling.sync.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SendControlTest {

    private val control = SendControl()
    private val work = UUID.randomUUID()

    @Test
    fun withoutARequestTheUploadGoesOn() {
        control.start(id = work)
        control.check()
    }

    @Test
    fun pauseStopsTheCurrentUpload() {
        control.start(id = work)
        assertTrue(control.request(id = work, value = SendControl.Request.PAUSE))
        assertEquals(true, stopped()?.isPaused)
    }

    @Test
    fun cancelStopsWithoutKeepingTheFile() {
        control.start(id = work)
        control.request(id = work, value = SendControl.Request.CANCEL)
        assertEquals(false, stopped()?.isPaused)
    }

    @Test
    fun aRequestForAnotherUploadIsIgnored() {
        control.start(id = work)
        assertFalse(control.request(id = UUID.randomUUID(), value = SendControl.Request.PAUSE))
        assertFalse(control.request(id = null, value = SendControl.Request.PAUSE))
        control.check()
    }

    @Test
    fun theNextUploadStartsClean() {
        control.start(id = work)
        control.request(id = work, value = SendControl.Request.PAUSE)
        control.finish(id = work)
        assertFalse(control.request(id = work, value = SendControl.Request.CANCEL))
        val next = UUID.randomUUID()
        control.start(id = next)
        control.check()
    }

    private fun stopped(): SendStopped? =
        try {
            control.check()
            null
        } catch (error: SendStopped) {
            error
        }
}
