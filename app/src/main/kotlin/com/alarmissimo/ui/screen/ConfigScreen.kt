package com.alarmissimo.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alarmissimo.R
import com.alarmissimo.data.model.AlarmSet
import com.alarmissimo.ui.viewmodel.ConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    viewModel: ConfigViewModel,
    onNavigateToAlarmSetEditor: (Long) -> Unit,
    onNavigateToVoiceList: () -> Unit,
    onNavigateToSoundDevice: () -> Unit,
    onNavigateUp: () -> Unit
) {
    val alarmSets by viewModel.alarmSets.collectAsState()
    var deleteCandidate by remember { mutableStateOf<AlarmSet?>(null) }

    LaunchedEffect(Unit) {
        viewModel.navigateToAlarmSet.collect { id ->
            onNavigateToAlarmSetEditor(id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Konfiguration") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.addAlarmSet() }) {
                Icon(Icons.Filled.Add, contentDescription = "Neuer Alarm-Set")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // ── Alarm-Set cards ──────────────────────────────────────────────
            if (alarmSets.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Noch keine Alarm-Sets vorhanden.\nDrücke + um einen neuen zu erstellen.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(alarmSets, key = { it.id }) { alarmSet ->
                    AlarmSetCard(
                        alarmSet = alarmSet,
                        onEdit = { onNavigateToAlarmSetEditor(alarmSet.id) },
                        onDuplicate = { viewModel.duplicateAlarmSet(alarmSet.id) },
                        onDelete = { deleteCandidate = alarmSet },
                        onToggleEnabled = { enabled -> viewModel.setAlarmSetEnabled(alarmSet.id, enabled) }
                    )
                    HorizontalDivider()
                }
            }

            // ── Global action buttons ────────────────────────────────────────
            item { Spacer(Modifier.height(16.dp)) }

            item {
                Button(
                    onClick = onNavigateToVoiceList,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text("Stimmprofile")
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            item {
                Button(
                    onClick = onNavigateToSoundDevice,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text("Soundgerät")
                }
            }

            // Build time and website link at bottom of config screen
            item {
                val uriHandler = LocalUriHandler.current
                val buildTime = LocalContext.current.getString(R.string.build_time)
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Build: $buildTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
                TextButton(
                    onClick = { uriHandler.openUri("https://github.com/imolb/alarmissimo") },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "github.com/imolb/alarmissimo",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Alarm-Set löschen") },
            text = { Text("\"${candidate.name}\" wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAlarmSet(candidate.id)
                    deleteCandidate = null
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun AlarmSetCard(
    alarmSet: AlarmSet,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Clickable info area
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = alarmSet.name.ifEmpty { "(kein Name)" },
                style = MaterialTheme.typography.titleMedium,
                color = if (alarmSet.enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val schedule = buildScheduleLabel(alarmSet)
            if (schedule.isNotEmpty()) {
                Text(
                    text = schedule,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${alarmSet.alarmEvents.size} Alarm-Ereignis(se)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // LED + Copy + Delete
        LedChip(
            enabled = alarmSet.enabled,
            onClick = { onToggleEnabled(!alarmSet.enabled) },
            modifier = Modifier.padding(end = 4.dp)
        )
        IconButton(onClick = onDuplicate) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Kopieren")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Löschen")
        }
    }
}

private fun buildScheduleLabel(alarmSet: AlarmSet): String {
    return when {
        alarmSet.specificDate != null -> {
            val dateParts = alarmSet.specificDate.split("-")
            if (dateParts.size == 3) "${dateParts[2]}.${dateParts[1]}.${dateParts[0]}" else alarmSet.specificDate
        }
        alarmSet.weekdays.size == 7 -> "Täglich"
        alarmSet.weekdays.isEmpty() -> "Keine Tage gewählt"
        else -> {
            val labels = listOf("Mo","Di","Mi","Do","Fr","Sa","So")
            alarmSet.weekdays.sorted().joinToString(", ") { labels.getOrNull(it - 1) ?: "$it" }
        }
    }
}
