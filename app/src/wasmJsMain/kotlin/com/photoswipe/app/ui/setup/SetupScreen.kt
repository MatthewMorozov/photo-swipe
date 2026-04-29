package com.photoswipe.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photoswipe.app.model.SwipeDirection
import com.photoswipe.app.storage.MockFolderTemplate
import com.photoswipe.app.storage.MockFolders
import com.photoswipe.app.storage.MockStorage
import com.photoswipe.app.storage.Storage

private sealed interface PickTarget {
    data object Source : PickTarget
    data class Destination(val direction: SwipeDirection) : PickTarget
}

@Composable
fun SetupScreen(onStart: (Storage) -> Unit) {
    var sourceFolder by remember { mutableStateOf<MockFolderTemplate?>(null) }
    var destinations by remember {
        mutableStateOf(mapOf<SwipeDirection, MockFolderTemplate>())
    }
    var pickTarget by remember { mutableStateOf<PickTarget?>(null) }

    SetupScreenScaffold(
        sourceName = sourceFolder?.name,
        destinations = destinations.mapValues { it.value.name },
        canStart = sourceFolder != null && destinations.isNotEmpty(),
        onPickSource = { pickTarget = PickTarget.Source },
        onPickDestination = { dir -> pickTarget = PickTarget.Destination(dir) },
        onClearDestination = { dir -> destinations = destinations - dir },
        onStart = {
            val src = sourceFolder ?: return@SetupScreenScaffold
            onStart(MockStorage(source = src, destinations = destinations))
        },
        subtitle = "Web demo — pick a mock source folder and assign mock destinations to swipe directions. Photos and moves are simulated.",
        startButtonLabel = "Start Demo",
    )

    pickTarget?.let { target ->
        val isSource = target is PickTarget.Source
        val options = if (isSource) MockFolders.sources else MockFolders.destinations
        val title = if (isSource) "Choose source folder" else {
            val dir = (target as PickTarget.Destination).direction
            "Choose folder for ${dir.label}"
        }
        FolderPickerDialog(
            title = title,
            options = options,
            onPick = { folder ->
                when (target) {
                    PickTarget.Source -> sourceFolder = folder
                    is PickTarget.Destination ->
                        destinations = destinations + (target.direction to folder)
                }
                pickTarget = null
            },
            onDismiss = { pickTarget = null }
        )
    }
}

@Composable
private fun FolderPickerDialog(
    title: String,
    options: List<MockFolderTemplate>,
    onPick: (MockFolderTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(folder) }
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            if (folder.photoCount > 0) {
                                Text(
                                    text = "${folder.photoCount} photos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
