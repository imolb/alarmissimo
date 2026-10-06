package com.alarmissimo.ui.screen

import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alarmissimo.ui.viewmodel.AlarmEventEditorViewModel

private val GONG_OPTIONS = listOf(
    "none"       to "Kein Gong",
    "bikebell1x" to "Fahrradklingel 1×",
    "bikebell2x" to "Fahrradklingel 2×",
    "gong1x"     to "Gong 1×",
    "gong2x"     to "Gong 2×",
    "gong3x"     to "Gong 3×",
    "gong4x"     to "Gong 4×",
    "doorbell"   to "Türklingel",
    "kettle"     to "Pauke"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEventEditorScreen(
    viewModel: AlarmEventEditorViewModel,
    onSaved: () -> Unit,
    onNavigateToAlarmSet: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val event = uiState.alarmEvent
    val alarmSet = uiState.alarmSet
    val profiles = uiState.availableProfiles
    val isRelative = alarmSet?.timeMode == "relative"
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    // System ringtone picker launcher
    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && event != null) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                viewModel.update(event.copy(gong = "system:$uri"))
            }
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable(onClick = onNavigateToAlarmSet)
                    ) {
                        Text(
                            text = event?.message?.ifEmpty { "Alarm-Ereignis" } ?: "Alarm-Ereignis",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (alarmSet != null) {
                            Text(
                                text = alarmSet.name.ifEmpty { "Alarm-Set" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.duplicate() }) {
                        Icon(Icons.Filled.ContentCopy, "Kopieren")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, "Löschen")
                    }
                }
            )
        }
    ) { padding ->
        if (event == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // ── Enabled ───────────────────────────────────────────────────
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Aktiviert", modifier = Modifier.weight(1f))
                        Switch(
                            checked = event.enabled,
                            onCheckedChange = { viewModel.update(event.copy(enabled = it)) }
                        )
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }

                // ── Time or Offset ─────────────────────────────────────────────
                item {
                    if (isRelative) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Minuten vor Ereignis:", modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                if (event.offsetMinutes > 0)
                                    viewModel.update(event.copy(offsetMinutes = event.offsetMinutes - 1))
                            }) { Text("-", style = MaterialTheme.typography.titleLarge) }
                            OutlinedTextField(
                                value = event.offsetMinutes.toString(),
                                onValueChange = { s -> s.toIntOrNull()?.let { v ->
                                    viewModel.update(event.copy(offsetMinutes = v.coerceIn(0, 480)))
                                }},
                                modifier = Modifier.width(80.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            IconButton(onClick = {
                                if (event.offsetMinutes < 480)
                                    viewModel.update(event.copy(offsetMinutes = event.offsetMinutes + 1))
                            }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                        }
                    } else {
                        InlineTimePickerField(
                            label = "Uhrzeit",
                            value = event.time,
                            onValueChange = { viewModel.update(event.copy(time = it)) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ── Gong ──────────────────────────────────────────────────────
                item {
                    var gongExpanded by remember { mutableStateOf(false) }
                    val isSystemUri = event.gong.startsWith("system:")
                    val gongLabel = when {
                        isSystemUri -> runCatching {
                            val uri = Uri.parse(event.gong.removePrefix("system:"))
                            RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Systemklang"
                        }.getOrElse { "Systemklang" }
                        else -> GONG_OPTIONS.firstOrNull { it.first == event.gong }?.second ?: event.gong
                    }
                    ExposedDropdownMenuBox(
                        expanded = gongExpanded,
                        onExpandedChange = { gongExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = gongLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Ton") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gongExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = gongExpanded, onDismissRequest = { gongExpanded = false }) {
                            GONG_OPTIONS.forEach { (gongId, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.update(event.copy(gong = gongId))
                                        gongExpanded = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Systemklang wählen …") },
                                onClick = {
                                    gongExpanded = false
                                    val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                                        if (isSystemUri) {
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                                Uri.parse(event.gong.removePrefix("system:")))
                                        }
                                    }
                                    ringtoneLauncher.launch(intent)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ── Time Playback ─────────────────────────────────────────────
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Uhrzeit ansagen")
                            Text(
                                "TTS spricht die aktuelle Uhrzeit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = event.timePlayback,
                            onCheckedChange = { viewModel.update(event.copy(timePlayback = it)) }
                        )
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }

                // ── Duration Playback (relative mode only) ────────────────────
                if (isRelative) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Restzeit ansagen")
                                Text(
                                    "TTS: \"Es sind noch X Minuten bis ${alarmSet?.endEventName?.ifEmpty { "…" }}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = event.durationPlayback,
                                onCheckedChange = { viewModel.update(event.copy(durationPlayback = it)) }
                            )
                        }
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // ── Message ───────────────────────────────────────────────────
                item {
                    OutlinedTextField(
                        value = event.message,
                        onValueChange = { viewModel.update(event.copy(message = it)) },
                        label = { Text("Nachricht (TTS)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // ── Voice Profile ─────────────────────────────────────────────
                item {
                    var profileExpanded by remember { mutableStateOf(false) }
                    val selectedProfile = profiles.firstOrNull { it.id == event.voiceProfileId }
                        ?: profiles.firstOrNull()
                    ExposedDropdownMenuBox(
                        expanded = profileExpanded,
                        onExpandedChange = { profileExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedProfile?.name ?: "Standard",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Stimmprofil") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = profileExpanded, onDismissRequest = { profileExpanded = false }) {
                            profiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.name) },
                                    onClick = {
                                        viewModel.update(event.copy(voiceProfileId = profile.id))
                                        profileExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ── Preview button ────────────────────────────────────────────
                item {
                    Button(
                        onClick = { viewModel.playNow(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Vorschau abspielen")
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Alarm-Ereignis löschen") },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineTimePickerField(label: String, value: String, onValueChange: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val parts = value.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
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
