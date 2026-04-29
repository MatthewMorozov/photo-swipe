package com.photoswipe.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photoswipe.app.model.SwipeDirection

@Composable
fun SetupScreenScaffold(
    sourceName: String?,
    destinations: Map<SwipeDirection, String>,
    canStart: Boolean,
    onPickSource: () -> Unit,
    onPickDestination: (SwipeDirection) -> Unit,
    onClearDestination: (SwipeDirection) -> Unit,
    onStart: () -> Unit,
    title: String = "Photo Swipe",
    subtitle: String = "Set up your source folder and assign destination folders to swipe directions.",
    startButtonLabel: String = "Start Sorting",
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Text(
            text = "Folder Setup",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Tap the center to set your source folder. Assign destinations to swipe directions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )

        DirectionGrid(
            destinations = destinations,
            sourceName = sourceName,
            onPickSource = onPickSource,
            onPickFolder = onPickDestination,
            onClearFolder = onClearDestination
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onStart,
            enabled = canStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(startButtonLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun DirectionGrid(
    destinations: Map<SwipeDirection, String>,
    sourceName: String?,
    onPickSource: () -> Unit,
    onPickFolder: (SwipeDirection) -> Unit,
    onClearFolder: (SwipeDirection) -> Unit
) {
    val rows = listOf(
        listOf(
            Triple(SwipeDirection.UP_LEFT, Icons.Default.NorthWest, Color(0xFFDCE775)),
            Triple(SwipeDirection.UP, Icons.Default.ArrowUpward, Color(0xFF81C784)),
            Triple(SwipeDirection.UP_RIGHT, Icons.Default.NorthEast, Color(0xFF4DB6AC))
        ),
        listOf(
            Triple(SwipeDirection.LEFT, Icons.AutoMirrored.Filled.ArrowBack, Color(0xFFFFB74D)),
            null,
            Triple(SwipeDirection.RIGHT, Icons.AutoMirrored.Filled.ArrowForward, Color(0xFFCE93D8))
        ),
        listOf(
            Triple(SwipeDirection.DOWN_LEFT, Icons.Default.SouthWest, Color(0xFFFFD54F)),
            Triple(SwipeDirection.DOWN, Icons.Default.ArrowDownward, Color(0xFF64B5F6)),
            Triple(SwipeDirection.DOWN_RIGHT, Icons.Default.SouthEast, Color(0xFF4DD0E1))
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { item ->
                    if (item == null) {
                        SourceCell(
                            sourceName = sourceName,
                            onPick = onPickSource,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        val (direction, icon, color) = item
                        DirectionCell(
                            direction = direction,
                            icon = icon,
                            color = color,
                            destName = destinations[direction],
                            onPick = { onPickFolder(direction) },
                            onClear = { onClearFolder(direction) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCell(
    sourceName: String?,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (sourceName != null) color.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (sourceName != null) 2.dp else 1.dp,
                color = if (sourceName != null) color
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = if (sourceName != null) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sourceName ?: "Source",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (sourceName != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (sourceName != null) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DirectionCell(
    direction: SwipeDirection,
    icon: ImageVector,
    color: Color,
    destName: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (destName != null) color.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onPick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destName != null) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = destName ?: direction.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (destName != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        if (destName != null) {
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear ${direction.label}",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
