package io.github.yungamp.shortvideoblocker.inspector

/** Renders a dump as an indented, human-readable text file (easier to skim than the JSON). */
object TextTreeFormatter {

    fun format(dump: InspectorDump): String = buildString {
        appendLine("Szenario:   ${dump.scenarioLabel} (${dump.scenarioId})")
        appendLine("App:        ${dump.targetPackage} ${dump.targetVersionName ?: "?"} (${dump.targetVersionCode ?: "?"})")
        appendLine("Gerät:      ${dump.device.manufacturer} ${dump.device.model}, Android ${dump.device.release} (SDK ${dump.device.sdkInt})")
        appendLine("Erstellt:   ${dump.createdAt}")
        appendLine("Text geschwärzt: ${dump.textRedacted}")
        appendLine()
        for (variant in dump.variants) {
            appendLine("=".repeat(80))
            appendLine("includeNotImportantViews = ${variant.includeNotImportantViews}")
            for (w in variant.windows) {
                appendLine("-".repeat(80))
                appendLine(
                    "Fenster #${w.windowId} type=${w.type} title=${w.title} layer=${w.layer} " +
                        "active=${w.active} focused=${w.focused}",
                )
                w.stats?.let {
                    appendLine(
                        "  Knoten=${it.nodeCount} mitId=${it.nodesWithId} Tiefe=${it.maxDepth} " +
                            "abgeschnitten=${it.truncated} Dauer=${"%.1f".format(it.durationMs)} ms",
                    )
                }
                w.root?.let { appendNode(it, 0) }
            }
        }
        appendLine()
        appendLine("=".repeat(80))
        appendLine("IDs nur mit includeNotImportantViews:")
        dump.idsOnlyWithNotImportantViews.forEach { appendLine("  $it") }
        appendLine()
        appendLine("Gezielte Suche per View-ID (findAccessibilityNodeInfosByViewId):")
        dump.lookups.forEach {
            appendLine("  ${"%6.2f".format(it.durationMs)} ms  ${it.matches} Treffer  ${it.viewId}")
        }
        appendLine()
        appendLine("Event-Zähler:")
        dump.eventCounts.entries.sortedByDescending { it.value }.forEach { appendLine("  ${it.value}\t${it.key}") }
        appendLine()
        appendLine("Letzte Events (älteste zuerst):")
        dump.recentEvents.forEach { appendLine("  " + formatEvent(it)) }
    }

    fun formatNodeLine(node: NodeDump): String = buildString {
        append('[').append(node.cls?.substringAfterLast('.') ?: "?").append(']')
        node.id?.let { append(" #").append(it.substringAfter(":id/")) }
        node.text?.let { append(" text=\"").append(it.oneLine()).append('"') }
        node.desc?.let { append(" desc=\"").append(it.oneLine()).append('"') }
        node.pane?.let { append(" pane=\"").append(it.oneLine()).append('"') }
        val b = node.bounds
        if (b.size == 4) append(" (${b[0]},${b[1]}-${b[2]},${b[3]})")
        if (node.flags.isNotEmpty()) append(" {").append(node.flags).append('}')
        if (node.childCount > node.children.size) append(" +${node.childCount - node.children.size} nicht erfasst")
    }

    fun formatEvent(e: EventRecord): String = buildString {
        append("%7d".format(e.tMs)).append(' ').append(e.type)
        e.pkg?.let { append(" pkg=").append(it) }
        e.cls?.let { append(" cls=").append(it.substringAfterLast('.')) }
        e.sourceId?.let { append(" src=").append(it.substringAfter(":id/")) }
        if (e.windowId >= 0) append(" win=").append(e.windowId)
        if (e.contentChangeTypes != 0) append(" change=0x").append(e.contentChangeTypes.toString(16))
        if (e.fromIndex >= 0 || e.toIndex >= 0) append(" idx=${e.fromIndex}..${e.toIndex}/${e.itemCount}")
        if (e.scrollDeltaX != 0 || e.scrollDeltaY != 0) append(" d=(${e.scrollDeltaX},${e.scrollDeltaY})")
    }

    private fun StringBuilder.appendNode(node: NodeDump, depth: Int) {
        append("  ".repeat(depth + 1)).appendLine(formatNodeLine(node))
        node.children.forEach { appendNode(it, depth + 1) }
    }

    private fun String.oneLine(): String {
        val flat = replace('\n', ' ')
        return if (flat.length > 80) flat.take(77) + "..." else flat
    }
}
