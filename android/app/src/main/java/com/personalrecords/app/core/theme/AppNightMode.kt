package com.personalrecords.app.core.theme

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build

/**
 * Keeps the system's view of the app's light/dark mode in line with the in-app theme choice
 * ("system", "light" or "dark").
 *
 * Without this, the system draws the launch screen and window background from the device theme,
 * so choosing Dark in the app while the phone is in light mode flashes a white screen on every
 * cold start before Compose draws the first dark frame.
 */
object AppNightMode {
    private const val PREFS = "app_night_mode"
    private const val KEY_APPLIED = "applied_theme_mode"

    /**
     * Tells the system which night mode this app uses (Android 12+). The system persists it,
     * so the next launch screen, the window background and `values-night` resources all follow
     * the in-app choice. The call is skipped when the mode was already applied, because a change
     * recreates the running activities.
     */
    fun apply(context: Context, themeMode: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_APPLIED, null) == themeMode) return
        val uiModeManager = context.getSystemService(UiModeManager::class.java) ?: return
        uiModeManager.setApplicationNightMode(
            when (themeMode) {
                "dark" -> UiModeManager.MODE_NIGHT_YES
                "light" -> UiModeManager.MODE_NIGHT_NO
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
        )
        prefs.edit().putString(KEY_APPLIED, themeMode).apply()
    }

    /** Whether the app should render dark for [themeMode], given the current configuration. */
    fun isDark(context: Context, themeMode: String): Boolean = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Paints the window in the theme's background color before Compose draws, so older Android
     * versions (which cannot be told the app's night mode) do not show the device theme's color.
     */
    fun paintWindowBackground(activity: Activity, dark: Boolean) {
        activity.window.setBackgroundDrawable(ColorDrawable(if (dark) WindowBackgroundDark else WindowBackgroundLight))
    }

    // Same values as android:windowBackground in values/themes.xml and values-night/themes.xml.
    private const val WindowBackgroundLight = 0xFFFEF7FF.toInt()
    private const val WindowBackgroundDark = 0xFF141218.toInt()
}
