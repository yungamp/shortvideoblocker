package io.github.yungamp.shortvideoblocker.inspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Date
import java.util.zip.ZipFile

class EventLogAndRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `event log keeps only the newest entries but counts all`() {
        val log = EventLog(capacity = 3)
        repeat(5) { log.add(EventRecord(tMs = it.toLong(), type = "VIEW_SCROLLED", pkg = "p")) }
        assertEquals(listOf(2L, 3L, 4L), log.snapshot().map { it.tMs })
        assertEquals(mapOf("VIEW_SCROLLED p" to 5), log.counts())
    }

    @Test
    fun `scenario id is parsed from file name`() {
        assertEquals("yt_shorts_tab", DumpRepository.parseScenarioId("20261126-120000_yt_shorts_tab"))
        assertEquals("yt_shorts_tab", DumpRepository.parseScenarioId("20261126-120000_yt_shorts_tab_2"))
        assertEquals("ig_reel_from_dm", DumpRepository.parseScenarioId("20261126-120000_ig_reel_from_dm"))
    }

    @Test
    fun `save list zip and delete`() {
        val repo = DumpRepository(tmp.newFolder("dumps"), tmp.newFolder("share"))
        val dump = sampleDump("ig_reels_tab")
        val now = Date(0)
        repo.save(dump, now)
        repo.save(dump, now) // same second -> suffix
        assertEquals(2, repo.entries.value.size)
        assertEquals(mapOf("ig_reels_tab" to 2), repo.scenarioCounts())
        repo.entries.value.forEach { assertEquals(listOf("json", "txt"), it.files.map { f -> f.extension }) }

        val zip = repo.zipAll(now)
        assertNotNull(zip)
        assertEquals(4, ZipFile(zip!!).use { it.size() })

        repo.delete(repo.entries.value.first())
        assertEquals(1, repo.entries.value.size)
        repo.deleteAll()
        assertEquals(0, repo.entries.value.size)
    }

    private fun sampleDump(scenarioId: String): InspectorDump {
        val tree = TreeDumper(redactText = true).dump(FakeNode(viewId = "com.instagram.android:id/x"))
        return InspectorDump(
            createdAt = "2026-11-26T12:00:00+0100",
            scenarioId = scenarioId,
            scenarioLabel = "Instagram: Reels-Tab",
            targetPackage = TargetApps.INSTAGRAM,
            device = DeviceInfo("Google", "Pixel", 36, "16"),
            textRedacted = true,
            variants = listOf(
                DumpVariant(false, listOf(WindowDump(1, "APPLICATION", null, 0, true, true, tree.root, tree.stats))),
            ),
            idsOnlyWithNotImportantViews = emptyList(),
            lookups = listOf(LookupTiming("com.instagram.android:id/x", 1, 0.5)),
            eventCounts = mapOf("VIEW_SCROLLED com.instagram.android" to 3),
            recentEvents = listOf(EventRecord(tMs = 1, type = "VIEW_SCROLLED", pkg = TargetApps.INSTAGRAM)),
        )
    }
}
