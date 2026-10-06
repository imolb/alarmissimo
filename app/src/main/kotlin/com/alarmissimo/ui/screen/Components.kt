package com.alarmissimo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Circular LED-style indicator using M3 theme colors.
 * Enabled → filled with `primary`; disabled → filled with `surfaceVariant` + `outline` border.
 */
@Composable
internal fun LedChip(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fill = if (enabled) MaterialTheme.colorScheme.primary
               else MaterialTheme.colorScheme.surfaceVariant
    val stroke = if (enabled) MaterialTheme.colorScheme.primary
                 else MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .size(22.dp)
            .border(1.5.dp, stroke, CircleShape)
            .clip(CircleShape)
            .background(fill)
            .clickable(onClick = onClick)
    )
}
