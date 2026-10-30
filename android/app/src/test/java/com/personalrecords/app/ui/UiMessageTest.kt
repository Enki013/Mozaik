package com.personalrecords.app.ui

import com.personalrecords.app.R
import com.personalrecords.app.ui.common.UiMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiMessageTest {
    @Test
    fun serverOr_usesServerTextWhenPresent() {
        val message = UiMessage.serverOr("Imported 3 books", R.string.settings_msg_synced, "Goodreads")
        assertEquals("Imported 3 books", message.raw)
        assertNull(message.resId)
        assertFalse(message.isError)
    }

    @Test
    fun serverOr_fallsBackToResourceWhenServerTextIsBlank() {
        val message = UiMessage.serverOr("  ", R.string.settings_msg_synced, "Goodreads")
        assertNull(message.raw)
        assertEquals(R.string.settings_msg_synced, message.resId)
        assertEquals(listOf<Any>("Goodreads"), message.args)
    }

    @Test
    fun error_isFlaggedSoEditorsStayOpen() {
        assertTrue(UiMessage.error(R.string.settings_msg_error, "HTTP 404").isError)
        assertFalse(UiMessage.of(R.string.settings_msg_keys_saved).isError)
    }
}
