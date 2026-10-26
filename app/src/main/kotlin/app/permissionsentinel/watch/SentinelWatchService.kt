package app.permissionsentinel.watch

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.permissionsentinel.SentinelApplication
import app.permissionsentinel.data.RecordType
import app.permissionsentinel.data.SentinelRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** A system permission dialog observed through the accessibility watch. */
data class DialogCapture(val at: Long, val sourcePackage: String, val text: String)

/**
 * Scoped accessibility watch. It only observes window switches and system
 * permission dialogs (permissioncontroller); it never reads or stores the
 * screen content of other apps, and every record stays on device.
 */
class SentinelWatchService : AccessibilityService() {

    override fun onServiceConnected() {
        connected.value = true
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        lastWindowPackage.value = pkg
        if (!pkg.contains("permissioncontroller")) return
        val text = event.text
            ?.filterNotNull()
            ?.joinToString(" | ") { it.toString() }
            .orEmpty()
            .ifBlank { windowContentText() }
        if (text.isBlank()) return
        val prev = lastDialog.value
        if (prev != null && prev.text == text && prev.sourcePackage == pkg) return
        val capture = DialogCapture(System.currentTimeMillis(), pkg, text)
        lastDialog.value = capture
        val app = application as? SentinelApplication ?: return
        app.appScope.launch {
            app.repository.recordStore.append(
                SentinelRecord(capture.at, RecordType.PERMISSION_DIALOG, pkg, text.hashCode().toLong())
            )
        }
    }

    /** Bounded traversal of the active window; used only for permission dialogs. */
    private fun windowContentText(): String {
        val root = rootInActiveWindow ?: return ""
        val out = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue += root
        var visited = 0
        while (queue.isNotEmpty() && visited < 120) {
            val node = queue.removeFirst()
            visited++
            node.text?.let { if (it.isNotBlank()) out += it.toString() }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue += it }
        }
        return out.joinToString(" | ")
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        connected.value = false
        super.onDestroy()
    }

    companion object {
        val connected = MutableStateFlow(false)
        val lastWindowPackage = MutableStateFlow<String?>(null)
        val lastDialog = MutableStateFlow<DialogCapture?>(null)
    }
}

/** Reads whether the scoped watch is enabled in system accessibility settings. */
object WatchAccessibility {
    fun isEnabled(context: Context): Boolean {
        val resolver = context.contentResolver
        if (Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
            return false
        }
        val enabled = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val flat = ComponentName(context, SentinelWatchService::class.java).flattenToString()
        return enabled.split(':').any {
            it.equals(flat, ignoreCase = true) || it.contains("SentinelWatchService")
        }
    }
}
