package com.ciphwren.android.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * The theme chosen in the app settings: auto (the system's dark theme setting), light or dark.
 * The values are the entry values of the theme list in the app settings.
 */
object ThemeUtils {
    const val PREF_THEME = "theme"
    const val THEME_AUTO = "auto"
    private const val THEME_LIGHT = "light"
    private const val THEME_DARK = "dark"

    /** Applies the theme stored in the settings. Called once when the app starts. */
    fun applyTheme(context: Context) =
            applyTheme(context.defaultSharedPreferences.getString(PREF_THEME, THEME_AUTO)!!)

    /** Applies [theme] to the whole app. AppCompat recreates the activities that are open. */
    fun applyTheme(theme: String) {
        AppCompatDelegate.setDefaultNightMode(when (theme) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            // Android 9 and older have no dark theme setting: there it's the light theme
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
    }
}
