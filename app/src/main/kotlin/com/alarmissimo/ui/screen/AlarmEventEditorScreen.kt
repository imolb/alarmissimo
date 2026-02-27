package com.alarmissimo.ui.screen

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
    "none" to "Kein Sound",
    "bikebell" to "Fahrradklingel",
    "doorbell" to "Türklingel",
    "kettle" to "Pauke",
    "gong" to "Gong"
)

/**
 * Alarm-Event Editor screen — time, gong, timePlayback toggle, message and preview button.
 *
 * @param viewModel The alarm-event editor view-model.
 * @param onSaved Called after save/delete completes; receiver should pop back.
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

    // Confirm delete
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Time picker ---
            Column {
                Text("Uhrzeit", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))

                val (hours, minutes) = remember(event.time) {
                    val parts = event.time.split(":")
                    (parts.getOrNull(0)?.toIntOrNull() ?: 7) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
                }

                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                viewModel.update(
                                    event.copy(time = "%02d:%02d".format(h, m))
                                )
                            },
                            hours,
                            minutes,
                            true   // 24h format
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = event.time,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }

            // --- Gong dropdown ---
            Column {
                Text("Gong-Sound", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))

                val currentLabel = GONG_OPTIONS.find { it.first == event.gong }?.second ?: "Kein Sound"

                ExposedDropdownMenuBox(
                    expanded = gongDropdownExpanded,
                    onExpandedChange = { gongDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = currentLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sound") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gongDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = gongDropdownExpanded,
                        onDismissRequest = { gongDropdownExpanded = false }
                    ) {
                        GONG_OPTIONS.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.update(event.copy(gong = id))
                                    gongDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // --- TimePlayback toggle ---
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
                    checked = event.timePlayback,
                    onCheckedChange = { viewModel.update(event.copy(timePlayback = it)) }
                )
            }

            // --- Message ---
            OutlinedTextField(
                value = event.message,
                onValueChange = { if (it.length <= 300) viewModel.update(event.copy(message = it)) },
                label = { Text("Nachricht") },
                placeholder = { Text("Optionale Sprachnachricht…") },
                minLines = 3,
                maxLines = 6,
                supportingText = { Text("${event.message.length}/300") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default
                )
            )

            // --- Preview ---
            OutlinedButton(
                onClick = { viewModel.playNow(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("▶  Jetzt abspielen")
            }

            HorizontalDivider()

            // --- Actions: Save / Duplicate / Delete ---
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.weight(1f)
                ) { Text("Speichern") }

                OutlinedButton(
                    onClick = {
                        viewModel.duplicate()
                        onSaved()   // navigate back after duplication
                    },
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
