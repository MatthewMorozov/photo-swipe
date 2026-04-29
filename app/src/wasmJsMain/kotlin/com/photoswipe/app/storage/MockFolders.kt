package com.photoswipe.app.storage

import androidx.compose.ui.graphics.Color

data class MockFolderTemplate(
    val id: String,
    val name: String,
    val photoCount: Int,
    val palette: List<Pair<Color, Color>>
)

private val vibrantPalette = listOf(
    Color(0xFFEF5350) to Color(0xFFFFCDD2),
    Color(0xFFAB47BC) to Color(0xFFE1BEE7),
    Color(0xFF5C6BC0) to Color(0xFFC5CAE9),
    Color(0xFF26A69A) to Color(0xFFB2DFDB),
    Color(0xFF66BB6A) to Color(0xFFC8E6C9),
    Color(0xFFFFCA28) to Color(0xFFFFF9C4),
    Color(0xFFFF7043) to Color(0xFFFFCCBC),
    Color(0xFFEC407A) to Color(0xFFF8BBD0),
)

private val warmPalette = listOf(
    Color(0xFFFFB74D) to Color(0xFFFFE0B2),
    Color(0xFFFF8A65) to Color(0xFFFFCCBC),
    Color(0xFFE57373) to Color(0xFFFFCDD2),
    Color(0xFFFFD54F) to Color(0xFFFFF59D),
    Color(0xFFA1887F) to Color(0xFFD7CCC8),
    Color(0xFFFF7043) to Color(0xFFFFAB91),
)

private val coolPalette = listOf(
    Color(0xFF42A5F5) to Color(0xFFBBDEFB),
    Color(0xFF7E57C2) to Color(0xFFD1C4E9),
    Color(0xFF26C6DA) to Color(0xFFB2EBF2),
    Color(0xFF78909C) to Color(0xFFCFD8DC),
    Color(0xFF5C6BC0) to Color(0xFFC5CAE9),
)

object MockFolders {
    val sources: List<MockFolderTemplate> = listOf(
        MockFolderTemplate("camera-roll", "Camera Roll", 15, vibrantPalette),
        MockFolderTemplate("vacation", "Vacation 2025", 10, warmPalette),
        MockFolderTemplate("screenshots", "Screenshots", 12, coolPalette),
        MockFolderTemplate("inbox", "Inbox", 6, vibrantPalette),
    )

    val destinations: List<MockFolderTemplate> = listOf(
        destFolder("trash", "Trash"),
        destFolder("keep", "Keep"),
        destFolder("favorites", "Favorites"),
        destFolder("later", "Later"),
        destFolder("family", "Family"),
        destFolder("work", "Work"),
        destFolder("memes", "Memes"),
        destFolder("to-edit", "To Edit"),
    )

    private fun destFolder(id: String, name: String) =
        MockFolderTemplate(id, name, photoCount = 0, palette = emptyList())
}
