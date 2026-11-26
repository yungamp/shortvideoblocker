package io.github.yungamp.shortvideoblocker.detection

/** Screen bounds of a node, independent of android.graphics.Rect so it works in JVM tests. */
data class NodeBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Minimal read-only view of an accessibility node.
 *
 * Detection and dumping code only talks to this interface, never to AccessibilityNodeInfo
 * directly, so that it can be unit-tested on the JVM with fake trees.
 *
 * Every property access on the real implementation may be an IPC call into the target app,
 * so callers should read each property at most once.
 */
interface UiNode {
    val className: String?
    val packageName: String?
    /** Fully qualified resource id, e.g. "com.instagram.android:id/clips_viewer_view_pager". */
    val viewId: String?
    val text: String?
    val contentDescription: String?
    val paneTitle: String?
    val bounds: NodeBounds
    val isVisibleToUser: Boolean
    val isClickable: Boolean
    val isScrollable: Boolean
    val isSelected: Boolean
    val childCount: Int

    /** Returns the child at [index]; the caller must [release] it. */
    fun child(index: Int): UiNode?

    /** Releases the underlying native object (no-op on Android 13+). */
    fun release()
}
