package ca.hdclark.esimnotificationzapper

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Event-driven, narrowly targeted auto-dismissal.
 * Cannot preempt a protected platform dialog or fix physical SIM hardware.
 */
class SimWarningAccessibilityService : AccessibilityService() {
    private var lastContentScanMs = 0L
    private var lastClickMs = 0L
    private val lastLoggedMs = mutableMapOf<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticStore.append(this, "SERVICE", "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val prefs = AppPrefs(this)
        if (!prefs.armed && !prefs.diagnosticsEnabled) return

        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val sourcePackage = event.packageName?.toString().orEmpty()
        val systemEvent = PackageGuard.isProbablySystem(sourcePackage)
        val now = SystemClock.elapsedRealtime()

        // Restrict frequent content-change scans to possible system warnings.
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (!systemEvent && !prefs.allowOtherPackages) return
            if (now - lastContentScanMs < 400L) return
            lastContentScanMs = now
        }

        if (prefs.diagnosticsEnabled && systemEvent) {
            val className = event.className?.toString().orEmpty().take(90)
            recordThrottled(
                "window:$sourcePackage:$className:$type", 15000L,
                "WINDOW", "event=$type package=$sourcePackage class=$className"
            )
        }

        if (!prefs.armed || !WarningMatcher.validTarget(prefs.warningText)) return

        // The active window can be insufficient for Samsung overlays.
        // FLAG_RETRIEVE_INTERACTIVE_WINDOWS provides additional accessible roots.
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots.add(it) }
        try {
            windows.forEach { window -> window.root?.let { roots.add(it) } }
        } catch (_: SecurityException) {
            recordThrottled("windows-unavailable", 60000L,
                "ACCESS", "Other interactive windows were not accessible")
        }

        for (root in roots) {
            val owner = root.packageName?.toString().orEmpty().ifBlank { sourcePackage }
            if (!prefs.allowOtherPackages && !PackageGuard.isProbablySystem(owner)) continue
            if (!hasWarning(root, prefs.warningText)) continue

            recordThrottled(
                "matched:$owner", 5000L, "MATCH",
                "Configured warning matched in package=$owner"
            )
            val button = findConfirmation(root, prefs.buttonText)
            if (button == null) {
                recordThrottled("no-button:$owner", 10000L, "NO_BUTTON",
                    "Warning found, but no clickable '${prefs.buttonText}' in same window")
                continue
            }
            if (now - lastClickMs < 1200L) return
            val clicked = try {
                button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } catch (_: SecurityException) {
                false
            }
            if (clicked) {
                lastClickMs = now
                prefs.dismissCount = prefs.dismissCount + 1
                prefs.lastDismissedAt = System.currentTimeMillis()
                DiagnosticStore.append(this, "DISMISSED",
                    "Clicked configured button in package=$owner")
                return
            }
            recordThrottled("click-failed:$owner", 10000L, "CLICK_FAILED",
                "Matched warning but accessibility click returned false in package=$owner")
        }
    }

    private fun hasWarning(root: AccessibilityNodeInfo, phrase: String): Boolean {
        return visit(root) { node ->
            WarningMatcher.warningMatches(node.text, phrase) ||
                WarningMatcher.warningMatches(node.contentDescription, phrase)
        } != null
    }

    private fun findConfirmation(
        root: AccessibilityNodeInfo,
        label: String
    ): AccessibilityNodeInfo? {
        return visit(root) { node ->
            if (!WarningMatcher.buttonMatches(node.text, label) &&
                !WarningMatcher.buttonMatches(node.contentDescription, label)
            ) return@visit false

            clickableAncestor(node) != null
        }?.let { clickableAncestor(it) }
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(4) {
            if (current?.isClickable == true &&
                current?.isEnabled == true &&
                current?.isVisibleToUser == true
            ) return current
            current = current?.parent
        }
        return null
    }

    /** Bounded traversal prevents pathological window trees from monopolizing the UI thread. */
    private fun visit(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        while (queue.isNotEmpty() && seen++ < 450) {
            val node = queue.removeFirst()
            if (predicate(node)) return node
            for (index in 0 until minOf(node.childCount, 50)) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return null
    }

    private fun recordThrottled(
        key: String,
        intervalMs: Long,
        category: String,
        details: String
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - (lastLoggedMs[key] ?: -intervalMs) < intervalMs) return
        lastLoggedMs[key] = now
        if (lastLoggedMs.size > 100) lastLoggedMs.clear()
        DiagnosticStore.append(this, category, details)
    }

    override fun onInterrupt() {
        DiagnosticStore.append(this, "SERVICE", "Accessibility service interrupted")
    }

    override fun onDestroy() {
        DiagnosticStore.append(this, "SERVICE", "Accessibility service stopped")
        super.onDestroy()
    }
}
