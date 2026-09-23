package eu.mymcd.shifts.store

import android.content.Context
import android.content.SharedPreferences

/** Non-sensitive app settings (plain SharedPreferences). */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mymcd_settings", Context.MODE_PRIVATE)

    var refreshIntervalMin: Int
        get() = prefs.getInt(KEY_REFRESH_MIN, 30).coerceIn(5, 24 * 60)
        set(value) = prefs.edit().putInt(KEY_REFRESH_MIN, value.coerceIn(5, 24 * 60)).apply()

    var notifyOnAdd: Boolean
        get() = prefs.getBoolean(KEY_N_ADD, true)
        set(value) = prefs.edit().putBoolean(KEY_N_ADD, value).apply()

    var notifyOnChange: Boolean
        get() = prefs.getBoolean(KEY_N_CHANGE, true)
        set(value) = prefs.edit().putBoolean(KEY_N_CHANGE, value).apply()

    /** Only notifies when a *future* shift disappears from the plan (not when it simply ages out). */
    var notifyOnRemove: Boolean
        get() = prefs.getBoolean(KEY_N_REMOVE, true)
        set(value) = prefs.edit().putBoolean(KEY_N_REMOVE, value).apply()

    var notifyDayOf: Boolean
        get() = prefs.getBoolean(KEY_N_DAY_OF, true)
        set(value) = prefs.edit().putBoolean(KEY_N_DAY_OF, value).apply()

    /** Hour of shift day (0–23) to remind. */
    var dayOfHour: Int
        get() = prefs.getInt(KEY_DAY_OF_HOUR, 8).coerceIn(0, 23)
        set(value) = prefs.edit().putInt(KEY_DAY_OF_HOUR, value.coerceIn(0, 23)).apply()

    var notifyDayBefore: Boolean
        get() = prefs.getBoolean(KEY_N_DAY_BEFORE, true)
        set(value) = prefs.edit().putBoolean(KEY_N_DAY_BEFORE, value).apply()

    /** Hour on previous day (0–23) to remind; default 22 = 10pm. */
    var dayBeforeHour: Int
        get() = prefs.getInt(KEY_DAY_BEFORE_HOUR, 22).coerceIn(0, 23)
        set(value) = prefs.edit().putInt(KEY_DAY_BEFORE_HOUR, value.coerceIn(0, 23)).apply()

    var lastLegalAcceptedVersion: Int
        get() = prefs.getInt(KEY_LEGAL_VER, 0)
        set(value) = prefs.edit().putInt(KEY_LEGAL_VER, value).apply()

    companion object {
        const val LEGAL_VERSION = 1

        private const val KEY_REFRESH_MIN = "refresh_interval_min"
        private const val KEY_N_ADD = "n_add"
        private const val KEY_N_CHANGE = "n_change"
        private const val KEY_N_REMOVE = "n_remove"
        private const val KEY_N_DAY_OF = "n_day_of"
        private const val KEY_DAY_OF_HOUR = "day_of_hour"
        private const val KEY_N_DAY_BEFORE = "n_day_before"
        private const val KEY_DAY_BEFORE_HOUR = "day_before_hour"
        private const val KEY_LEGAL_VER = "legal_ver"
    }
}
