package com.photoswipe.app.storage

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.documentfile.provider.DocumentFile
import com.photoswipe.app.model.FolderConfig
import com.photoswipe.app.model.PhotoItem
import com.photoswipe.app.model.SwipeDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafStorage(
    private val context: Context,
    override val config: FolderConfig,
    private val sourceUri: Uri,
    private val destinationUris: Map<SwipeDirection, Uri>,
) : Storage {
    private val resolver: ContentResolver = context.contentResolver
    private val photoUris = mutableMapOf<String, Uri>()

    override suspend fun loadPhotos(): List<PhotoItem> = withContext(Dispatchers.IO) {
        photoUris.clear()
        val docFile = DocumentFile.fromTreeUri(context, sourceUri) ?: return@withContext emptyList()
        docFile.listFiles()
            .filter { it.isFile && (it.type?.startsWith("image/") == true) }
            .sortedBy { it.lastModified() }
            .map { file ->
                val docId = DocumentsContract.getDocumentId(file.uri)
                photoUris[docId] = file.uri
                PhotoItem(
                    id = docId,
                    name = file.name ?: "unknown",
                    mimeType = file.type ?: "image/*"
                )
            }
    }

    override suspend fun loadImage(photo: PhotoItem): ImageBitmap? = withContext(Dispatchers.IO) {
        val uri = photoUris[photo.id] ?: return@withContext null
        try {
            // Probe dimensions first, then decode subsampled to avoid OOM on 12 MP+ photos.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (bounds.outWidth / sample > MAX_DECODE_DIM ||
                bounds.outHeight / sample > MAX_DECODE_DIM
            ) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun movePhoto(photo: PhotoItem, direction: SwipeDirection): Boolean =
        withContext(Dispatchers.IO) {
            val photoUri = photoUris[photo.id] ?: return@withContext false
            val destTreeUri = destinationUris[direction] ?: return@withContext false
            val newUri = doMove(photoUri, sourceUri, destTreeUri, photo) ?: return@withContext false
            photoUris[photo.id] = newUri
            true
        }

    override suspend fun undoMove(photo: PhotoItem, direction: SwipeDirection): PhotoItem? =
        withContext(Dispatchers.IO) {
            val destTreeUri = destinationUris[direction] ?: return@withContext null
            val currentUri = photoUris[photo.id] ?: return@withContext null
            val newUri = doMove(currentUri, destTreeUri, sourceUri, photo) ?: return@withContext null
            val newId = DocumentsContract.getDocumentId(newUri)
            PhotoItem(id = newId, name = photo.name, mimeType = photo.mimeType)
        }

    private fun doMove(photoUri: Uri, fromTree: Uri, toTree: Uri, photo: PhotoItem): Uri? {
        val fromDocId = DocumentsContract.getTreeDocumentId(fromTree)
        val fromParentUri = DocumentsContract.buildDocumentUriUsingTree(fromTree, fromDocId)
        val toDocId = DocumentsContract.getTreeDocumentId(toTree)
        val toParentUri = DocumentsContract.buildDocumentUriUsingTree(toTree, toDocId)
        return try {
            DocumentsContract.moveDocument(resolver, photoUri, fromParentUri, toParentUri)
        } catch (e: Exception) {
            copyAndDelete(photoUri, toTree, photo)
        }
    }

    private fun copyAndDelete(photoUri: Uri, toTree: Uri, photo: PhotoItem): Uri? {
        val destDir = DocumentFile.fromTreeUri(context, toTree) ?: return null
        val destFile = destDir.createFile(photo.mimeType, photo.name.substringBeforeLast(".")) ?: return null
        return try {
            resolver.openInputStream(photoUri)?.use { input ->
                resolver.openOutputStream(destFile.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
            DocumentsContract.deleteDocument(resolver, photoUri)
            destFile.uri
        } catch (e: Exception) {
            destFile.delete()
            null
        }
    }

    private companion object {
        const val MAX_DECODE_DIM = 2048
    }
}
