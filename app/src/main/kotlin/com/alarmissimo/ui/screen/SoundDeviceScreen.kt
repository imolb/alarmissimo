package com.alarmissimo.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alarmissimo.data.model.SoundDeviceConfig
import com.alarmissimo.ui.viewmodel.BtDeviceInfo
import com.alarmissimo.ui.viewmodel.SoundDeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundDeviceScreen(
    viewModel: SoundDeviceViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val config = uiState.config

    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.loadBondedDevices(context) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            viewModel.loadBondedDevices(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Soundgerät") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
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

            // ── Bluetooth Warning section ────────────────────────────────────
            Text("Bluetooth-Warnung", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Warnung aktivieren", modifier = Modifier.weight(1f))
                Switch(
                    checked = config.btWarningEnabled,
                    onCheckedChange = {
                        val updated = config.copy(btWarningEnabled = it)
                        viewModel.update(updated)
                        viewModel.save()
                    }
                )
            }

            if (config.btWarningEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Vorlaufzeit: ", modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val v = (config.btWarningAheadMinutes - 1).coerceAtLeast(1)
                        viewModel.update(config.copy(btWarningAheadMinutes = v)); viewModel.save()
                    }) { Icon(Icons.Filled.Remove, contentDescription = "Weniger") }
                    Text(
                        "${config.btWarningAheadMinutes} min",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.widthIn(min = 48.dp)
                    )
                    IconButton(onClick = {
                        val v = (config.btWarningAheadMinutes + 1).coerceAtMost(60)
                        viewModel.update(config.copy(btWarningAheadMinutes = v)); viewModel.save()
                    }) { Icon(Icons.Filled.Add, contentDescription = "Mehr") }
                }

                Spacer(Modifier.height(8.dp))
                Text("Akzeptierte Geräte", style = MaterialTheme.typography.bodyMedium)

                config.acceptedDevices.forEach { device ->
                    val isBonded = uiState.bondedDevices.any { it.mac == device.mac }
                    ListItem(
                        headlineContent = { Text(device.name) },
                        supportingContent = {
                            Text(if (isBonded) device.mac else "${device.mac} (nicht mehr gekoppelt)")
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.removeAcceptedDevice(device.mac) }) {
                                Icon(Icons.Filled.Remove, contentDescription = "Entfernen")
                            }
                        }
                    )
                }

                if (!uiState.hasBluetoothPermission) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bluetooth-Berechtigung erforderlich.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    }) {
                        Text("Berechtigung erteilen")
                    }
                } else if (uiState.bondedDevices.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Gerät hinzufügen:", style = MaterialTheme.typography.bodySmall)
                    uiState.bondedDevices
                        .filter { d -> config.acceptedDevices.none { it.mac == d.mac } }
                        .forEach { device ->
                            ListItem(
                                headlineContent = { Text(device.name) },
                                supportingContent = {
                                    Text(if (device.isCurrentlyConnected) "Verbunden" else device.mac)
                                },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.addAcceptedDevice(device) }) {
                                        Icon(Icons.Filled.Add, contentDescription = "Hinzufügen")
                                    }
                                }
                            )
                        }
                }
            }

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            // ── Bluetooth Keep-Alive section ─────────────────────────────────
            Text("Verbindung aufrechterhalten", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Keep-Alive aktivieren", modifier = Modifier.weight(1f))
                Switch(
                    checked = config.btKeepAliveEnabled,
                    onCheckedChange = {
                        val updated = config.copy(btKeepAliveEnabled = it)
                        viewModel.update(updated)
                        viewModel.save()
                    }
                )
            }

            if (config.btKeepAliveEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Intervall: ", modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val v = (config.btKeepAliveIntervalMinutes - 1).coerceAtLeast(1)
                        viewModel.update(config.copy(btKeepAliveIntervalMinutes = v)); viewModel.save()
                    }) { Icon(Icons.Filled.Remove, contentDescription = "Weniger") }
                    Text(
                        "${config.btKeepAliveIntervalMinutes} min",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.widthIn(min = 48.dp)
                    )
                    IconButton(onClick = {
                        val v = (config.btKeepAliveIntervalMinutes + 1).coerceAtMost(30)
                        viewModel.update(config.copy(btKeepAliveIntervalMinutes = v)); viewModel.save()
                    }) { Icon(Icons.Filled.Add, contentDescription = "Mehr") }
                }
            }

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            // ── Auto-Disconnect section ──────────────────────────────────────
            Text("Auto-Trennung", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Trennt das BT-Gerät, wenn kein Alarm in den nächsten X Minuten geplant ist.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Auto-Trennung aktivieren", modifier = Modifier.weight(1f))
                Switch(
                    checked = config.btAutoDisconnectEnabled,
                    onCheckedChange = {
                        val updated = config.copy(btAutoDisconnectEnabled = it)
                        viewModel.update(updated)
                        viewModel.save()
                    }
                )
            }

            if (config.btAutoDisconnectEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Vorlaufzeit: ", modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val v = (config.btAutoDisconnectAheadMinutes - 1).coerceAtLeast(1)
                        viewModel.update(config.copy(btAutoDisconnectAheadMinutes = v)); viewModel.save()
                    }) { Icon(Icons.Filled.Remove, contentDescription = "Weniger") }
                    Text(
                        "${config.btAutoDisconnectAheadMinutes} min",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.widthIn(min = 48.dp)
                    )
                    IconButton(onClick = {
                        val v = (config.btAutoDisconnectAheadMinutes + 1).coerceAtMost(120)
                        viewModel.update(config.copy(btAutoDisconnectAheadMinutes = v)); viewModel.save()
                    }) { Icon(Icons.Filled.Add, contentDescription = "Mehr") }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
