package com.photoswipe.app.ui.setup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import com.photoswipe.app.model.DestinationFolder
import com.photoswipe.app.model.FolderConfig
import com.photoswipe.app.model.SwipeDirection
import com.photoswipe.app.storage.SafStorage
import com.photoswipe.app.storage.Storage

private data class PickedDestination(val uri: Uri, val name: String)

@Composable
fun SetupScreen(onStart: (Storage) -> Unit) {
    val context = LocalContext.current

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var destinations by remember {
        mutableStateOf(mapOf<SwipeDirection, PickedDestination>())
    }

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
            sourceName = DocumentFile.fromTreeUri(context, it)?.name
                ?: it.lastPathSegment ?: "Selected folder"
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
                destinations = destinations + (direction to PickedDestination(treeUri, name))
            }
        }
        pickingFor = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SetupScreenScaffold(
            sourceName = sourceName.ifEmpty { null },
            destinations = destinations.mapValues { it.value.name },
            canStart = sourceUri != null && destinations.isNotEmpty(),
            onPickSource = { sourceLauncher.launch(null) },
            onPickDestination = { direction ->
                pickingFor = direction
                destLauncher.launch(null)
            },
            onClearDestination = { direction ->
                destinations = destinations - direction
            },
            onStart = {
                val src = sourceUri ?: return@SetupScreenScaffold
                val config = FolderConfig(
                    sourceName = sourceName,
                    destinations = destinations.mapValues { DestinationFolder(it.value.name) }
                )
                onStart(
                    SafStorage(
                        context = context.applicationContext,
                        config = config,
                        sourceUri = src,
                        destinationUris = destinations.mapValues { it.value.uri }
                    )
                )
            }
        )
    }
}
