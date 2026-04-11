package com.photoswipe.app.ui.setup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.photoswipe.app.model.DestinationFolder
import com.photoswipe.app.model.FolderConfig
import com.photoswipe.app.model.SwipeDirection

@Composable
fun SetupScreen(onStart: (FolderConfig) -> Unit) {
    val context = LocalContext.current

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var destinations by remember {
        mutableStateOf(mapOf<SwipeDirection, DestinationFolder>())
    }

    // Tracks which direction we're currently picking a folder for
    var pickingFor by remember { mutableStateOf<SwipeDirection?>(null) }

    val sourceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            sourceUri = it
            sourceName = DocumentFile.fromTreeUri(context, it)?.name ?: it.lastPathSegment ?: "Selected folder"
        }
    }

    val destLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { treeUri ->
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val name = DocumentFile.fromTreeUri(context, treeUri)?.name
                ?: treeUri.lastPathSegment
                ?: "Selected folder"
            pickingFor?.let { direction ->
                destinations = destinations + (direction to DestinationFolder(treeUri, name))
            }
        }
        pickingFor = null
    }

    val canStart = sourceUri != null && destinations.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Photo Swipe",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Set up your source folder and assign destination folders to swipe directions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        // Source folder
        SectionHeader("Source Folder")
        FolderPickerCard(
            label = "Choose source folder",
            selectedName = sourceName.ifEmpty { null },
            icon = Icons.Default.FolderOpen,
            tint = MaterialTheme.colorScheme.primary,
            onClick = { sourceLauncher.launch(null) }
        )

        // Destination folders
        SectionHeader("Destination Folders")
        Text(
            text = "Assign at least one folder to a swipe direction.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )

        DirectionGrid(
            destinations = destinations,
            onPickFolder = { direction ->
                pickingFor = direction
                destLauncher.launch(null)
            },
            onClearFolder = { direction ->
                destinations = destinations - direction
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                val src = sourceUri ?: return@Button
                val name = sourceName
                onStart(FolderConfig(src, name, destinations))
            },
            enabled = canStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Sorting", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun FolderPickerCard(
    label: String,
    selectedName: String?,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (selectedName != null) tint.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selectedName != null) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            if (selectedName != null) {
                Text(
                    text = selectedName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        if (selectedName != null && onClear != null) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DirectionGrid(
    destinations: Map<SwipeDirection, DestinationFolder>,
    onPickFolder: (SwipeDirection) -> Unit,
    onClearFolder: (SwipeDirection) -> Unit
) {
    // 3×3 compass layout; null = centre placeholder
    val rows = listOf(
        listOf(
            Triple(SwipeDirection.UP_LEFT, Icons.Default.NorthWest, Color(0xFFDCE775)),
            Triple(SwipeDirection.UP, Icons.Default.ArrowUpward, Color(0xFF81C784)),
            Triple(SwipeDirection.UP_RIGHT, Icons.Default.NorthEast, Color(0xFF4DB6AC))
        ),
        listOf(
            Triple(SwipeDirection.LEFT, Icons.Default.ArrowBack, Color(0xFFFFB74D)),
            null,
            Triple(SwipeDirection.RIGHT, Icons.Default.ArrowForward, Color(0xFFCE93D8))
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
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val (direction, icon, color) = item
                        DirectionCell(
                            direction = direction,
                            icon = icon,
                            color = color,
                            dest = destinations[direction],
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
private fun DirectionCell(
    direction: SwipeDirection,
    icon: ImageVector,
    color: Color,
    dest: DestinationFolder?,
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
                color = if (dest != null) color.copy(alpha = 0.5f)
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
                tint = if (dest != null) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dest?.name ?: direction.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (dest != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        if (dest != null) {
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
