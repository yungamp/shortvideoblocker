package io.github.yungamp.shortvideoblocker.detection

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/** [UiNode] backed by a real [AccessibilityNodeInfo]. */
class AccessibilityUiNode(private val info: AccessibilityNodeInfo) : UiNode {
    override val className: String? get() = info.className?.toString()
    override val packageName: String? get() = info.packageName?.toString()
    override val viewId: String? get() = info.viewIdResourceName
    override val text: String? get() = info.text?.toString()
    override val contentDescription: String? get() = info.contentDescription?.toString()
    override val paneTitle: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.paneTitle?.toString() else null

    override val bounds: NodeBounds
        get() {
            val r = Rect()
            info.getBoundsInScreen(r)
            return NodeBounds(r.left, r.top, r.right, r.bottom)
        }

    override val isVisibleToUser: Boolean get() = info.isVisibleToUser
    override val isClickable: Boolean get() = info.isClickable
    override val isScrollable: Boolean get() = info.isScrollable
    override val isSelected: Boolean get() = info.isSelected
    override val childCount: Int get() = info.childCount

    override fun child(index: Int): UiNode? = info.getChild(index)?.let(::AccessibilityUiNode)

    override fun release() = info.releaseCompat()
}

/** AccessibilityNodeInfo.recycle() is required before Android 13 and a deprecated no-op afterwards. */
fun AccessibilityNodeInfo.releaseCompat() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        @Suppress("DEPRECATION")
        recycle()
    }
}
