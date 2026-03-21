package com.alarmissimo.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alarmissimo.data.model.AlarmEvent
import com.alarmissimo.data.model.AlarmSet
import com.alarmissimo.ui.viewmodel.ConfigViewModel

/**
 * Configuration screen — lists all alarm-sets with edit/copy/delete actions and a FAB.
 *
 * @param viewModel The configuration view-model.
 * @param onNavigateToAlarmSetEditor Called when the pencil icon or FAB is tapped.
 *   Receives the alarm-set ID.
 * @param onNavigateToVoiceConfig Called when the "Sprachkonfiguration" button is tapped.
 * @param onNavigateUp Called when the back-to-dashboard button is tapped (item 14).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    viewModel: ConfigViewModel,
    onNavigateToAlarmSetEditor: (Long) -> Unit,
    onNavigateToVoiceConfig: () -> Unit = {},
    onNavigateUp: () -> Unit
) {
    val alarmSets by viewModel.alarmSets.collectAsState()
    var deleteCandidate by remember { mutableStateOf<AlarmSet?>(null) }

    // Items 4 & 10: navigate to new/duplicated alarm-set after creation
    LaunchedEffect(Unit) {
        viewModel.navigateToAlarmSet.collect { newSetId ->
            onNavigateToAlarmSetEditor(newSetId)
        }
    }

    // Confirmation dialog for deletion
    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAlarmSet(candidate.id)
                    deleteCandidate = null
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Abbrechen") }
            },
            title = { Text("Weckergruppe löschen?") },
            text = { Text("Die Weckergruppe \"${candidate.name}\" und alle zugeh\u00F6rigen Alarme werden dauerhaft gel\u00F6scht.") }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Konfiguration") },
                // Item 14: back-to-dashboard navigation arrow
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück zum Dashboard"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.addAlarmSet() }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Neue Weckergruppe"
                )
            }
        }
    ) { padding ->
        if (alarmSets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    VoiceConfigButton(onNavigateToVoiceConfig)
                    Text(
                        text = "Keine Weckergruppen vorhanden.\nTippe auf + um eine neue anzulegen.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    VoiceConfigButton(onNavigateToVoiceConfig)
                }
                items(alarmSets, key = { it.id }) { alarmSet ->
                    AlarmSetCard(
                        alarmSet = alarmSet,
                        onEditClick = { onNavigateToAlarmSetEditor(alarmSet.id) },
                        onCopyClick = { viewModel.duplicateAlarmSet(alarmSet.id) },
                        onDeleteClick = { deleteCandidate = alarmSet }
                    )
                }
            }
        }
    }
}

/**
 * Full-width outlined button that navigates to the Voice Configuration screen.
 */
@Composable
private fun VoiceConfigButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.RecordVoiceOver,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("Sprachkonfiguration")
    }
}

/**
 * A card representing a single alarm-set in the config list.
 * Item 13: Shows the first 5 alarm-events with their time and message.
 */
@Composable
private fun AlarmSetCard(
    alarmSet: AlarmSet,
    onEditClick: () -> Unit,
    onCopyClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left: name, status, and first 5 alarm-events
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarmSet.name.ifBlank { "(kein Name)" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${if (alarmSet.enabled) "Aktiv" else "Inaktiv"} · ${alarmSet.alarmEvents.size} Alarm${if (alarmSet.alarmEvents.size != 1) "e" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Item 13: first 5 alarm-events
                if (alarmSet.alarmEvents.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    alarmSet.alarmEvents.take(5).forEach { event ->
                        AlarmEventPreviewRow(event)
                    }
                    if (alarmSet.alarmEvents.size > 5) {
                        Text(
                            text = "+${alarmSet.alarmEvents.size - 5} weitere…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            // Right: icon-only action buttons
            Column {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Bearbeiten")
                }
                IconButton(onClick = onCopyClick) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplizieren")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Löschen",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Compact row showing a single alarm-event's time and message inside the config card.
 * Used by item 13 to preview the first 5 events.
 */
@Composable
private fun AlarmEventPreviewRow(event: AlarmEvent) {
    Row(
        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = event.time,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (event.message.isNotBlank()) {
            Text(
                text = event.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
