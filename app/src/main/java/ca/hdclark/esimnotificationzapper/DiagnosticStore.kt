package ca.hdclark.esimnotificationzapper

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only bounded log. Never records generic on-screen text or keystrokes.
 * Only diagnostic metadata and the configured matching warning phrase are logged.
 */
object DiagnosticStore {
    private const val KEY = "log"
    private const val LIMIT = 120
    private val lock = Any()

    fun append(context: Context, category: String, details: String) {
        if (!AppPrefs(context).diagnosticsEnabled) return
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val safe = details.replace('\n', ' ').replace('\r', ' ').take(220)
        synchronized(lock) {
            val storage = context.getSharedPreferences("zapper_diagnostics", Context.MODE_PRIVATE)
            val previous = storage.getString(KEY, "").orEmpty()
            val line = "$time | $category | $safe"
            val all = (previous.lineSequence().filter { it.isNotBlank() }.toList() + line)
                .takeLast(LIMIT)
            storage.edit().putString(KEY, all.joinToString("\n")).apply()
        }
    }

    fun read(context: Context): String =
        context.getSharedPreferences("zapper_diagnostics", Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty()

    fun clear(context: Context) {
        synchronized(lock) {
            context.getSharedPreferences("zapper_diagnostics", Context.MODE_PRIVATE)
                .edit().remove(KEY).apply()
        }
    }
}
