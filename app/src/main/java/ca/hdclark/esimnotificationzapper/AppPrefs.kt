package ca.hdclark.esimnotificationzapper

import android.content.Context
import android.content.SharedPreferences

class AppPrefs(context: Context) {
    private val store: SharedPreferences =
        context.getSharedPreferences("zapper_preferences", Context.MODE_PRIVATE)

    var armed: Boolean
        get() = store.getBoolean("armed", true)
        set(value) { store.edit().putBoolean("armed", value).apply() }

    var diagnosticsEnabled: Boolean
        get() = store.getBoolean("diagnostics_enabled", true)
        set(value) { store.edit().putBoolean("diagnostics_enabled", value).apply() }

    var allowOtherPackages: Boolean
        get() = store.getBoolean("allow_other_packages", false)
        set(value) { store.edit().putBoolean("allow_other_packages", value).apply() }

    var warningText: String
        get() = store.getString("warning_text", "Check SIM card tray") ?: "Check SIM card tray"
        set(value) { store.edit().putString("warning_text", value).apply() }

    var buttonText: String
        get() = store.getString("button_text", "OK") ?: "OK"
        set(value) { store.edit().putString("button_text", value).apply() }

    var dismissCount: Int
        get() = store.getInt("dismiss_count", 0)
        set(value) { store.edit().putInt("dismiss_count", value).apply() }

    var lastDismissedAt: Long
        get() = store.getLong("last_dismissed", 0)
        set(value) { store.edit().putLong("last_dismissed", value).apply() }
}
