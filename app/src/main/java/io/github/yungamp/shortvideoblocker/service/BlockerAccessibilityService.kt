package io.github.yungamp.shortvideoblocker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import io.github.yungamp.shortvideoblocker.App
import io.github.yungamp.shortvideoblocker.detection.AccessibilityUiNode
import io.github.yungamp.shortvideoblocker.detection.releaseCompat
import io.github.yungamp.shortvideoblocker.inspector.AppVersions
import io.github.yungamp.shortvideoblocker.inspector.DeviceInfo
import io.github.yungamp.shortvideoblocker.inspector.DumpVariant
import io.github.yungamp.shortvideoblocker.inspector.EventRecord
import io.github.yungamp.shortvideoblocker.inspector.InspectorDump
import io.github.yungamp.shortvideoblocker.inspector.InspectorOverlay
import io.github.yungamp.shortvideoblocker.inspector.LookupTiming
import io.github.yungamp.shortvideoblocker.inspector.Scenario
import io.github.yungamp.shortvideoblocker.inspector.TargetApps
import io.github.yungamp.shortvideoblocker.inspector.TreeDumper
import io.github.yungamp.shortvideoblocker.inspector.WindowDump
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 0: the service only acts as an inspector. It records events of the target apps and,
 * on request from the overlay, captures the view tree of the current screen to a file.
 * Nothing is blocked yet.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var app: App
    private var overlay: InspectorOverlay? = null
    private var dumpJob: Job? = null
    private val startUptime = SystemClock.uptimeMillis()

    override fun onServiceConnected() {
        super.onServiceConnected()
        app = application as App
        ServiceState.setConnected(true)
        overlay = InspectorOverlay(
            service = this,
            doneCounts = { app.dumps.scenarioCounts() },
            onScenarioChosen = ::captureDump,
        )
        scope.launch {
            combine(app.settings.overlayEnabled, ServiceState.foregroundPackage) { enabled, pkg -> enabled to pkg }
                .collect { (enabled, pkg) -> updateOverlay(enabled, pkg) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        val type = event.eventType

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            updateForeground()
        }
        if (pkg != null && pkg in TargetApps.all) {
            app.eventLog.add(toRecord(event, pkg))
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        overlay?.hide()
        overlay = null
        scope.cancel()
        ServiceState.setConnected(false)
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------------------
    // Foreground tracking
    // ---------------------------------------------------------------------------------------

    private fun updateForeground() {
        val root = rootInActiveWindow ?: return
        val active = root.packageName?.toString()
        root.releaseCompat()
        // Our own overlay must never count as "foreground app".
        if (active == null || active == packageName) return
        if (active != ServiceState.foregroundPackage.value) {
            ServiceState.setForeground(active)
            // Only the package name of other apps is logged, nothing else.
            app.eventLog.add(EventRecord(tMs = SystemClock.uptimeMillis() - startUptime, type = "FOREGROUND", pkg = active))
        }
    }

    private fun updateOverlay(enabled: Boolean, pkg: String?) {
        val o = overlay ?: return
        if (enabled && pkg != null && pkg in TargetApps.all) o.show(pkg) else o.hide()
    }

    private fun toRecord(event: AccessibilityEvent, pkg: String): EventRecord {
        val type = event.eventType
        val wantsSource = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            type == AccessibilityEvent.TYPE_VIEW_SELECTED
        val sourceId = if (wantsSource) {
            event.source?.let { src -> src.viewIdResourceName.also { src.releaseCompat() } }
        } else {
            null
        }
        return EventRecord(
            tMs = event.eventTime - startUptime,
            type = AccessibilityEvent.eventTypeToString(type).removePrefix("TYPE_"),
            pkg = pkg,
            cls = event.className?.toString(),
            sourceId = sourceId,
            windowId = event.windowId,
            contentChangeTypes = event.contentChangeTypes,
            fromIndex = event.fromIndex,
            toIndex = event.toIndex,
            itemCount = event.itemCount,
            scrollDeltaX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaX else 0,
            scrollDeltaY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaY else 0,
        )
    }

    // ---------------------------------------------------------------------------------------
    // Dump capture
    // ---------------------------------------------------------------------------------------

    private fun captureDump(scenario: Scenario) {
        val target = ServiceState.foregroundPackage.value
        if (target == null || target !in TargetApps.all) {
            toast("Keine Ziel-App im Vordergrund")
            return
        }
        if (dumpJob?.isActive == true) return
        overlay?.setBusy(true)
        dumpJob = scope.launch {
            try {
                // Let the scenario panel disappear and the target app settle.
                delay(400)
                val redact = app.settings.redactText.value

                // 1) As phase 1 will see it: only "important" views.
                setIncludeNotImportantViews(false)
                val important = withContext(Dispatchers.Default) { captureVariant(target, false, redact) }
                val lookups = withContext(Dispatchers.Default) { measureLookups(target, important) }

                // 2) Everything, to find out whether we need FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.
                setIncludeNotImportantViews(true)
                delay(300)
                val all = try {
                    withContext(Dispatchers.Default) { captureVariant(target, true, redact) }
                } finally {
                    setIncludeNotImportantViews(false)
                }

                val idsImportant = important.windows.flatMapTo(mutableSetOf()) { TreeDumper.collectIds(it.root) }
                val idsAll = all.windows.flatMapTo(linkedSetOf()) { TreeDumper.collectIds(it.root) }
                val version = AppVersions.of(packageManager, target)

                val dump = InspectorDump(
                    createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()),
                    scenarioId = scenario.id,
                    scenarioLabel = "${TargetApps.label(target)}: ${scenario.label}",
                    targetPackage = target,
                    targetVersionName = version?.versionName,
                    targetVersionCode = version?.versionCode,
                    device = DeviceInfo(Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT, Build.VERSION.RELEASE),
                    textRedacted = redact,
                    variants = listOf(important, all),
                    idsOnlyWithNotImportantViews = (idsAll - idsImportant).sorted(),
                    lookups = lookups,
                    eventCounts = app.eventLog.counts(),
                    recentEvents = app.eventLog.snapshot(),
                )
                val entry = withContext(Dispatchers.IO) { app.dumps.save(dump) }
                val nodes = important.windows.sumOf { it.stats?.nodeCount ?: 0 }
                val msg = "Gespeichert: ${scenario.label} ($nodes Knoten)"
                ServiceState.setMessage("$msg – ${entry.baseName}")
                toast(msg)
            } catch (e: Exception) {
                ServiceState.setMessage("Fehler beim Dump: ${e.message}")
                toast("Fehler beim Dump: ${e.message}")
            } finally {
                overlay?.setBusy(false)
            }
        }
    }

    /** Must run on the main thread. */
    private fun setIncludeNotImportantViews(include: Boolean) {
        val info = serviceInfo ?: return
        val flag = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        val newFlags = if (include) info.flags or flag else info.flags and flag.inv()
        if (newFlags != info.flags) {
            info.flags = newFlags
            serviceInfo = info
        }
    }

    private fun captureVariant(target: String, includeNotImportant: Boolean, redact: Boolean): DumpVariant {
        val dumper = TreeDumper(redactText = redact)
        val result = mutableListOf<WindowDump>()
        for (w in windows) {
            val root = w.root ?: continue
            try {
                if (root.packageName?.toString() != target) continue
                val (tree, stats) = dumper.dump(AccessibilityUiNode(root))
                result += WindowDump(
                    windowId = w.id,
                    type = windowTypeName(w.type),
                    title = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) w.title?.toString() else null,
                    layer = w.layer,
                    active = w.isActive,
                    focused = w.isFocused,
                    root = tree,
                    stats = stats,
                )
            } finally {
                root.releaseCompat()
            }
        }
        if (result.isEmpty()) {
            // Fallback in case window retrieval is unavailable.
            rootInActiveWindow?.let { root ->
                try {
                    if (root.packageName?.toString() == target) {
                        val (tree, stats) = dumper.dump(AccessibilityUiNode(root))
                        result += WindowDump(root.windowId, "ACTIVE_ROOT", null, 0, true, true, tree, stats)
                    }
                } finally {
                    root.releaseCompat()
                }
            }
        }
        return DumpVariant(includeNotImportant, result)
    }

    /**
     * Measures what phase 1 will actually do: a few targeted findAccessibilityNodeInfosByViewId
     * calls. Only IDs that look relevant are measured, to keep the capture quick.
     */
    private fun measureLookups(target: String, variant: DumpVariant): List<LookupTiming> {
        val timings = mutableListOf<LookupTiming>()

        val t0 = System.nanoTime()
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return timings
        timings += LookupTiming("<rootInActiveWindow>", 1, (System.nanoTime() - t0) / 1_000_000.0)
        try {
            if (root.packageName?.toString() != target) return timings
            val ids = variant.windows
                .flatMapTo(linkedSetOf()) { TreeDumper.collectIds(it.root) }
                .filter { RELEVANT_ID.containsMatchIn(it.substringAfter(":id/")) }
                .take(MAX_LOOKUPS)
            for (id in ids) {
                val start = System.nanoTime()
                val found = root.findAccessibilityNodeInfosByViewId(id)
                val ms = (System.nanoTime() - start) / 1_000_000.0
                timings += LookupTiming(id, found.size, ms)
                found.forEach { it.releaseCompat() }
            }
        } finally {
            root.releaseCompat()
        }
        return timings
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private companion object {
        val RELEVANT_ID = Regex("(?i)reel|short|clip|pager|player|viewer|tab|direct|thread|feed|watch|pivot|navigation")
        const val MAX_LOOKUPS = 40

        fun windowTypeName(type: Int): String = when (type) {
            AccessibilityWindowInfo.TYPE_APPLICATION -> "APPLICATION"
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "INPUT_METHOD"
            AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "ACCESSIBILITY_OVERLAY"
            AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "SPLIT_SCREEN_DIVIDER"
            else -> "TYPE_$type"
        }
    }
}
