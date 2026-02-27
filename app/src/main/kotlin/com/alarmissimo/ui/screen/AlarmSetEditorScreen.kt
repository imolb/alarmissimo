package com.alarmissimo.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alarmissimo.data.model.AlarmEvent
import com.alarmissimo.data.model.AlarmSet
import com.alarmissimo.ui.viewmodel.AlarmSetEditorUiState
import com.alarmissimo.ui.viewmodel.AlarmSetEditorViewModel

private val WEEKDAY_LABELS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
// Calendar weekday indices (1=Mon … 7=Sun)
private val WEEKDAY_INDICES = listOf(2, 3, 4, 5, 6, 7, 1)

/**
 * Alarm-Set Editor screen — name, enable toggle, volume, weekday chips, alarm-event list.
 *
 * @param viewModel The alarm-set editor view-model.
 * @param onNavigateToAlarmEventEditor Called when the pencil icon or "Neuer Alarm" is tapped.
 *   Receives (alarmSetId, alarmEventId). -1L as alarmEventId means new.
 * @param onSaved Called after save/delete/duplicate completes; receiver should pop back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSetEditorScreen(
    viewModel: AlarmSetEditorViewModel,
    onNavigateToAlarmEventEditor: (Long, Long) -> Unit,
    onSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Pop back after save/delete
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaved()
    }

    var deleteDialogVisible by remember { mutableStateOf(false) }
    var deleteEventCandidate by remember { mutableStateOf<AlarmEvent?>(null) }

    // Confirm delete alarm-set
    if (deleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete()
                        deleteDialogVisible = false
                    }
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogVisible = false }) { Text("Abbrechen") }
            },
            title = { Text("Weckergruppe löschen?") },
            text = { Text("Diese Weckergruppe und alle ihre Alarme werden dauerhaft gelöscht.") }
        )
    }

    // Confirm delete alarm-event
    deleteEventCandidate?.let { event ->
        AlertDialog(
            onDismissRequest = { deleteEventCandidate = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val set = uiState.alarmSet ?: return@TextButton
                        viewModel.update(set.copy(alarmEvents = set.alarmEvents.filter { it.id != event.id }))
                        viewModel.save()
                        deleteEventCandidate = null
                    }
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleteEventCandidate = null }) { Text("Abbrechen") }
            },
            title = { Text("Alarm löschen?") },
            text = { Text("Der Alarm um ${event.time} wird dauerhaft gelöscht.") }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Weckergruppe bearbeiten") })
        }
    ) { padding ->
        val set = uiState.alarmSet
        if (set == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Name ---
            item {
                OutlinedTextField(
                    value = set.name,
                    onValueChange = { if (it.length <= 30) viewModel.update(set.copy(name = it)) },
                    label = { Text("Name") },
                    singleLine = true,
                    supportingText = { Text("${set.name.length}/30") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }

            // --- Enabled toggle ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aktiviert",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = set.enabled,
                        onCheckedChange = { viewModel.update(set.copy(enabled = it)) }
                    )
                }
            }

            // --- Volume slider ---
            item {
                Column {
                    Text(
                        text = "Lautstärke: ${set.audioVolume}%",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Slider(
                        value = set.audioVolume.toFloat(),
                        onValueChange = { viewModel.update(set.copy(audioVolume = it.toInt())) },
                        valueRange = 0f..100f,
                        steps = 0
                    )
                }
            }

            // --- Weekday chips ---
            item {
                Text("Wochentage", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WEEKDAY_LABELS.forEachIndexed { index, label ->
                        val dayIndex = WEEKDAY_INDICES[index]
                        val selected = set.weekdays.contains(dayIndex)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val updated = if (selected) {
                                    set.weekdays - dayIndex
                                } else {
                                    (set.weekdays + dayIndex).sorted()
                                }
                                viewModel.update(set.copy(weekdays = updated))
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // --- Alarm-event list header ---
            item {
                Text("Alarme", style = MaterialTheme.typography.titleMedium)
            }

            // --- Alarm-event rows ---
            items(set.alarmEvents, key = { it.id }) { event ->
                AlarmEventRow(
                    alarmEvent = event,
                    onEditClick = { onNavigateToAlarmEventEditor(set.id, event.id) },
                    onCopyClick = {
                        val copy = event.copy(id = System.currentTimeMillis())
                        viewModel.update(
                            set.copy(alarmEvents = (set.alarmEvents + copy).sortedBy { it.time })
                        )
                        viewModel.save()
                    },
                    onDeleteClick = { deleteEventCandidate = event }
                )
            }

            // --- Add alarm-event ---
            item {
                OutlinedButton(
                    onClick = { viewModel.addAlarmEvent() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Neuer Alarm")
                }
            }

            // --- Actions: Save / Duplicate / Delete ---
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.save() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Speichern") }

                    OutlinedButton(
                        onClick = { viewModel.duplicate() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Duplizieren") }

                    OutlinedButton(
                        onClick = { deleteDialogVisible = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Löschen") }
                }
            }
        }
    }
}

/**
 * A single row representing an alarm-event within the alarm-set editor.
 */
@Composable
private fun AlarmEventRow(
    alarmEvent: AlarmEvent,
    onEditClick: () -> Unit,
    onCopyClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alarmEvent.time,
                style = MaterialTheme.typography.titleSmall
            )
            if (alarmEvent.message.isNotBlank()) {
                Text(
                    text = alarmEvent.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
