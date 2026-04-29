package com.photoswipe.app.storage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.photoswipe.app.model.DestinationFolder
import com.photoswipe.app.model.FolderConfig
import com.photoswipe.app.model.PhotoItem
import com.photoswipe.app.model.SwipeDirection

class MockStorage(
    source: MockFolderTemplate,
    destinations: Map<SwipeDirection, MockFolderTemplate>
) : Storage {
    override val config: FolderConfig = FolderConfig(
        sourceName = source.name,
        destinations = destinations.mapValues { DestinationFolder(it.value.name) }
    )

    private val allPhotos: List<MockPhoto> =
        generateMockPhotos(source.id, source.photoCount, source.palette)
    private val moved = mutableMapOf<String, SwipeDirection>()

    override suspend fun loadPhotos(): List<PhotoItem> =
        allPhotos.filter { it.id !in moved.keys }.map { it.toItem() }

    override suspend fun loadImage(photo: PhotoItem): ImageBitmap? =
        allPhotos.firstOrNull { it.id == photo.id }?.bitmap

    override suspend fun movePhoto(photo: PhotoItem, direction: SwipeDirection): Boolean {
        moved[photo.id] = direction
        return true
    }

    override suspend fun undoMove(photo: PhotoItem, direction: SwipeDirection): PhotoItem? {
        moved.remove(photo.id)
        return photo
    }
}

private class MockPhoto(val id: String, val name: String, val bitmap: ImageBitmap) {
    fun toItem() = PhotoItem(id, name, "image/png")
}

private fun generateMockPhotos(
    sourceId: String,
    n: Int,
    palette: List<Pair<Color, Color>>
): List<MockPhoto> {
    if (n == 0 || palette.isEmpty()) return emptyList()
    return (1..n).map { i ->
        val (a, b) = palette[(i - 1) % palette.size]
        val seed = sourceId.hashCode() xor (i * 7919)
        val bm = generateGradientBitmap(800, 1000, a, b, accentSeed = seed)
        MockPhoto(
            id = "$sourceId-$i",
            name = "${sourceId}_${i.toString().padStart(2, '0')}.png",
            bitmap = bm
        )
    }
}

private fun generateGradientBitmap(w: Int, h: Int, top: Color, bottom: Color, accentSeed: Int): ImageBitmap {
    val bitmap = ImageBitmap(w, h)
    val canvas = Canvas(bitmap)
    val drawScope = CanvasDrawScope()
    drawScope.draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(w.toFloat(), h.toFloat())
    ) {
        drawRect(
            brush = Brush.verticalGradient(listOf(top, bottom)),
            size = size
        )
        val rng = SimpleRng(accentSeed.toLong() * 7919L)
        repeat(4 + (accentSeed.mod(3))) {
            val cx = rng.nextFloat() * w
            val cy = rng.nextFloat() * h
            val radius = 40f + rng.nextFloat() * 120f
            drawCircle(
                color = top.copy(alpha = 0.35f),
                radius = radius,
                center = Offset(cx, cy)
            )
        }
        val barH = h / 6f
        drawRect(
            color = bottom.copy(alpha = 0.6f),
            topLeft = Offset(w * 0.1f, h * 0.45f - barH / 2f),
            size = Size(w * 0.8f * (0.3f + accentSeed.mod(7) / 10f), barH)
        )
    }
    return bitmap
}

private class SimpleRng(seed: Long) {
    private var state: Long = seed.takeIf { it != 0L } ?: 0xDEADBEEFL
    fun nextFloat(): Float {
        state = state xor (state shl 13)
        state = state xor (state ushr 7)
        state = state xor (state shl 17)
        val v = (state ushr 8).toInt() and 0x00FFFFFF
        return v / 0x01000000.toFloat()
    }
}
