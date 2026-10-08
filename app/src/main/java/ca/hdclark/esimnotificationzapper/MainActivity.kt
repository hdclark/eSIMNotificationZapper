package ca.hdclark.esimnotificationzapper

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** No external UI framework, network permission, or locally installed development tools. */
class MainActivity : Activity() {
    private lateinit var prefs: AppPrefs
    private var viewingDiagnostics = false
    private var statusView: TextView? = null
    private var countView: TextView? = null
    private var logView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        if (savedInstanceState?.getBoolean("diagnostics") == true) {
            showDiagnostics()
        } else {
            showHome()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("diagnostics", viewingDiagnostics)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        refreshDiagnostics()
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value + 0.5f).toInt()

    private fun page(): LinearLayout {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(247, 249, 251))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }
        scroll.addView(content)
        setContentView(scroll)
        statusView = null
        countView = null
        logView = null
        return content
    }

    private fun text(
        parent: LinearLayout,
        value: String,
        size: Float = 15f,
        bold: Boolean = false
    ): TextView {
        val widget = TextView(this).apply {
            text = value
            setTextColor(Color.rgb(36, 44, 56))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }
        parent.addView(widget, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })
        return widget
    }

    private fun button(
        parent: LinearLayout,
        label: String,
        action: () -> Unit
    ): Button {
        val widget = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }
        parent.addView(widget, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })
        return widget
    }

    private fun checkbox(
        parent: LinearLayout,
        label: String,
        initial: Boolean,
        action: (Boolean) -> Unit
    ) {
        val widget = CheckBox(this).apply {
            text = label
            isChecked = initial
            setTextColor(Color.rgb(36, 44, 56))
            setOnCheckedChangeListener { _, checked -> action(checked) }
        }
        parent.addView(widget, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })
    }

    private fun field(parent: LinearLayout, label: String, value: String): EditText {
        text(parent, label, 14f, true)
        val edit = EditText(this).apply {
            setSingleLine(true)
            setText(value)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSelectAllOnFocus(true)
            contentDescription = label
        }
        parent.addView(edit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })
        return edit
    }

    private fun showHome() {
        viewingDiagnostics = false
        val root = page()
        text(root, "eSIM Notification Zapper", 25f, true)
        text(root,
            "Attempts to automatically click a confirmation button only when a configured SIM-tray warning appears. " +
                "The warning may flash before dismissal. This does not repair the SIM connector.")
        text(root, "1. Enable the Accessibility Service", 19f, true)
        statusView = text(root, "", 16f, true)
        text(root,
            "Android requires you to enable this service manually in Settings → Accessibility → Installed apps. " +
                "If a sideloaded APK is blocked, open Settings → Apps → eSIM Notification Zapper → " +
                "⋮ → Allow restricted settings, then return to Accessibility.")
        button(root, "Open Accessibility settings") {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Could not open Accessibility settings", Toast.LENGTH_LONG).show()
            }
        }
        text(root, "2. Configure detection", 19f, true)
        checkbox(root, "Automatically dismiss matching warning", prefs.armed) {
            prefs.armed = it
            updateStatus()
        }
        val phrase = field(root, "Warning text (specific phrase to find)", prefs.warningText)
        val confirmation = field(root, "Confirmation button label (exact text)", prefs.buttonText)
        button(root, "Save detection text") {
            val target = phrase.text.toString().trim()
            val label = confirmation.text.toString().trim()
            if (!WarningMatcher.validTarget(target) || label.isBlank()) {
                Toast.makeText(this,
                    "Use a specific multi-word warning (8+ characters) and a button label.",
                    Toast.LENGTH_LONG).show()
            } else {
                prefs.warningText = target
                prefs.buttonText = label
                Toast.makeText(this, "Detection text saved", Toast.LENGTH_SHORT).show()
            }
        }
        checkbox(root,
            "Advanced: permit matching warnings in non-system apps",
            prefs.allowOtherPackages
        ) {
            prefs.allowOtherPackages = it
            Toast.makeText(this,
                if (it) "Matching is now allowed in every app: use with caution."
                else "Matching limited to likely system packages.",
                Toast.LENGTH_LONG).show()
        }
        text(root,
            "By default the service only interacts with likely Android/Samsung system packages. " +
                "Enable the advanced option only if diagnostics suggest an unexpected source.")
        text(root, "3. Verify and diagnose", 19f, true)
        countView = text(root, "")
        button(root, "Open diagnostics") { showDiagnostics() }
        text(root,
            "Privacy: all processing occurs on this device. No internet permission is requested. " +
                "Diagnostics contain limited window package/class metadata, not general screen text. " +
                "You control whether to copy or share logs.")
        updateStatus()
    }

    private fun showDiagnostics() {
        viewingDiagnostics = true
        val root = page()
        text(root, "Diagnostics", 25f, true)
        text(root,
            "Use this while the warning occurs. WINDOW entries show system package and class names; " +
                "MATCH means the phrase was found; NO_BUTTON and CLICK_FAILED help narrow down failures; " +
                "DISMISSED records a successful accessibility click. Logs remain on the device.")
        button(root, "Back to settings") { showHome() }
        checkbox(root, "Record on-device diagnostics", prefs.diagnosticsEnabled) {
            prefs.diagnosticsEnabled = it
            if (it) DiagnosticStore.append(this, "SETTINGS", "Diagnostic logging enabled")
            refreshDiagnostics()
        }
        button(root, "Refresh log") { refreshDiagnostics() }
        button(root, "Copy diagnostics") {
            val data = diagnosticExport()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("SIM warning diagnostics", data))
            Toast.makeText(this, "Diagnostics copied", Toast.LENGTH_SHORT).show()
        }
        button(root, "Share diagnostics (explicit opt-in)") {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, diagnosticExport())
            }
            startActivity(Intent.createChooser(send, "Share diagnostic log"))
        }
        button(root, "Clear diagnostics") {
            DiagnosticStore.clear(this)
            refreshDiagnostics()
        }
        logView = text(root, "", 12f)
        logView?.setTypeface(Typeface.MONOSPACE)
        refreshDiagnostics()
    }

    private fun diagnosticExport(): String {
        val data = DiagnosticStore.read(this)
        return "eSIM Notification Zapper diagnostics\n" +
            "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}; " +
            "Android API ${android.os.Build.VERSION.SDK_INT}\n" +
            "Service enabled: ${serviceEnabled()}\n" +
            "Auto-dismiss armed: ${prefs.armed}\n" +
            "Filter: ${prefs.warningText}; Button: ${prefs.buttonText}\n\n" +
            data.ifBlank { "(no diagnostic events recorded)" }
    }

    private fun refreshDiagnostics() {
        logView?.text = DiagnosticStore.read(this)
            .ifBlank { "No events yet. Leave diagnostics enabled, return to another app, and wait for the warning." }
    }

    private fun updateStatus() {
        statusView?.text = when {
            !serviceEnabled() -> "Service: OFF — enable it in Android settings"
            !prefs.armed -> "Service: ON — automatic dismissal paused"
            else -> "Service: ON — automatic dismissal armed"
        }
        val time = if (prefs.lastDismissedAt == 0L) "never"
            else DateFormat.format("yyyy-MM-dd HH:mm:ss", prefs.lastDismissedAt).toString()
        countView?.text = "Successful click actions: ${prefs.dismissCount}\nLast click: $time"
    }

    private fun serviceEnabled(): Boolean {
        val expected = ComponentName(this, SimWarningAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it) == expected
        }
    }

    @Deprecated("Deprecated in Android platform; retained for compatibility with API 26+")
    override fun onBackPressed() {
        if (viewingDiagnostics) showHome() else super.onBackPressed()
    }
}
