package com.personalrecords.app.ui

import com.personalrecords.app.core.network.model.ActivityItemDto
import com.personalrecords.app.ui.components.notificationAppName
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationAppNameTest {
    @Test
    fun usesStoredAppName() {
        val item = ActivityItemDto(
            source = "notification",
            title = "Mesajlar: VODAFONE",
            details = mapOf("app_name" to JsonPrimitive("Messages"))
        )
        assertEquals("Messages", notificationAppName(item))
    }

    @Test
    fun fallsBackToTitlePrefix() {
        val item = ActivityItemDto(source = "notification", title = "WhatsApp: Ayşe")
        assertEquals("WhatsApp", notificationAppName(item))
    }
}
