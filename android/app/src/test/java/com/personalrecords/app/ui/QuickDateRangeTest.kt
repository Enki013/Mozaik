package com.personalrecords.app.ui

import com.personalrecords.app.ui.components.QuickDateRanges
import com.personalrecords.app.ui.components.applyQuickRange
import com.personalrecords.app.ui.components.quickRangeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class QuickDateRangeTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test
    fun quickRanges_areTodayAndUpTo30Days() {
        assertEquals(listOf(0, 7, 14, 30), QuickDateRanges)
    }

    @Test
    fun applyQuickRange_today_setsSingleDate() {
        var date: String? = null
        applyQuickRange(0, setDate = { date = it }, setRange = { _, _ -> error("not a range") }, today = today)
        assertEquals("2026-10-30", date)
    }

    @Test
    fun applyQuickRange_sevenDays_includesToday() {
        var from: String? = null
        var to: String? = null
        applyQuickRange(7, setDate = { error("not a date") }, setRange = { f, t -> from = f; to = t }, today = today)
        assertEquals("2026-10-24", from)
        assertEquals("2026-10-30", to)
    }

    @Test
    fun quickRangeOf_roundTripsEveryRange() {
        for (range in QuickDateRanges) {
            var date: String? = null
            var from: String? = null
            var to: String? = null
            applyQuickRange(range, setDate = { date = it }, setRange = { f, t -> from = f; to = t }, today = today)
            assertEquals(range, quickRangeOf(date, from, to, today))
        }
    }

    @Test
    fun quickRangeOf_customRangeIsNotAQuickRange() {
        assertNull(quickRangeOf(null, "2026-10-05", "2026-10-30", today))
        assertNull(quickRangeOf("2026-10-29", null, null, today))
        assertNull(quickRangeOf(null, "2026-10-24", "2026-10-29", today))
    }

    @Test
    fun quickRangeOf_ninetyDaysIsNoLongerAQuickRange() {
        assertNull(quickRangeOf(null, today.minusDays(89).toString(), today.toString(), today))
    }
}
