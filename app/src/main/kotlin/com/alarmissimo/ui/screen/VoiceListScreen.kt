package com.alarmissimo.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alarmissimo.data.model.VoiceProfile
import com.alarmissimo.ui.viewmodel.VoiceListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceListScreen(
    viewModel: VoiceListViewModel,
    onNavigateToProfile: (Long) -> Unit,
    onNavigateUp: () -> Unit
) {
    val profiles by viewModel.profiles.collectAsState()
    var deleteCandidate by remember { mutableStateOf<VoiceProfile?>(null) }

    LaunchedEffect(Unit) {
        viewModel.navigateToProfile.collect { id ->
            onNavigateToProfile(id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stimmprofile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.addProfile() }) {
                Icon(Icons.Filled.Add, contentDescription = "Neues Stimmprofil")
            }
        }
    ) { innerPadding ->
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Keine Stimmprofile vorhanden.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(innerPadding)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ListItem(
                        headlineContent = { Text(profile.name.ifEmpty { "(ohne Name)" }) },
                        supportingContent = {
                            val lang = if (profile.language == "system") "Systemsprache"
                                       else profile.language
                            Text("Geschwindigkeit ${"%.1f".format(profile.speechRate)} · Ton $lang")
                        },
                        trailingContent = {
                            // Duplicate always available; delete only for user profiles (id > 0)
                            androidx.compose.foundation.layout.Row {
                                IconButton(onClick = { viewModel.duplicateProfile(profile.id) }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Kopieren")
                                }
                                if (profile.id != 0L) {
                                    IconButton(onClick = { deleteCandidate = profile }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { onNavigateToProfile(profile.id) }
                    )
                }
            }
        }
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Stimmprofil löschen") },
            text = { Text("${candidate.name} wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(candidate.id)
                    deleteCandidate = null
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Abbrechen") }
            }
        )
    }
}

