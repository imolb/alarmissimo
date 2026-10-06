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
import com.alarmissimo.util.TimeUtils
import com.alarmissimo.ui.viewmodel.DashboardViewModel
import com.alarmissimo.ui.viewmodel.UpcomingAlarm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToConfig: () -> Unit,
    onNavigateToAlarmEventEditor: (Long, Long) -> Unit
) {
    val upcomingAlarms by viewModel.upcomingAlarms.collectAsState()
    val nowMillis      by viewModel.tickMillis.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp)
                        )
                        Text("Alarmissimo")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToConfig) {
                        Icon(Icons.Default.Settings, contentDescription = "Konfiguration")
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
                // Section header
                item {
                    Text(
                        text = "Alarme in den nächsten 24 Stunden",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(upcomingAlarms, key = { it.alarmEvent.id }) { upcoming ->
                    UpcomingAlarmItem(
                        upcoming  = upcoming,
                        nowMillis = nowMillis,
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

@Composable
private fun UpcomingAlarmItem(
    upcoming: UpcomingAlarm,
    nowMillis: Long,
    onEditClick: () -> Unit
) {
    // Always show the computed absolute trigger time
    val triggerTime = TimeUtils.computeAbsoluteTriggerTime(upcoming.alarmSet, upcoming.alarmEvent)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = upcoming.alarmSet.name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Show date for date-specific alarms
            upcoming.alarmSet.specificDate?.let { date ->
                Text(
                    text = TimeUtils.formatDateDisplay(date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = triggerTime,
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

private fun formatRemaining(triggerMillis: Long, nowMillis: Long): String {
    val diff = triggerMillis - nowMillis
    if (diff <= 0) return "jetzt"
    val totalSeconds = diff / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "in %d:%02d:%02d".format(h, m, s)
}
