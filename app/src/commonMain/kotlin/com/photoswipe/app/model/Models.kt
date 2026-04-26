package com.photoswipe.app.model

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
    val sourceName: String,
    val destinations: Map<SwipeDirection, DestinationFolder>
)

data class DestinationFolder(
    val name: String
)

data class PhotoItem(
    val id: String,
    val name: String,
    val mimeType: String
)
