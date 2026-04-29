package com.photoswipe.app.ui.setup

import android.content.Context
import android.net.Uri
import android.util.Log
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

private fun tryTakePersistablePermission(context: Context, uri: Uri): Boolean = try {
    context.contentResolver.takePersistableUriPermission(
        uri,
        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    )
    true
} catch (e: SecurityException) {
    // Some DocumentsProviders return URIs without FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
    // which causes takePersistableUriPermission to throw. Skip the folder rather than crash.
    Log.w("PhotoSwipe", "Could not take persistable permission for $uri", e)
    false
}

private fun safeFolderName(context: Context, uri: Uri): String = try {
    DocumentFile.fromTreeUri(context, uri)?.name
        ?: uri.lastPathSegment ?: "Selected folder"
} catch (e: Exception) {
    Log.w("PhotoSwipe", "Could not resolve folder name for $uri", e)
    uri.lastPathSegment ?: "Selected folder"
}

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
        if (uri != null && tryTakePersistablePermission(context, uri)) {
            sourceUri = uri
            sourceName = safeFolderName(context, uri)
        }
    }

    val destLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val direction = pickingFor
        if (uri != null && direction != null && tryTakePersistablePermission(context, uri)) {
            val name = safeFolderName(context, uri)
            destinations = destinations + (direction to PickedDestination(uri, name))
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
