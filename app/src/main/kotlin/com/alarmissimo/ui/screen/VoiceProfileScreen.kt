package com.alarmissimo.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alarmissimo.ui.viewmodel.VoiceProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceProfileScreen(
    viewModel: VoiceProfileViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val profile = uiState.profile
    var showDeleteDialog by remember { mutableStateOf(false) }
    var previewText by rememberSaveable { mutableStateOf("Es ist acht Uhr dreißig.") }

    LaunchedEffect(Unit) {
        viewModel.loadEnginesAndVoices(context)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateUp()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile.name.ifEmpty { "Stimmprofil" }) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.saveAndClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (profile.id != 0L) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            // Name
            OutlinedTextField(
                value = profile.name,
                onValueChange = { viewModel.update(profile.copy(name = it)) },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = profile.id != 0L,
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            // Speech Rate
            SliderRow(
                label = "Geschwindigkeit",
                value = profile.speechRate,
                valueRange = 0.5f..2.0f,
                displayValue = "%.1f".format(profile.speechRate),
                onValueChange = { viewModel.update(profile.copy(speechRate = it)) },
                enabled = profile.id != 0L
            )

            // Pitch
            SliderRow(
                label = "Tonhöhe",
                value = profile.pitch,
                valueRange = 0.5f..2.0f,
                displayValue = "%.1f".format(profile.pitch),
                onValueChange = { viewModel.update(profile.copy(pitch = it)) },
                enabled = profile.id != 0L
            )

            // Pan
            SliderRow(
                label = "Balance",
                value = profile.pan,
                valueRange = -1.0f..1.0f,
                displayValue = when {
                    profile.pan < -0.05f -> "L %.1f".format(-profile.pan)
                    profile.pan > 0.05f  -> "R %.1f".format(profile.pan)
                    else                 -> "Mitte"
                },
                onValueChange = { viewModel.update(profile.copy(pan = it)) },
                enabled = profile.id != 0L
            )

            // Language dropdown
            LanguageDropdown(
                selected = profile.language,
                onSelected = { viewModel.update(profile.copy(language = it, voiceName = null)) },
                enabled = profile.id != 0L
            )

            Spacer(Modifier.height(8.dp))

            // Engine dropdown
            if (uiState.availableEngines.isNotEmpty()) {
                var engineExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = engineExpanded,
                    onExpandedChange = { if (profile.id != 0L) engineExpanded = it }
                ) {
                    OutlinedTextField(
                        value = uiState.availableEngines.find { it.packageName == profile.enginePackage }?.label ?: "Systemstandard",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("TTS-Engine") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = engineExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        enabled = profile.id != 0L
                    )
                    ExposedDropdownMenu(expanded = engineExpanded, onDismissRequest = { engineExpanded = false }) {
                        DropdownMenuItem(text = { Text("Systemstandard") }, onClick = {
                            viewModel.update(profile.copy(enginePackage = null, voiceName = null))
                            engineExpanded = false
                        })
                        uiState.availableEngines.forEach { engine ->
                            DropdownMenuItem(text = { Text(engine.label) }, onClick = {
                                viewModel.update(profile.copy(enginePackage = engine.packageName, voiceName = null))
                                engineExpanded = false
                                viewModel.loadEnginesAndVoices(context)
                            })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Voice dropdown
            if (uiState.availableVoices.isNotEmpty()) {
                val filteredVoices = if (profile.language == "system") uiState.availableVoices
                else uiState.availableVoices.filter { it.languageTag.startsWith(profile.language.take(2)) }
                var voiceExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = voiceExpanded,
                    onExpandedChange = { if (profile.id != 0L) voiceExpanded = it }
                ) {
                    OutlinedTextField(
                        value = filteredVoices.find { it.name == profile.voiceName }?.displayLabel ?: "Stimme wählen …",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Stimme") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        enabled = profile.id != 0L
                    )
                    ExposedDropdownMenu(expanded = voiceExpanded, onDismissRequest = { voiceExpanded = false }) {
                        DropdownMenuItem(text = { Text("(Engine-Standard)") }, onClick = {
                            viewModel.update(profile.copy(voiceName = null))
                            voiceExpanded = false
                        })
                        filteredVoices.forEach { v ->
                            DropdownMenuItem(text = { Text(v.displayLabel) }, onClick = {
                                viewModel.update(profile.copy(voiceName = v.name))
                                voiceExpanded = false
                            })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Preview
            OutlinedTextField(
                value = previewText,
                onValueChange = { previewText = it },
                label = { Text("Vorschautext") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (uiState.isPlaying) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    } else {
                        IconButton(onClick = { viewModel.playNow(context, previewText) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Abspielen")
                        }
                    }
                }
            )

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Stimmprofil löschen") },
            text = { Text("${profile.name} wirklich löschen?") },
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
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(text = label, modifier = Modifier.weight(1.2f))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(2.5f),
            enabled = enabled
        )
        Text(text = displayValue, modifier = Modifier.weight(0.8f).padding(start = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    selected: String,
    onSelected: (String) -> Unit,
    enabled: Boolean = true
) {
    val options = listOf(
        "system" to "Systemsprache",
        "de-DE"  to "Deutsch (Deutschland)",
        "de-AT"  to "Deutsch (Österreich)",
        "de-CH"  to "Deutsch (Schweiz)",
        "en-US"  to "English (US)",
        "en-GB"  to "English (UK)",
        "fr-FR"  to "Français",
        "es-ES"  to "Español",
        "it-IT"  to "Italiano",
        "pt-BR"  to "Português (Brasil)",
        "nl-NL"  to "Nederlands",
        "pl-PL"  to "Polski",
        "ru-RU"  to "Русский",
        "ja-JP"  to "日本語",
        "zh-CN"  to "中文 (简体)"
    )
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = options.find { it.first == selected }?.second ?: selected
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Sprache") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            enabled = enabled
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (tag, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    onSelected(tag)
                    expanded = false
                })
            }
        }
    }
}
