package io.github.yungamp.shortvideoblocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yungamp.shortvideoblocker.App
import io.github.yungamp.shortvideoblocker.inspector.AppVersions
import io.github.yungamp.shortvideoblocker.inspector.DumpEntry
import io.github.yungamp.shortvideoblocker.inspector.Scenarios
import io.github.yungamp.shortvideoblocker.inspector.TargetApps
import io.github.yungamp.shortvideoblocker.service.ServiceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(app: App) {
    val context = LocalContext.current
    var enabledInSettings by remember { mutableStateOf(A11yStatus.isEnabled(context)) }
    var versionsTick by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabledInSettings = A11yStatus.isEnabled(context)
        app.dumps.refresh()
        versionsTick++
    }

    val connected by ServiceState.connected.collectAsStateWithLifecycle()
    val lastMessage by ServiceState.lastMessage.collectAsStateWithLifecycle()
    val overlayEnabled by app.settings.overlayEnabled.collectAsStateWithLifecycle()
    val redact by app.settings.redactText.collectAsStateWithLifecycle()
    val entries by app.dumps.entries.collectAsStateWithLifecycle()
    val counts = remember(entries) { entries.groupingBy { it.scenarioId }.eachCount() }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("ShortVideoBlocker · Phase 0") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard("1. Bedienungshilfe aktivieren") {
                    val status = when {
                        connected -> "✅ Aktiv und verbunden"
                        enabledInSettings -> "⏳ Aktiviert, verbindet …"
                        else -> "❌ Nicht aktiviert"
                    }
                    Text(status, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Der Inspector liest nur auf deinen Knopfdruck die Bildschirmstruktur von YouTube " +
                            "und Instagram aus und speichert sie lokal. Nichts wird gesendet; Dateien verlassen " +
                            "das Gerät nur, wenn du sie unten selbst teilst.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = { A11yStatus.openAccessibilitySettings(context) }) {
                        Text("Bedienungshilfen öffnen")
                    }
                    Text(
                        "Ist der Schalter ausgegraut („Eingeschränkte Einstellung“)? Dann App-Info öffnen → " +
                            "⋮ oben rechts → „Eingeschränkte Einstellungen zulassen“ und es erneut versuchen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { A11yStatus.openAppInfo(context) }) { Text("App-Info öffnen") }
                }
            }

            item {
                SectionCard("2. Inspector") {
                    SwitchRow("Overlay-Knopf in YouTube/Instagram", overlayEnabled, app.settings::setOverlayEnabled)
                    SwitchRow("Texte schwärzen (empfohlen)", redact, app.settings::setRedactText)
                    Text(
                        "Bei „Texte schwärzen“ wird sichtbarer Text (z. B. Nachrichten, Titel) durch seine Länge " +
                            "ersetzt. Inhaltsbeschreibungen von Bedienelementen (z. B. „Reels“, „Gefällt mir“) " +
                            "bleiben erhalten, da sie für die Erkennung wichtig sind.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val versions = remember(versionsTick) {
                        TargetApps.all.map { it to AppVersions.of(context.packageManager, it) }
                    }
                    versions.forEach { (pkg, v) ->
                        Text(
                            "${TargetApps.label(pkg)}: " + (v?.let { "${it.versionName} (${it.versionCode})" } ?: "nicht installiert"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    lastMessage?.let { Text("Zuletzt: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }

            item {
                SectionCard("3. Checkliste") {
                    Text(
                        "Öffne YouTube bzw. Instagram, bring den jeweiligen Bildschirm in den Zustand und tippe " +
                            "auf den roten SVB-Knopf → passenden Eintrag wählen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TargetApps.all.forEach { pkg ->
                        Text(TargetApps.label(pkg), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                        Scenarios.forPackage(pkg).forEach { s ->
                            val n = counts[s.id] ?: 0
                            Column(Modifier.padding(start = 8.dp)) {
                                Text((if (n > 0) "✓ " else "○ ") + s.label + if (n > 1) " ($n)" else "")
                                Text(s.hint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 18.dp))
                            }
                        }
                    }
                }
            }

            item {
                SectionCard("4. Dumps (${entries.size})") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = entries.isNotEmpty(),
                            onClick = {
                                app.dumps.zipAll()?.let { Share.files(context, listOf(it), "Dumps teilen") }
                            },
                        ) { Text("Alle als ZIP teilen") }
                        OutlinedButton(enabled = entries.isNotEmpty(), onClick = { confirmDeleteAll = true }) {
                            Text("Alle löschen")
                        }
                    }
                }
            }

            items(entries, key = { it.baseName }) { entry ->
                DumpRow(
                    entry = entry,
                    onShare = { Share.files(context, entry.files, entry.baseName) },
                    onDelete = { app.dumps.delete(entry) },
                )
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Alle Dumps löschen?") },
            text = { Text("${entries.size} Dumps werden vom Gerät gelöscht.") },
            confirmButton = {
                TextButton(onClick = { app.dumps.deleteAll(); confirmDeleteAll = false }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DumpRow(entry: DumpEntry, onShare: () -> Unit, onDelete: () -> Unit) {
    val scenario = Scenarios.byId(entry.scenarioId)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(scenario?.let { "${TargetApps.label(it.pkg)}: ${it.label}" } ?: entry.scenarioId, fontWeight = FontWeight.SemiBold)
                Text(entry.baseName, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onShare) { Text("Teilen") }
            TextButton(onClick = onDelete) { Text("Löschen") }
        }
    }
}
