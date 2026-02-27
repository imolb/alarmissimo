package com.alarmissimo.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alarmissimo.data.model.AlarmSet
import com.alarmissimo.ui.viewmodel.ConfigViewModel

/**
 * Configuration screen — lists all alarm-sets with edit/copy/delete actions and a FAB.
 *
 * @param viewModel The configuration view-model.
 * @param onNavigateToAlarmSetEditor Called when the pencil icon on an alarm-set is tapped.
 *   Receives the alarm-set ID.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    viewModel: ConfigViewModel,
    onNavigateToAlarmSetEditor: (Long) -> Unit
) {
    val alarmSets by viewModel.alarmSets.collectAsState()
    var deleteCandidate by remember { mutableStateOf<AlarmSet?>(null) }

    // Confirmation dialog for deletion
    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAlarmSet(candidate.id)
                        deleteCandidate = null
                    }
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Abbrechen")
                }
            },
            title = { Text("Weckergruppe löschen?") },
            text = { Text("Die Weckergruppe \"${candidate.name}\" und alle zugeh\u00F6rigen Alarme werden dauerhaft gel\u00F6scht.") }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Konfiguration") })
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Keine Weckergruppen vorhanden.\nTippe auf + um eine neue anzulegen.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
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
 * A card representing a single alarm-set in the config list.
 *
 * @param alarmSet The alarm-set to display.
 * @param onEditClick Called when the pencil icon is tapped.
 * @param onCopyClick Called when the copy icon is tapped.
 * @param onDeleteClick Called when the trash icon is tapped.
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarmSet.name.ifBlank { "(kein Name)" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(if (alarmSet.enabled) "Aktiv" else "Inaktiv")
                        append(" · ")
                        append("${alarmSet.alarmEvents.size} Alarm${if (alarmSet.alarmEvents.size != 1) "e" else ""}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Bearbeiten"
                )
            }
            IconButton(onClick = onCopyClick) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Duplizieren"
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Löschen",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
