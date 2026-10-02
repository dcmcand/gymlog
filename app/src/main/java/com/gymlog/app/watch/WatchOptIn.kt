package com.gymlog.app.watch

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * The Settings switch for the Pebble watch link. Sending workout data to the Pebble app counts as
 * sharing with another app (Play Data safety), so it is off until the user turns it on.
 */
object WatchOptIn {
    private const val PREFS = "gymlog_settings"
    private const val KEY = "pebble_enabled"

    fun isEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY, false)

    fun setEnabled(prefs: SharedPreferences, enabled: Boolean) = prefs.edit { putBoolean(KEY, enabled) }

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = isEnabled(prefs(context))
}
