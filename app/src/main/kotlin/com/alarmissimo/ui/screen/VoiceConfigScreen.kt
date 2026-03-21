package com.alarmissimo.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.alarmissimo.data.model.VoiceConfig
import com.alarmissimo.ui.viewmodel.VoiceConfigViewModel
import kotlin.math.roundToInt

/** Supported language options in the language dropdown. */
private val LANGUAGE_OPTIONS = listOf(
    "de-DE" to "Deutsch (de-DE)",
    "en-US" to "Englisch (en-US)",
    "en-GB" to "Englisch British (en-GB)",
    "fr-FR" to "Französisch (fr-FR)",
    "es-ES" to "Spanisch (es-ES)",
    "it-IT" to "Italienisch (it-IT)",
    "system" to "System-Standard"
)

/**
 * Voice Configuration screen.
 *
 * Exposes all Android [android.speech.tts.TextToSpeech] voice-design parameters:
 * - Speech rate
 * - Pitch
 * - Pan (stereo balance)
 * - Volume
 * - Language / Locale
 * - Voice selection
 * - TTS engine
 *
 * A preview text field + "Jetzt abspielen" button allows live testing of the settings.
 *
 * @param viewModel The voice configuration view-model.
 * @param onNavigateUp Called when the back button is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceConfigScreen(
    viewModel: VoiceConfigViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var previewText by remember { mutableStateOf("Es ist 7 Uhr 30. Guten Morgen!") }

    // Load engines & voices once when screen is first composed
    LaunchedEffect(Unit) {
        viewModel.loadEnginesAndVoices(context)
    }

    // Save config when leaving the screen
    DisposableEffect(Unit) {
        onDispose { viewModel.save() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sprachkonfiguration") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.save(); onNavigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Language & Voice ─────────────────────────────────────────────
            SectionCard(title = "Sprache & Stimme") {
                // 1. Engine dropdown (dynamically populated)
                val engineOptions = buildList {
                    add(null to "System-Standard")
                    uiState.availableEngines.forEach { add(it.packageName to it.label) }
                }
                DropdownRow(
                    label = "TTS-Engine",
                    selectedKey = uiState.config.enginePackage,
                    options = engineOptions,
                    onSelect = { newEngine ->
                        viewModel.update(uiState.config.copy(enginePackage = newEngine, voiceName = null))
                        // Reload voices for the newly selected engine
                        viewModel.loadEnginesAndVoices(context)
                    }
                )

                Spacer(Modifier.height(4.dp))

                // 2. Language dropdown
                DropdownRow(
                    label = "Sprache",
                    selectedKey = uiState.config.language,
                    options = LANGUAGE_OPTIONS,
                    onSelect = { viewModel.update(uiState.config.copy(language = it, voiceName = null)) }
                )

                Spacer(Modifier.height(4.dp))

                // 3. Voice dropdown — filtered to voices whose locale matches the selected language.
                // "system" language means no filtering.
                val filteredVoices = if (uiState.config.language == "system") {
                    uiState.availableVoices
                } else {
                    uiState.availableVoices.filter { v ->
                        v.languageTag.startsWith(uiState.config.language.substringBefore("-"), ignoreCase = true)
                    }
                }
                val voiceOptions = buildList {
                    add(null to "Automatisch (Standard der Engine)")
                    filteredVoices.forEach { add(it.name to it.displayLabel) }
                }
                DropdownRow(
                    label = "Stimme",
                    selectedKey = uiState.config.voiceName,
                    options = voiceOptions,
                    placeholder = if (uiState.availableVoices.isEmpty()) "Stimmen werden geladen …" else null,
                    onSelect = { viewModel.update(uiState.config.copy(voiceName = it)) }
                )
            }

            // ── Speech Parameters ────────────────────────────────────────────
            SectionCard(title = "Sprachparameter") {
                LabeledSlider(
                    label = "Sprechgeschwindigkeit",
                    value = uiState.config.speechRate,
                    valueDisplay = "%.1fx".format(uiState.config.speechRate),
                    valueRange = 0.5f..2.0f,
                    steps = 14,       // 0.5, 0.6, … 2.0  → 15 values, 14 gaps
                    onValueChange = { viewModel.update(uiState.config.copy(speechRate = (it * 10).roundToInt() / 10f)) }
                )

                LabeledSlider(
                    label = "Tonhöhe (Pitch)",
                    value = uiState.config.pitch,
                    valueDisplay = "%.1fx".format(uiState.config.pitch),
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    onValueChange = { viewModel.update(uiState.config.copy(pitch = (it * 10).roundToInt() / 10f)) }
                )

                LabeledSlider(
                    label = "Stereo-Balance (Pan)",
                    value = uiState.config.pan,
                    valueDisplay = when {
                        uiState.config.pan < -0.05f -> "%.1f (Links)".format(uiState.config.pan)
                        uiState.config.pan > 0.05f  -> "+%.1f (Rechts)".format(uiState.config.pan)
                        else                         -> "Mitte"
                    },
                    valueRange = -1.0f..1.0f,
                    steps = 19,       // -1.0, -0.9, … +1.0 → 21 values, 20 gaps, but steps = inner ticks = 19
                    onValueChange = { viewModel.update(uiState.config.copy(pan = (it * 10).roundToInt() / 10f)) }
                )

                LabeledSlider(
                    label = "Lautstärke",
                    value = uiState.config.volume.toFloat(),
                    valueDisplay = "${uiState.config.volume} %",
                    valueRange = 0f..100f,
                    steps = 99,
                    onValueChange = { viewModel.update(uiState.config.copy(volume = it.roundToInt())) }
                )
            }

            // ── Preview ──────────────────────────────────────────────────────
            SectionCard(title = "Vorschau") {
                OutlinedTextField(
                    value = previewText,
                    onValueChange = { previewText = it },
                    label = { Text("Vorschautext") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    )
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.playNow(context, previewText) },
                    enabled = !uiState.isPlaying,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isPlaying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Wird gesprochen …")
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Jetzt abspielen")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Reusable composables ────────────────────────────────────────────────────

/**
 * Card section with a title and arbitrary content.
 */
@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * A slider with a label on the left and a value badge on the right.
 */
@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueDisplay: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * A labelled exposed-dropdown row for selecting from a key→label list.
 *
 * @param selectedKey The currently selected option key (or null for the default).
 * @param options List of (key, displayLabel) pairs. Use null key for "auto/default".
 * @param placeholder Replaces the selected label when it should not be clickable yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <K> DropdownRow(
    label: String,
    selectedKey: K,
    options: List<Pair<K, String>>,
    placeholder: String? = null,
    onSelect: (K) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selectedKey }?.second
        ?: placeholder
        ?: options.firstOrNull()?.second
        ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (placeholder == null) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelect(key); expanded = false }
                )
            }
        }
    }
}
