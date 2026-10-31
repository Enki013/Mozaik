package com.personalrecords.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Cold start to the first frame, without and with ahead-of-time compiled profiles. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Like a fresh install before the Play Store or ART has compiled anything. */
    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    /** Uses the Baseline Profile shipped in the APK, when there is one. */
    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.UseIfAvailable))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
        waitForHome()
    }
}
