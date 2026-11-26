package io.github.yungamp.shortvideoblocker.inspector

import kotlinx.serialization.Serializable

/** One node of a captured view tree. Field names are short to keep dumps small. */
@Serializable
data class NodeDump(
    val cls: String? = null,
    val id: String? = null,
    val text: String? = null,
    val desc: String? = null,
    val pane: String? = null,
    /** left, top, right, bottom in screen pixels. */
    val bounds: List<Int>,
    /** Comma-separated: visible, clickable, scrollable, selected. */
    val flags: String = "",
    val childCount: Int = 0,
    val children: List<NodeDump> = emptyList(),
)

@Serializable
data class TreeStats(
    val nodeCount: Int,
    val nodesWithId: Int,
    val maxDepth: Int,
    val truncated: Boolean,
    val durationMs: Double,
)

@Serializable
data class WindowDump(
    val windowId: Int,
    val type: String,
    val title: String? = null,
    val layer: Int,
    val active: Boolean,
    val focused: Boolean,
    val root: NodeDump? = null,
    val stats: TreeStats? = null,
)

/** The same screen captured with one setting of FLAG_INCLUDE_NOT_IMPORTANT_VIEWS. */
@Serializable
data class DumpVariant(
    val includeNotImportantViews: Boolean,
    val windows: List<WindowDump>,
)

/** Cost of a targeted findAccessibilityNodeInfosByViewId() call, as phase 1 will use it. */
@Serializable
data class LookupTiming(
    val viewId: String,
    val matches: Int,
    val durationMs: Double,
)

@Serializable
data class EventRecord(
    /** Milliseconds since the service started (SystemClock.uptimeMillis based). */
    val tMs: Long,
    val type: String,
    val pkg: String? = null,
    val cls: String? = null,
    val sourceId: String? = null,
    val windowId: Int = -1,
    val contentChangeTypes: Int = 0,
    val fromIndex: Int = -1,
    val toIndex: Int = -1,
    val itemCount: Int = -1,
    val scrollDeltaX: Int = 0,
    val scrollDeltaY: Int = 0,
)

@Serializable
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val sdkInt: Int,
    val release: String,
)

@Serializable
data class InspectorDump(
    val schemaVersion: Int = 1,
    val createdAt: String,
    val scenarioId: String,
    val scenarioLabel: String,
    val targetPackage: String,
    val targetVersionName: String? = null,
    val targetVersionCode: Long? = null,
    val device: DeviceInfo,
    val textRedacted: Boolean,
    val variants: List<DumpVariant>,
    /** IDs that only appear when not-important views are included. */
    val idsOnlyWithNotImportantViews: List<String>,
    val lookups: List<LookupTiming>,
    val eventCounts: Map<String, Int>,
    val recentEvents: List<EventRecord>,
)
