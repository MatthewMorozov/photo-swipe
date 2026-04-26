package com.photoswipe.app.storage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.photoswipe.app.model.DestinationFolder
import com.photoswipe.app.model.FolderConfig
import com.photoswipe.app.model.PhotoItem
import com.photoswipe.app.model.SwipeDirection

class MockStorage : Storage {
    override val config: FolderConfig = FolderConfig(
        sourceName = "Demo Photos",
        destinations = mapOf(
            SwipeDirection.LEFT to DestinationFolder("Trash"),
            SwipeDirection.RIGHT to DestinationFolder("Keep"),
            SwipeDirection.UP to DestinationFolder("Favorites"),
            SwipeDirection.DOWN to DestinationFolder("Later"),
        )
    )

    private val allPhotos: List<MockPhoto> = generateMockPhotos(15)
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

private fun generateMockPhotos(n: Int): List<MockPhoto> {
    val palette = listOf(
        Color(0xFFEF5350) to Color(0xFFFFCDD2),
        Color(0xFFAB47BC) to Color(0xFFE1BEE7),
        Color(0xFF5C6BC0) to Color(0xFFC5CAE9),
        Color(0xFF26A69A) to Color(0xFFB2DFDB),
        Color(0xFF66BB6A) to Color(0xFFC8E6C9),
        Color(0xFFFFCA28) to Color(0xFFFFF9C4),
        Color(0xFFFF7043) to Color(0xFFFFCCBC),
        Color(0xFF8D6E63) to Color(0xFFD7CCC8),
        Color(0xFF42A5F5) to Color(0xFFBBDEFB),
        Color(0xFFEC407A) to Color(0xFFF8BBD0),
    )
    return (1..n).map { i ->
        val (a, b) = palette[(i - 1) % palette.size]
        val bm = generateGradientBitmap(800, 1000, a, b, accentSeed = i)
        MockPhoto(
            id = "demo-$i",
            name = "demo_${i.toString().padStart(2, '0')}.png",
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
        // A few accent shapes so each photo looks visually distinct
        val rng = SimpleRng(accentSeed.toLong() * 7919L)
        repeat(4 + (accentSeed % 3)) {
            val cx = rng.nextFloat() * w
            val cy = rng.nextFloat() * h
            val radius = 40f + rng.nextFloat() * 120f
            drawCircle(
                color = top.copy(alpha = 0.35f),
                radius = radius,
                center = Offset(cx, cy)
            )
        }
        // Big number-like bar so the user can tell photos apart
        val barH = h / 6f
        drawRect(
            color = bottom.copy(alpha = 0.6f),
            topLeft = Offset(w * 0.1f, h * 0.45f - barH / 2f),
            size = Size(w * 0.8f * (0.3f + (accentSeed % 7) / 10f), barH)
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
