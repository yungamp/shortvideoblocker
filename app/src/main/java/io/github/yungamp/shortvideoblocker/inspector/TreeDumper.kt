package io.github.yungamp.shortvideoblocker.inspector

import io.github.yungamp.shortvideoblocker.detection.UiNode

/**
 * Walks a [UiNode] tree and converts it into a serializable [NodeDump].
 *
 * Only used by the inspector (phase 0). The real detector will never walk the full tree.
 * Children are released after use; releasing the root is the caller's job.
 */
class TreeDumper(
    private val redactText: Boolean,
    private val maxNodes: Int = 5000,
    private val maxDepth: Int = 80,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    data class Result(val root: NodeDump, val stats: TreeStats)

    private class Counter {
        var nodes = 0
        var withId = 0
        var maxDepth = 0
        var truncated = false
    }

    fun dump(root: UiNode): Result {
        val start = nanoTime()
        val counter = Counter()
        val tree = dumpNode(root, 0, counter)
        val durationMs = (nanoTime() - start) / 1_000_000.0
        return Result(
            tree,
            TreeStats(counter.nodes, counter.withId, counter.maxDepth, counter.truncated, durationMs),
        )
    }

    private fun dumpNode(node: UiNode, depth: Int, counter: Counter): NodeDump {
        counter.nodes++
        if (depth > counter.maxDepth) counter.maxDepth = depth

        val id = node.viewId
        if (id != null) counter.withId++
        val childCount = node.childCount

        val children = ArrayList<NodeDump>(childCount)
        if (depth >= maxDepth) {
            if (childCount > 0) counter.truncated = true
        } else {
            for (i in 0 until childCount) {
                if (counter.nodes >= maxNodes) {
                    counter.truncated = true
                    break
                }
                val child = node.child(i) ?: continue
                try {
                    children += dumpNode(child, depth + 1, counter)
                } finally {
                    child.release()
                }
            }
        }

        val b = node.bounds
        return NodeDump(
            cls = node.className,
            id = id,
            text = node.text.let { if (redactText) redact(it) else it },
            desc = node.contentDescription,
            pane = node.paneTitle,
            bounds = listOf(b.left, b.top, b.right, b.bottom),
            flags = flags(node),
            childCount = childCount,
            children = children,
        )
    }

    private fun flags(node: UiNode): String = buildList {
        if (node.isVisibleToUser) add("visible")
        if (node.isClickable) add("clickable")
        if (node.isScrollable) add("scrollable")
        if (node.isSelected) add("selected")
    }.joinToString(",")

    companion object {
        /** Replaces a text with its length so that no message content ends up in a dump. */
        fun redact(text: String?): String? = text?.let { "<${it.length} Zeichen>" }

        /** All view IDs contained in a dumped tree. */
        fun collectIds(root: NodeDump?, into: MutableSet<String> = linkedSetOf()): MutableSet<String> {
            if (root == null) return into
            root.id?.let(into::add)
            root.children.forEach { collectIds(it, into) }
            return into
        }
    }
}
