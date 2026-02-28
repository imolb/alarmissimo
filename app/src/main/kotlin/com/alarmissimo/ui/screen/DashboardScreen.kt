package com.alarmissimo.ui.screen

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alarmissimo.R
import com.alarmissimo.ui.viewmodel.DashboardViewModel
import com.alarmissimo.ui.viewmodel.UpcomingAlarm

/**
 * Dashboard screen — shows all alarm-events firing in the next 24 hours.
 *
 * @param viewModel The dashboard view-model.
 * @param onNavigateToConfig Called when the gear button is tapped.
 * @param onNavigateToAlarmEventEditor Called when the pencil icon on an item is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToConfig: () -> Unit,
    onNavigateToAlarmEventEditor: (Long, Long) -> Unit
) {
    val upcomingAlarms by viewModel.upcomingAlarms.collectAsState()
    val nowMillis      by viewModel.tickMillis.collectAsState()      // item 21: 1 s tick

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Item 1: icon + app name in the dashboard heading
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.alarmissimo_icon),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Text("Alarmissimo")
                    }
                },
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
                modifier = Modifier.fillMaxSize().padding(padding),
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
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(upcomingAlarms, key = { it.alarmEvent.id }) { upcoming ->
                    UpcomingAlarmItem(
                        upcoming  = upcoming,
                        nowMillis = nowMillis,   // item 21: forces recompose every second
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
 */
@Composable
private fun UpcomingAlarmItem(
    upcoming: UpcomingAlarm,
    nowMillis: Long,
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
            Text(
                text = upcoming.alarmSet.name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = upcoming.alarmEvent.time,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
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

        // Right: remaining time with "in" prefix (items 15 & 16)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatRemaining(upcoming.triggerMillis, nowMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

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
 * Formats the remaining time until [triggerMillis] as "in hh:mm:ss" (items 15, 16 & 21).
 * [nowMillis] is the current time; passing it explicitly ensures recomposition every second.
 */
private fun formatRemaining(triggerMillis: Long, nowMillis: Long): String {
    val diff = triggerMillis - nowMillis
    if (diff <= 0) return "jetzt"
    val totalSeconds = diff / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "in %d:%02d:%02d".format(h, m, s)
}
