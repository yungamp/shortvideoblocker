package io.github.yungamp.shortvideoblocker.inspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeDumperTest {

    private fun sampleTree() = FakeNode(
        viewId = "com.example:id/root",
        children = listOf(
            FakeNode(
                viewId = "com.example:id/clips_viewer_view_pager",
                isScrollable = true,
                children = listOf(FakeNode(text = "Hallo Welt", contentDescription = "Reel von Max")),
            ),
            FakeNode(className = "android.widget.Button", contentDescription = "Reels", isClickable = true),
        ),
    )

    @Test
    fun `dump keeps structure ids and flags`() {
        val result = TreeDumper(redactText = false).dump(sampleTree())

        assertEquals(4, result.stats.nodeCount)
        assertEquals(2, result.stats.nodesWithId)
        assertEquals(2, result.stats.maxDepth)
        assertFalse(result.stats.truncated)

        val pager = result.root.children[0]
        assertEquals("com.example:id/clips_viewer_view_pager", pager.id)
        assertTrue("scrollable" in pager.flags)
        assertEquals("Hallo Welt", pager.children[0].text)
        assertEquals("Reels", result.root.children[1].desc)
    }

    @Test
    fun `redaction replaces text but keeps content descriptions`() {
        val result = TreeDumper(redactText = true).dump(sampleTree())
        val leaf = result.root.children[0].children[0]
        assertEquals("<10 Zeichen>", leaf.text)
        assertEquals("Reel von Max", leaf.desc)
    }

    @Test
    fun `children are released, root is left to the caller`() {
        val tree = sampleTree()
        TreeDumper(redactText = true).dump(tree)
        assertEquals(0, tree.releaseCount)
        tree.allNodes().drop(1).forEach { assertEquals(1, it.releaseCount) }
    }

    @Test
    fun `node limit truncates the dump`() {
        val wide = FakeNode(children = List(10) { FakeNode() })
        val result = TreeDumper(redactText = false, maxNodes = 4).dump(wide)
        assertEquals(4, result.stats.nodeCount)
        assertTrue(result.stats.truncated)
        assertEquals(10, result.root.childCount)
        assertEquals(3, result.root.children.size)
    }

    @Test
    fun `depth limit truncates the dump`() {
        val deep = FakeNode(children = listOf(FakeNode(children = listOf(FakeNode(children = listOf(FakeNode()))))))
        val result = TreeDumper(redactText = false, maxDepth = 1).dump(deep)
        assertEquals(2, result.stats.nodeCount)
        assertTrue(result.stats.truncated)
    }

    @Test
    fun `collectIds finds all ids`() {
        val result = TreeDumper(redactText = false).dump(sampleTree())
        assertEquals(
            setOf("com.example:id/root", "com.example:id/clips_viewer_view_pager"),
            TreeDumper.collectIds(result.root),
        )
    }

    @Test
    fun `text formatter prints short ids and flags`() {
        val result = TreeDumper(redactText = false).dump(sampleTree())
        val line = TextTreeFormatter.formatNodeLine(result.root.children[0])
        assertEquals("[FrameLayout] #clips_viewer_view_pager (0,0-100,100) {visible,scrollable}", line)
    }
}
