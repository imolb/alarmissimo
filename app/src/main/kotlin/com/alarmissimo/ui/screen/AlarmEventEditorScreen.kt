package com.alarmissimo.ui.screen

import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.alarmissimo.ui.viewmodel.AlarmEventEditorViewModel

private val GONG_OPTIONS = listOf(
    "none"     to "Kein Sound",
    "bikebell" to "Fahrradklingel",
    "doorbell" to "Türklingel",
    "kettle"   to "Pauke",
    "gong"     to "Gong"
)

/**
 * Alarm-Event Editor screen — time, gong, timePlayback toggle, message and preview button.
 *
 * Item 3:  System alarm-sound picker via RingtoneManager.
 * Item 12: Actions use icon buttons (Save / Play / Duplicate / Delete).
 * Item 17: Material3 TimePicker replaces android.app.TimePickerDialog.
 *
 * @param viewModel The alarm-event editor view-model.
 * @param onSaved   Called after save/delete completes; receiver should pop back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEventEditorScreen(
    viewModel: AlarmEventEditorViewModel,
    onSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Pop back after save/delete
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaved()
    }

    var deleteDialogVisible by remember { mutableStateOf(false) }
    var gongDropdownExpanded by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Item 3 — Android system alarm-sound picker
    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = result.data
                ?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                val event = uiState.alarmEvent ?: return@rememberLauncherForActivityResult
                viewModel.update(event.copy(gong = "system:$uri"))
            }
        }
    }

    // Confirm-delete dialog
    if (deleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete()
                    deleteDialogVisible = false
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogVisible = false }) { Text("Abbrechen") }
            },
            title = { Text("Alarm löschen?") },
            text = { Text("Dieser Alarm wird dauerhaft gelöscht.") }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Alarm bearbeiten") })
        }
    ) { padding ->
        val event = uiState.alarmEvent
        if (event == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        // Item 17 — Material3 TimePicker (replaces android.app.TimePickerDialog)
        if (showTimePicker) {
            val timeParts = remember(event.time) {
                val p = event.time.split(":")
                (p.getOrNull(0)?.toIntOrNull() ?: 7) to (p.getOrNull(1)?.toIntOrNull() ?: 0)
            }
            val tpState = rememberTimePickerState(
                initialHour   = timeParts.first,
                initialMinute = timeParts.second,
                is24Hour      = true
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.update(
                            event.copy(time = "%02d:%02d".format(tpState.hour, tpState.minute))
                        )
                        showTimePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") }
                },
                text = { TimePicker(state = tpState) }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Time ─────────────────────────────────────────────────────────
            Column {
                Text("Uhrzeit", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = event.time, style = MaterialTheme.typography.headlineMedium)
                }
            }

            // ── Gong dropdown + system ringtone (item 3) ──────────────────
            Column {
                Text("Gong-Sound", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))

                val currentLabel = remember(event.gong, context) {
                    when {
                        event.gong.startsWith("system:") -> {
                            val uri = Uri.parse(event.gong.removePrefix("system:"))
                            try {
                                RingtoneManager.getRingtone(context, uri)?.getTitle(context)
                                    ?: "Systemton"
                            } catch (e: Exception) { "Systemton" }
                        }
                        else -> GONG_OPTIONS.find { it.first == event.gong }?.second ?: "Kein Sound"
                    }
                }

                ExposedDropdownMenuBox(
                    expanded  = gongDropdownExpanded,
                    onExpandedChange = { gongDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value       = currentLabel,
                        onValueChange = {},
                        readOnly    = true,
                        label       = { Text("Sound") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gongDropdownExpanded) },
                        modifier    = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded  = gongDropdownExpanded,
                        onDismissRequest = { gongDropdownExpanded = false }
                    ) {
                        GONG_OPTIONS.forEach { (id, label) ->
                            DropdownMenuItem(
                                text  = { Text(label) },
                                onClick = {
                                    viewModel.update(event.copy(gong = id))
                                    gongDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Open Android system alarm-sound picker
                OutlinedButton(
                    onClick = {
                        val currentUri = if (event.gong.startsWith("system:"))
                            Uri.parse(event.gong.removePrefix("system:")) else null
                        val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            if (currentUri != null)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
                        }
                        ringtoneLauncher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Systemton wählen…")
                }
            }

            // ── TimePlayback toggle ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Uhrzeit ansagen", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Spricht die Uhrzeit auf Deutsch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked         = event.timePlayback,
                    onCheckedChange = { viewModel.update(event.copy(timePlayback = it)) }
                )
            }

            // ── Message ──────────────────────────────────────────────────────
            OutlinedTextField(
                value       = event.message,
                onValueChange = { if (it.length <= 300) viewModel.update(event.copy(message = it)) },
                label       = { Text("Nachricht") },
                placeholder = { Text("Optionale Sprachnachricht…") },
                minLines    = 3,
                maxLines    = 6,
                supportingText = { Text("${event.message.length}/300") },
                modifier    = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction      = ImeAction.Default
                )
            )

            HorizontalDivider()

            // ── Actions — icon buttons (item 12) ─────────────────────────────
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Save
                FilledIconButton(
                    onClick  = { viewModel.save() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Speichern")
                }

                // Play now
                FilledTonalIconButton(
                    onClick  = { viewModel.playNow(context) },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Jetzt abspielen")
                }

                // Duplicate → go back to alarm-set screen
                OutlinedIconButton(
                    onClick  = { viewModel.duplicate(); onSaved() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplizieren")
                }

                // Delete
                OutlinedIconButton(
                    onClick  = { deleteDialogVisible = true },
                    modifier = Modifier.size(56.dp),
                    colors   = IconButtonDefaults.outlinedIconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border   = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Löschen")
                }
            }
        }
    }
}
