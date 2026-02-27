package com.alarmissimo.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alarmissimo.ui.viewmodel.DashboardViewModel
import com.alarmissimo.ui.viewmodel.UpcomingAlarm
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Dashboard screen — shows all alarm-events firing in the next 24 hours.
 *
 * @param viewModel The dashboard view-model.
 * @param onNavigateToConfig Called when the gear button is tapped.
 * @param onNavigateToAlarmEventEditor Called when the pencil icon on an item is tapped.
 *   receives (alarmSetId, alarmEventId).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToConfig: () -> Unit,
    onNavigateToAlarmEventEditor: (Long, Long) -> Unit
) {
    val upcomingAlarms by viewModel.upcomingAlarms.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alarmissimo") },
                actions = {
                    IconButton(onClick = onNavigateToConfig) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Konfiguration"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (upcomingAlarms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Keine Alarme in den nächsten 24 Stunden.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(upcomingAlarms, key = { it.alarmEvent.id }) { upcoming ->
                    UpcomingAlarmItem(
                        upcoming = upcoming,
                        onEditClick = {
                            onNavigateToAlarmEventEditor(
                                upcoming.alarmSet.id,
                                upcoming.alarmEvent.id
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * A single row in the dashboard list.
 *
 * @param upcoming The upcoming alarm data.
 * @param onEditClick Called when the pencil icon is tapped.
 */
@Composable
private fun UpcomingAlarmItem(
    upcoming: UpcomingAlarm,
    onEditClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: three lines
        Column(modifier = Modifier.weight(1f)) {
            // Line 1: Alarm-set name
            Text(
                text = upcoming.alarmSet.name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Line 2: Alarm time
            Text(
                text = upcoming.alarmEvent.time,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            // Line 3: Message (grey, truncated)
            if (upcoming.alarmEvent.message.isNotBlank()) {
                Text(
                    text = upcoming.alarmEvent.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right column: remaining time
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatRemaining(upcoming.triggerMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Pencil icon
        IconButton(onClick = onEditClick) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Bearbeiten",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Formats the remaining time until [triggerMillis] as "X:MM h" or "N min".
 */
private fun formatRemaining(triggerMillis: Long): String {
    val diff = triggerMillis - System.currentTimeMillis()
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(abs(diff)).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "%d:%02d h".format(hours, minutes)
    } else {
        "%d min".format(minutes.coerceAtLeast(1))
    }
}
