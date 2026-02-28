package com.alarmissimo.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alarmissimo.data.model.AlarmEvent
import com.alarmissimo.ui.viewmodel.AlarmSetEditorViewModel

// Labels Monday–Sunday (German abbreviations)
private val WEEKDAY_LABELS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
// Spec weekday indices: 1 = Monday ... 7 = Sunday  (item 6: fixes former Calendar-value bug)
private val WEEKDAY_INDICES = listOf(1, 2, 3, 4, 5, 6, 7)

/**
 * Alarm-Set Editor screen — name, enable toggle, volume, weekday chips, alarm-event list.
 *
 * @param viewModel The alarm-set editor view-model.
 * @param onNavigateToAlarmEventEditor Called when an alarm-event editor should be opened.
 * @param onSaved Called after save/delete completes; receiver should pop back.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmSetEditorScreen(
    viewModel: AlarmSetEditorViewModel,
    onNavigateToAlarmEventEditor: (Long, Long) -> Unit,
    onNavigateToAlarmSet: (Long) -> Unit = {},
    onSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Pop back after save/delete (item 8: event deletion must NOT set isSaved)
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaved()
    }

    // Item 7: Navigate to newly created or duplicated alarm-event editor
    val currentSetId = uiState.alarmSet?.id
    LaunchedEffect(currentSetId) {
        viewModel.navigateToAlarmEvent.collect { eventId ->
            val setId = uiState.alarmSet?.id ?: return@collect
            onNavigateToAlarmEventEditor(setId, eventId)
        }
    }

    // Item 19: Navigate to the duplicate's editor so its "(Kopie)" name is visible immediately
    LaunchedEffect(Unit) {
        viewModel.navigateToAlarmSet.collect { setId ->
            onNavigateToAlarmSet(setId)
        }
    }

    var deleteSetDialogVisible by remember { mutableStateOf(false) }
    var deleteEventCandidate by remember { mutableStateOf<AlarmEvent?>(null) }

    // Confirm delete alarm-set
    if (deleteSetDialogVisible) {
        AlertDialog(
            onDismissRequest = { deleteSetDialogVisible = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete()
                    deleteSetDialogVisible = false
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleteSetDialogVisible = false }) { Text("Abbrechen") }
            },
            title = { Text("Weckergruppe löschen?") },
            text = { Text("Diese Weckergruppe und alle ihre Alarme werden dauerhaft gelöscht.") }
        )
    }

    // Confirm delete alarm-event (item 8: stays on this screen after delete)
    deleteEventCandidate?.let { event ->
        AlertDialog(
            onDismissRequest = { deleteEventCandidate = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAlarmEvent(event.id)   // item 8: no navigation
                    deleteEventCandidate = null
                }) { Text("Löschen") }
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
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
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
                    Text("Lautstärke: ${set.audioVolume}%", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = set.audioVolume.toFloat(),
                        onValueChange = { viewModel.update(set.copy(audioVolume = it.toInt())) },
                        valueRange = 0f..100f
                    )
                }
            }

            // --- Weekday chips (item 6: FlowRow so "So"/Sunday is always visible) ---
            item {
                Text("Wochentage", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WEEKDAY_LABELS.forEachIndexed { index, label ->
                        val dayIndex = WEEKDAY_INDICES[index]   // item 6: correct spec values
                        val selected = set.weekdays.contains(dayIndex)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val updated = if (selected)
                                    (set.weekdays - dayIndex).sorted()
                                else
                                    (set.weekdays + dayIndex).sorted()
                                viewModel.update(set.copy(weekdays = updated))
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // --- Alarm-event list header + add button above list (item 22) ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Alarme",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    FilledIconButton(
                        onClick = { viewModel.addAlarmEvent() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Neuer Alarm")
                    }
                }
            }

            // --- Alarm-event rows (item 9: time + message shown) ---
            items(set.alarmEvents, key = { it.id }) { event ->
                AlarmEventRow(
                    alarmEvent = event,
                    onEditClick = { onNavigateToAlarmEventEditor(set.id, event.id) },
                    onCopyClick = { viewModel.duplicateAlarmEvent(event.id) },  // item 7
                    onDeleteClick = { deleteEventCandidate = event }            // item 8
                )
                HorizontalDivider(modifier = Modifier.padding(start = 8.dp))
            }

            // --- Actions: Save / Duplicate / Delete (item 20: icon-only) ---
            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledIconButton(
                        onClick = { viewModel.save() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Speichern")
                    }
                    OutlinedIconButton(
                        onClick = { viewModel.duplicate() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplizieren")
                    }
                    OutlinedIconButton(
                        onClick = { deleteSetDialogVisible = true },
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.outlinedIconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Löschen")
                    }
                }
            }
        }
    }
}

/**
 * A single row representing an alarm-event within the alarm-set editor.
 * Shows time and message (item 9).
 */
@Composable
private fun AlarmEventRow(
    alarmEvent: AlarmEvent,
    onEditClick: () -> Unit,
    onCopyClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alarmEvent.time,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = alarmEvent.message.ifBlank { "(keine Nachricht)" },
                style = MaterialTheme.typography.bodySmall,
                color = if (alarmEvent.message.isNotBlank())
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.outlineVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onEditClick) {
            Icon(Icons.Default.Edit, contentDescription = "Bearbeiten")
        }
        IconButton(onClick = onCopyClick) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Duplizieren")
        }
        IconButton(onClick = onDeleteClick) {
            Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
        }
    }
}
