package com.personalrecords.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Records the code used on startup and on the main lists into a Baseline Profile. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()
        waitForHome()
        flingList(2)
        openTimeline()
        flingList(3)
        openMusic()
        flingList(3)
        back()
        openNotifications()
        flingList(3)
        back()
        openOverview()
    }
}
