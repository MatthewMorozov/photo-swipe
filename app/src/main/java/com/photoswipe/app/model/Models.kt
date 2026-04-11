package com.photoswipe.app.model

import android.net.Uri

enum class SwipeDirection(val label: String) {
    LEFT("Left"),
    RIGHT("Right"),
    UP("Up"),
    DOWN("Down"),
    UP_LEFT("Up-Left"),
    UP_RIGHT("Up-Right"),
    DOWN_LEFT("Down-Left"),
    DOWN_RIGHT("Down-Right")
}

data class FolderConfig(
    val sourceUri: Uri,
    val sourceName: String,
    val destinations: Map<SwipeDirection, DestinationFolder>
)

data class DestinationFolder(
    val uri: Uri,
    val name: String
)

data class PhotoItem(
    val uri: Uri,
    val documentId: String,
    val name: String,
    val dateModified: Long,
    val mimeType: String
)
