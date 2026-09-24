package com.personalrecords.app.core.common

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {
    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val friendlyFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("tr"))

    fun todayISO(): String = LocalDate.now().format(isoFormatter)

    fun formatFriendly(isoDate: String): String {
        return try {
            val date = LocalDate.parse(isoDate, isoFormatter)
            val today = LocalDate.now()
            when (date) {
                today -> "Bugün"
                today.minusDays(1) -> "Dün"
                else -> date.format(friendlyFormatter)
            }
        } catch (e: Exception) {
            isoDate
        }
    }
}
