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

    /** Accepted legal bundle version at last confirmation. */
    var lastLegalAcceptedVersion: Int
        get() = prefs.getInt(KEY_LEGAL_VER, 0)
        set(value) = prefs.edit().putInt(KEY_LEGAL_VER, value).apply()

    /** Epoch ms when the user last checked all boxes and accepted. 0 = never. */
    var lastLegalConfirmedAtMs: Long
        get() = prefs.getLong(KEY_LEGAL_CONFIRMED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LEGAL_CONFIRMED_AT, value).apply()

    /**
     * Epoch ms when the bundled legal texts last changed (mirrors [LEGAL_DOCS_CHANGED_AT_MS]).
     * Written on accept for audit; re-prompt uses the code constant so a new APK always re-asks.
     */
    var lastLegalDocsChangedAtMs: Long
        get() = prefs.getLong(KEY_LEGAL_DOCS_CHANGED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LEGAL_DOCS_CHANGED_AT, value).apply()

    /** All three documents accepted for the current docs revision. */
    val legalAccepted: Boolean
        get() = lastLegalConfirmedAtMs > 0L &&
            lastLegalAcceptedVersion >= LEGAL_VERSION &&
            lastLegalConfirmedAtMs >= LEGAL_DOCS_CHANGED_AT_MS

    fun acceptLegalNow() {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putInt(KEY_LEGAL_VER, LEGAL_VERSION)
            .putLong(KEY_LEGAL_CONFIRMED_AT, now)
            .putLong(KEY_LEGAL_DOCS_CHANGED_AT, LEGAL_DOCS_CHANGED_AT_MS)
            .apply()
    }

    companion object {
        /** Bump when the meaning of the legal bundle changes (forces re-accept). */
        const val LEGAL_VERSION = 2

        /**
         * Bump (set to current time) whenever eula/terms/privacy text files change.
         * Accept is valid only if confirmedAt >= this value.
         */
        const val LEGAL_DOCS_CHANGED_AT_MS: Long = 1_769_000_000_000L // 2026-01-21; update with docs edits

        private const val KEY_REFRESH_MIN = "refresh_interval_min"
        private const val KEY_N_ADD = "n_add"
        private const val KEY_N_CHANGE = "n_change"
        private const val KEY_N_REMOVE = "n_remove"
        private const val KEY_N_DAY_OF = "n_day_of"
        private const val KEY_DAY_OF_HOUR = "day_of_hour"
        private const val KEY_N_DAY_BEFORE = "n_day_before"
        private const val KEY_DAY_BEFORE_HOUR = "day_before_hour"
        private const val KEY_LEGAL_VER = "legal_ver"
        private const val KEY_LEGAL_CONFIRMED_AT = "legal_confirmed_at"
        private const val KEY_LEGAL_DOCS_CHANGED_AT = "legal_docs_changed_at"
    }
}
