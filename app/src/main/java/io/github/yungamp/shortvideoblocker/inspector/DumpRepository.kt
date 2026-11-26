package io.github.yungamp.shortvideoblocker.inspector

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A saved dump: a .json file plus a .txt rendering with the same base name. */
data class DumpEntry(val baseName: String, val scenarioId: String, val files: List<File>, val createdMillis: Long)

/**
 * Stores dumps in the app's private files dir (filesDir/dumps).
 * Files only leave the device when the user shares them from the app.
 */
class DumpRepository(private val dumpDir: File, private val shareDir: File) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = false
    }

    private val _entries = MutableStateFlow<List<DumpEntry>>(emptyList())
    val entries: StateFlow<List<DumpEntry>> = _entries.asStateFlow()

    init {
        dumpDir.mkdirs()
        refresh()
    }

    @Synchronized
    fun save(dump: InspectorDump, now: Date = Date()): DumpEntry {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)
        var base = "${stamp}_${dump.scenarioId}"
        var n = 2
        while (File(dumpDir, "$base.json").exists()) base = "${stamp}_${dump.scenarioId}_${n++}"
        File(dumpDir, "$base.json").writeText(json.encodeToString(InspectorDump.serializer(), dump))
        File(dumpDir, "$base.txt").writeText(TextTreeFormatter.format(dump))
        refresh()
        return _entries.value.first { it.baseName == base }
    }

    @Synchronized
    fun delete(entry: DumpEntry) {
        entry.files.forEach { it.delete() }
        refresh()
    }

    @Synchronized
    fun deleteAll() {
        dumpDir.listFiles()?.forEach { it.delete() }
        refresh()
    }

    /** Packs all dumps into one zip in the share dir and returns it. */
    @Synchronized
    fun zipAll(now: Date = Date()): File? {
        val files = dumpDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) return null
        shareDir.mkdirs()
        shareDir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)
        val zip = File(shareDir, "svb-dumps_$stamp.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            for (f in files) {
                out.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        return zip
    }

    /** Scenario IDs that have at least one dump, with counts. */
    fun scenarioCounts(): Map<String, Int> = _entries.value.groupingBy { it.scenarioId }.eachCount()

    @Synchronized
    fun refresh() {
        val files = dumpDir.listFiles()?.filter { it.isFile && (it.extension == "json" || it.extension == "txt") }.orEmpty()
        _entries.value = files
            .groupBy { it.nameWithoutExtension }
            .map { (base, group) ->
                DumpEntry(base, parseScenarioId(base), group.sortedBy { it.extension }, group.maxOf { it.lastModified() })
            }
            .sortedByDescending { it.baseName }
    }

    companion object {
        /** "20261126-120000_yt_shorts_tab_2" -> "yt_shorts_tab". */
        fun parseScenarioId(baseName: String): String {
            val rest = baseName.substringAfter('_', baseName)
            return rest.replace(Regex("_\\d+$"), "")
        }
    }
}
