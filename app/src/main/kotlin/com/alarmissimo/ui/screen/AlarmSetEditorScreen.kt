package com.alarmissimo.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alarmissimo.data.model.AlarmEvent
import com.alarmissimo.data.model.AlarmSet
import com.alarmissimo.ui.viewmodel.AlarmSetEditorViewModel
import com.alarmissimo.util.TimeUtils
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSetEditorScreen(
    viewModel: AlarmSetEditorViewModel,
    onNavigateToAlarmEventEditor: (Long, Long) -> Unit,
    onNavigateToAlarmSet: (Long) -> Unit,
    onSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val alarmSet = uiState.alarmSet
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTimeModeWarning by remember { mutableStateOf<String?>(null) } // target mode

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaved()
    }

    val currentAlarmSetId by rememberUpdatedState(alarmSet?.id)
    LaunchedEffect(Unit) {
        viewModel.navigateToAlarmEvent.collect { eventId ->
            currentAlarmSetId?.let { setId -> onNavigateToAlarmEventEditor(setId, eventId) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToAlarmSet.collect { setId ->
            onNavigateToAlarmSet(setId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(alarmSet?.name?.ifEmpty { "Alarm-Set" } ?: "Alarm-Set") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.duplicate() }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Kopieren")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.addAlarmEvent() }) {
                Icon(Icons.Filled.Add, contentDescription = "Neues Alarm-Ereignis")
            }
        }
    ) { padding ->
        if (alarmSet == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // ── Name ─────────────────────────────────────────────────────
                item {
                    OutlinedTextField(
                        value = alarmSet.name,
                        onValueChange = { viewModel.update(alarmSet.copy(name = it)) },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // ── Enabled ──────────────────────────────────────────────────
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Aktiviert",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = alarmSet.enabled,
                            onCheckedChange = { viewModel.update(alarmSet.copy(enabled = it)) }
                        )
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }

                // ── Weekday chips (disabled when specificDate is set) ─────────
                item {
                    Text("Wochentage", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val days = listOf("Mo","Di","Mi","Do","Fr","Sa","So")
                        for (idx in days.indices) {
                            val label = days[idx]
                            val day = idx + 1
                            val selected = day in alarmSet.weekdays
                            val canToggle = alarmSet.specificDate == null
                            val bgColor = when {
                                !canToggle && selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                !canToggle             -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                selected               -> MaterialTheme.colorScheme.primary
                                else                   -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            val textColor = when {
                                !canToggle -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                selected   -> MaterialTheme.colorScheme.onPrimary
                                else       -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Surface(
                                onClick = {
                                    if (!canToggle) return@Surface
                                    val newDays = if (selected) alarmSet.weekdays - day
                                                 else alarmSet.weekdays + day
                                    viewModel.update(alarmSet.copy(weekdays = newDays.sorted()))
                                },
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                                shape = CircleShape,
                                color = bgColor
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(label, fontSize = 12.sp, color = textColor)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ── Specific Date ─────────────────────────────────────────────
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = alarmSet.specificDate?.let { TimeUtils.formatDateDisplay(it) } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Datum (einmalig)") },
                            placeholder = { Text("Kein Datum gewählt") },
                            modifier = Modifier.weight(1f),
                            trailingIcon = {
                                if (alarmSet.specificDate != null) {
                                    IconButton(onClick = {
                                        viewModel.update(alarmSet.copy(specificDate = null))
                                    }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Datum entfernen")
                                    }
                                }
                            }
                        )
                        IconButton(onClick = { showDatePickerDialog = true }) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = "Datum wählen")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ── Time Mode ─────────────────────────────────────────────────
                item {
                    Text("Zeitmodus", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("absolute" to "Absolute Zeiten", "relative" to "Relative Zeiten")
                            .forEach { (mode, label) ->
                                FilterChip(
                                    selected = alarmSet.timeMode == mode,
                                    label = { Text(label) },
                                    onClick = {
                                        if (alarmSet.timeMode != mode) {
                                            showTimeModeWarning = mode
                                        }
                                    }
                                )
                            }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ── End Time + End Event Name (relative mode) ──────────────────
                if (alarmSet.timeMode == "relative") {
                    item {
                        TimePickerField(
                            label = "Ereignis Uhrzeit",
                            value = alarmSet.endTime ?: "08:00",
                            onValueChange = { viewModel.update(alarmSet.copy(endTime = it)) }
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = alarmSet.endEventName,
                            onValueChange = { if (it.length <= 50) viewModel.update(alarmSet.copy(endEventName = it)) },
                            label = { Text("Name des Ereignisses (für TTS)") },
                            placeholder = { Text("z. B. Frühstück") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = { Text("${alarmSet.endEventName.length}/50") }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // ── Audio Volume ──────────────────────────────────────────────
                item {
                    Text("Lautstärke: ${alarmSet.audioVolume}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = alarmSet.audioVolume.toFloat(),
                        onValueChange = { viewModel.update(alarmSet.copy(audioVolume = it.toInt())) },
                        valueRange = 0f..100f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // ── Alarm Events list ─────────────────────────────────────────
                item {
                    Text("Alarm-Ereignisse", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                }

                if (alarmSet.alarmEvents.isEmpty()) {
                    item {
                        Text(
                            "Noch keine Alarm-Ereignisse. Drücke + um eines hinzuzufügen.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    val sortedEvents = if (alarmSet.timeMode == "relative")
                        alarmSet.alarmEvents.sortedByDescending { it.offsetMinutes }
                    else
                        alarmSet.alarmEvents
                    items(sortedEvents, key = { it.id }) { event ->
                        AlarmEventRow(
                            event = event,
                            alarmSet = alarmSet,
                            onEdit = { viewModel.saveAndNavigateToAlarmEvent(event.id) },
                            onDuplicate = { viewModel.duplicateAlarmEvent(event.id) },
                            onDelete = { viewModel.deleteAlarmEvent(event.id) },
                            onToggleEnabled = { enabled ->
                                viewModel.toggleAlarmEventEnabled(event.id, enabled)
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // Date picker dialog
    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = parseIsoDateMillis(alarmSet?.specificDate)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val isoDate = sdf.format(java.util.Date(millis))
                        if (alarmSet != null) viewModel.update(alarmSet.copy(specificDate = isoDate))
                    }
                    showDatePickerDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) { Text("Abbrechen") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time mode switch warning
    showTimeModeWarning?.let { newMode ->
        AlertDialog(
            onDismissRequest = { showTimeModeWarning = null },
            title = { Text("Zeitmodus wechseln") },
            text = { Text("Beim Wechsel des Zeitmodus werden alle Zeiten/Offsets der Ereignisse zurückgesetzt.") },
            confirmButton = {
                TextButton(onClick = {
                    if (alarmSet != null) {
                        val resetEvents = alarmSet.alarmEvents.map {
                            it.copy(time = "07:00", offsetMinutes = 0)
                        }
                        viewModel.update(alarmSet.copy(timeMode = newMode, alarmEvents = resetEvents))
                    }
                    showTimeModeWarning = null
                }) { Text("Wechseln") }
            },
            dismissButton = {
                TextButton(onClick = { showTimeModeWarning = null }) { Text("Abbrechen") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Alarm-Set löschen") },
            text = { Text("Wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete()
                    showDeleteDialog = false
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun AlarmEventRow(
    event: AlarmEvent,
    alarmSet: AlarmSet,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    val triggerTime = TimeUtils.computeAbsoluteTriggerTime(alarmSet, event)
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Alarm-Ereignis löschen") },
            text = { Text("Wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Clickable content area
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit)
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(
                text = triggerTime,
                style = MaterialTheme.typography.titleMedium,
                color = if (event.enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (alarmSet.timeMode == "relative") {
                Text(
                    text = "${event.offsetMinutes} min vor Ereignis",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (event.message.isNotBlank()) {
                Text(
                    text = event.message,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        LedChip(
            enabled = event.enabled,
            onClick = { onToggleEnabled(!event.enabled) },
            modifier = Modifier.padding(end = 4.dp)
        )
        IconButton(onClick = onDuplicate) { Icon(Icons.Filled.ContentCopy, "Kopieren") }
        IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Filled.Delete, "Löschen") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerField(label: String, value: String, onValueChange: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "Uhrzeit ändern")
            }
        }
    )
    if (showPicker) {
        val parts = value.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val state = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange("%02d:%02d".format(state.hour, state.minute))
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Abbrechen") } },
            text = { TimePicker(state = state) }
        )
    }
}

private fun parseIsoDateMillis(isoDate: String?): Long? {
    if (isoDate == null) return null
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.parse(isoDate)?.time
    } catch (e: Exception) { null }
}
