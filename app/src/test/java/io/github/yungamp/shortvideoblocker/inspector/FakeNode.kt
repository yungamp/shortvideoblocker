package io.github.yungamp.shortvideoblocker.inspector

import io.github.yungamp.shortvideoblocker.detection.NodeBounds
import io.github.yungamp.shortvideoblocker.detection.UiNode

/** In-memory [UiNode] for JVM tests. */
class FakeNode(
    override val className: String? = "android.widget.FrameLayout",
    override val viewId: String? = null,
    override val text: String? = null,
    override val contentDescription: String? = null,
    override val paneTitle: String? = null,
    override val packageName: String? = "com.example",
    override val bounds: NodeBounds = NodeBounds(0, 0, 100, 100),
    override val isVisibleToUser: Boolean = true,
    override val isClickable: Boolean = false,
    override val isScrollable: Boolean = false,
    override val isSelected: Boolean = false,
    val children: List<FakeNode> = emptyList(),
) : UiNode {
    var releaseCount = 0
        private set

    override val childCount: Int get() = children.size
    override fun child(index: Int): UiNode? = children.getOrNull(index)
    override fun release() {
        releaseCount++
    }

    fun allNodes(): List<FakeNode> = listOf(this) + children.flatMap { it.allNodes() }
}
