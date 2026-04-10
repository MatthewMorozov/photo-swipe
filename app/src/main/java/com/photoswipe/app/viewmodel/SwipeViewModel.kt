package com.photoswipe.app.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photoswipe.app.model.FolderConfig
import com.photoswipe.app.model.PhotoItem
import com.photoswipe.app.model.SwipeDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SwipeState(
    val photos: List<PhotoItem> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val processedCount: Int = 0,
    val lastAction: LastAction? = null
)

data class LastAction(
    val photo: PhotoItem,
    val direction: SwipeDirection,
    val originalParentUri: Uri
)

class SwipeViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SwipeState())
    val state: StateFlow<SwipeState> = _state.asStateFlow()

    private lateinit var config: FolderConfig

    fun initialize(folderConfig: FolderConfig) {
        config = folderConfig
        loadPhotos()
    }

    private fun loadPhotos() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val photos = withContext(Dispatchers.IO) {
                    loadPhotosFromFolder(config.sourceUri)
                }
                _state.value = _state.value.copy(
                    photos = photos,
                    currentIndex = 0,
                    isLoading = false,
                    processedCount = 0
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load photos: ${e.message}"
                )
            }
        }
    }

    private fun loadPhotosFromFolder(treeUri: Uri): List<PhotoItem> {
        val context = getApplication<Application>()
        val docFile = DocumentFile.fromTreeUri(context, treeUri)
            ?: return emptyList()

        return docFile.listFiles()
            .filter { it.isFile && (it.type?.startsWith("image/") == true) }
            .map { file ->
                val docId = DocumentsContract.getDocumentId(file.uri)
                PhotoItem(
                    uri = file.uri,
                    documentId = docId,
                    name = file.name ?: "unknown",
                    dateModified = file.lastModified(),
                    mimeType = file.type ?: "image/*"
                )
            }
            .sortedBy { it.dateModified }
    }

    fun swipePhoto(direction: SwipeDirection) {
        val state = _state.value
        val photo = state.photos.getOrNull(state.currentIndex) ?: return
        val destination = config.destinations[direction] ?: return

        viewModelScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    movePhoto(photo, destination.uri)
                }
                if (success) {
                    val lastAction = LastAction(
                        photo = photo,
                        direction = direction,
                        originalParentUri = config.sourceUri
                    )
                    _state.value = _state.value.copy(
                        currentIndex = state.currentIndex + 1,
                        processedCount = state.processedCount + 1,
                        lastAction = lastAction,
                        errorMessage = null
                    )
                } else {
                    _state.value = _state.value.copy(
                        errorMessage = "Failed to move ${photo.name}"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = "Error: ${e.message}"
                )
            }
        }
    }

    private fun movePhoto(photo: PhotoItem, destinationTreeUri: Uri): Boolean {
        val context = getApplication<Application>()
        val resolver: ContentResolver = context.contentResolver

        // Build the source parent document URI from the tree URI
        val sourceTreeUri = config.sourceUri
        val sourceParentDocId = DocumentsContract.getTreeDocumentId(sourceTreeUri)
        val sourceParentDocUri = DocumentsContract.buildDocumentUriUsingTree(
            sourceTreeUri, sourceParentDocId
        )

        // Build the destination parent document URI from its tree URI
        val destDocId = DocumentsContract.getTreeDocumentId(destinationTreeUri)
        val destParentDocUri = DocumentsContract.buildDocumentUriUsingTree(
            destinationTreeUri, destDocId
        )

        return try {
            // Try atomic move first (requires same provider)
            val result = DocumentsContract.moveDocument(
                resolver,
                photo.uri,
                sourceParentDocUri,
                destParentDocUri
            )
            result != null
        } catch (e: Exception) {
            // Fall back to copy + delete
            copyAndDelete(photo, destinationTreeUri)
        }
    }

    private fun copyAndDelete(photo: PhotoItem, destinationTreeUri: Uri): Boolean {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        val destDir = DocumentFile.fromTreeUri(context, destinationTreeUri) ?: return false

        val destFile = destDir.createFile(photo.mimeType, photo.name.substringBeforeLast("."))
            ?: return false

        return try {
            resolver.openInputStream(photo.uri)?.use { input ->
                resolver.openOutputStream(destFile.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
            // Delete original
            DocumentsContract.deleteDocument(resolver, photo.uri)
        } catch (e: Exception) {
            destFile.delete()
            false
        }
    }

    fun undoLastAction() {
        val lastAction = _state.value.lastAction ?: return
        val state = _state.value

        viewModelScope.launch {
            try {
                // We need to find the moved file in its destination
                val direction = lastAction.direction
                val destinationTreeUri = config.destinations[direction]?.uri ?: return@launch

                val movedFileUri = withContext(Dispatchers.IO) {
                    findFileInFolder(destinationTreeUri, lastAction.photo.name)
                } ?: return@launch

                val movedPhoto = lastAction.photo.copy(uri = movedFileUri)
                val success = withContext(Dispatchers.IO) {
                    movePhoto(movedPhoto, config.sourceUri)
                }

                if (success) {
                    // Reload photos to reflect the restored state
                    val photos = withContext(Dispatchers.IO) {
                        loadPhotosFromFolder(config.sourceUri)
                    }
                    _state.value = _state.value.copy(
                        photos = photos,
                        currentIndex = (state.currentIndex - 1).coerceAtLeast(0),
                        processedCount = (state.processedCount - 1).coerceAtLeast(0),
                        lastAction = null
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = "Undo failed: ${e.message}"
                )
            }
        }
    }

    private fun findFileInFolder(treeUri: Uri, name: String): Uri? {
        val context = getApplication<Application>()
        val docFile = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        return docFile.listFiles().firstOrNull { it.name == name }?.uri
    }

    fun dismissError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
