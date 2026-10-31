package com.personalrecords.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

const val TARGET_PACKAGE = "com.personalrecords.app"

// Labels in English and Turkish, so the journeys work in either app language.
private val OVERVIEW_TAB = Pattern.compile("Overview|Özet|Mozaik")
private val TIMELINE_TAB = Pattern.compile("Timeline|Zaman")
private val RECORDS_TAB = Pattern.compile("Records|Kayıtlar")
private val MUSIC_ENTRY = Pattern.compile("Music & Audio|Müzik & Ses")
private val NOTIFICATIONS_ENTRY = Pattern.compile("Notification Log|Bildirim Günlüğü")

/** Waits until the first screen shows its navigation bar. */
fun MacrobenchmarkScope.waitForHome() {
    device.wait(Until.hasObject(By.desc(TIMELINE_TAB)), 10_000)
    device.waitForIdle()
}

private fun MacrobenchmarkScope.tapDesc(pattern: Pattern) {
    device.wait(Until.findObject(By.desc(pattern)), 5_000)?.click()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.tapText(pattern: Pattern) {
    device.wait(Until.findObject(By.text(pattern)), 5_000)?.click()
    device.waitForIdle()
}

fun MacrobenchmarkScope.openOverview() = tapDesc(OVERVIEW_TAB)
fun MacrobenchmarkScope.openTimeline() = tapDesc(TIMELINE_TAB)

fun MacrobenchmarkScope.openMusic() {
    tapDesc(RECORDS_TAB)
    tapText(MUSIC_ENTRY)
    Thread.sleep(1_500) // let the list load from the local cache and network
}

fun MacrobenchmarkScope.openNotifications() {
    tapDesc(RECORDS_TAB)
    tapText(NOTIFICATIONS_ENTRY)
    Thread.sleep(1_500)
}

fun MacrobenchmarkScope.back() {
    device.pressBack()
    device.waitForIdle()
}

/**
 * Flings the list in the middle of the screen [times] times down, then back up.
 * Swipes avoid the edges so they never trigger system gestures.
 */
fun MacrobenchmarkScope.flingList(times: Int = 5) {
    val x = device.displayWidth / 2
    val top = (device.displayHeight * 0.30).toInt()
    val bottom = (device.displayHeight * 0.75).toInt()
    repeat(times) {
        device.swipe(x, bottom, x, top, 6)
        Thread.sleep(250)
    }
    repeat(times) {
        device.swipe(x, top, x, bottom, 6)
        Thread.sleep(250)
    }
    device.waitForIdle()
}
