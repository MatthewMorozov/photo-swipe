package com.photoswipe.app.storage

import androidx.compose.ui.graphics.ImageBitmap
import com.photoswipe.app.model.FolderConfig
import com.photoswipe.app.model.PhotoItem
import com.photoswipe.app.model.SwipeDirection

interface Storage {
    val config: FolderConfig
    suspend fun loadPhotos(): List<PhotoItem>
    suspend fun loadImage(photo: PhotoItem): ImageBitmap?
    suspend fun movePhoto(photo: PhotoItem, direction: SwipeDirection): Boolean
    suspend fun undoMove(photo: PhotoItem, direction: SwipeDirection): PhotoItem?
}
