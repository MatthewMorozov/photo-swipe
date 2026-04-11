package com.photoswipe.app.ui.swipe

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.photoswipe.app.model.FolderConfig
import com.photoswipe.app.model.SwipeDirection
import com.photoswipe.app.viewmodel.SwipeViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt
import androidx.compose.ui.geometry.Offset

private val directionColors = mapOf(
    SwipeDirection.UP to Color(0xFF81C784),
    SwipeDirection.DOWN to Color(0xFF64B5F6),
    SwipeDirection.LEFT to Color(0xFFFFB74D),
    SwipeDirection.RIGHT to Color(0xFFCE93D8),
    SwipeDirection.UP_LEFT to Color(0xFFDCE775),
    SwipeDirection.UP_RIGHT to Color(0xFF4DB6AC),
    SwipeDirection.DOWN_LEFT to Color(0xFFFFD54F),
    SwipeDirection.DOWN_RIGHT to Color(0xFF4DD0E1)
)

private val directionIcons = mapOf(
    SwipeDirection.UP to Icons.Default.ArrowUpward,
    SwipeDirection.DOWN to Icons.Default.ArrowDownward,
    SwipeDirection.LEFT to Icons.Default.ArrowBack,
    SwipeDirection.RIGHT to Icons.Default.ArrowForward,
    SwipeDirection.UP_LEFT to Icons.Default.NorthWest,
    SwipeDirection.UP_RIGHT to Icons.Default.NorthEast,
    SwipeDirection.DOWN_LEFT to Icons.Default.SouthWest,
    SwipeDirection.DOWN_RIGHT to Icons.Default.SouthEast
)

/**
 * Detects which of 8 swipe directions is dominant when drag magnitude exceeds [threshold].
 * Uses 45° sectors: cardinal directions occupy a 90° sector centred on their axis;
 * diagonals fill the 45° gaps between cardinals.
 */
private fun detectSwipeDirection(offsetX: Float, offsetY: Float, threshold: Float): SwipeDirection? {
    val magnitude = sqrt(offsetX * offsetX + offsetY * offsetY)
    if (magnitude < threshold) return null
    // tan(67.5°) ≈ 2.414 separates cardinal (>2.414) from diagonal sectors
    val ratio = if (abs(offsetY) > 0f) abs(offsetX) / abs(offsetY) else Float.MAX_VALUE
    return when {
        ratio > 2.414f -> if (offsetX > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
        ratio < 0.414f -> if (offsetY > 0) SwipeDirection.DOWN else SwipeDirection.UP
        offsetX >= 0 && offsetY <= 0 -> SwipeDirection.UP_RIGHT
        offsetX >= 0 -> SwipeDirection.DOWN_RIGHT
        offsetY <= 0 -> SwipeDirection.UP_LEFT
        else -> SwipeDirection.DOWN_LEFT
    }
}

private fun oppositeOf(direction: SwipeDirection?): SwipeDirection? = when (direction) {
    SwipeDirection.LEFT -> SwipeDirection.RIGHT
    SwipeDirection.RIGHT -> SwipeDirection.LEFT
    SwipeDirection.UP -> SwipeDirection.DOWN
    SwipeDirection.DOWN -> SwipeDirection.UP
    SwipeDirection.UP_LEFT -> SwipeDirection.DOWN_RIGHT
    SwipeDirection.UP_RIGHT -> SwipeDirection.DOWN_LEFT
    SwipeDirection.DOWN_LEFT -> SwipeDirection.UP_RIGHT
    SwipeDirection.DOWN_RIGHT -> SwipeDirection.UP_LEFT
    null -> null
}

@Composable
fun SwipeScreen(
    config: FolderConfig,
    onDone: () -> Unit,
    vm: SwipeViewModel = viewModel()
) {
    LaunchedEffect(config) { vm.initialize(config) }
    val state by vm.state.collectAsState()

    val currentPhoto = state.photos.getOrNull(state.currentIndex)
    val isDone = !state.isLoading && state.currentIndex >= state.photos.size && state.photos.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        when {
            state.isLoading -> LoadingContent()
            isDone -> DoneContent(
                processedCount = state.processedCount,
                totalCount = state.photos.size,
                onNewSession = onDone
            )
            state.photos.isEmpty() -> EmptyContent(sourceName = config.sourceName, onBack = onDone)
            currentPhoto != null -> {
                PhotoSwipeContent(
                    config = config,
                    photo = currentPhoto,
                    currentIndex = state.currentIndex,
                    totalCount = state.photos.size,
                    hasUndo = state.lastAction != null,
                    lastActionDirection = state.lastAction?.direction,
                    onSwipe = { dir -> vm.swipePhoto(dir) },
                    onUndo = { vm.undoLastAction() },
                    onBack = onDone
                )
            }
        }

        // Error snackbar
        state.errorMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { vm.dismissError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(msg)
            }
        }
    }
}

@Composable
private fun PhotoSwipeContent(
    config: FolderConfig,
    photo: com.photoswipe.app.model.PhotoItem,
    currentIndex: Int,
    totalCount: Int,
    hasUndo: Boolean,
    lastActionDirection: SwipeDirection?,
    onSwipe: (SwipeDirection) -> Unit,
    onUndo: () -> Unit,
    onBack: () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }

    // How far to drag before triggering (in pixels)
    val threshold = with(density) { 120.dp.toPx() }
    // How far to fly off screen
    val flyDistance = with(density) { 600.dp.toPx() }

    // Swiping in the opposite direction of the last action undoes it
    val undoDirection: SwipeDirection? = if (hasUndo) oppositeOf(lastActionDirection) else null

    // Active drag direction for overlay highlights
    val activeDrag: SwipeDirection? = detectSwipeDirection(offsetX, offsetY, threshold / 2)

    // Reset animation when photo changes
    LaunchedEffect(currentIndex) {
        animOffsetX.snapTo(0f)
        animOffsetY.snapTo(0f)
        offsetX = 0f
        offsetY = 0f
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Direction overlays (shown when actively dragging toward that direction)
        config.destinations.forEach { (direction, dest) ->
            DirectionOverlay(
                direction = direction,
                label = dest.name,
                isActive = activeDrag == direction,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Undo overlay — shown when dragging opposite to the last moved photo
        if (undoDirection != null) {
            DirectionOverlay(
                direction = undoDirection,
                label = "Undo",
                isActive = activeDrag == undoDirection,
                modifier = Modifier.fillMaxSize(),
                overlayColor = Color(0xFFB0BEC5),
                labelIcon = Icons.Default.Undo
            )
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${currentIndex + 1} / $totalCount",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.weight(1f))
            if (hasUndo) {
                IconButton(onClick = onUndo) {
                    Icon(
                        Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        // Progress bar
        LinearProgressIndicator(
            progress = { (currentIndex.toFloat() / totalCount) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 56.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surface
        )

        // Photo card
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 72.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            val rotation = (animOffsetX.value / flyDistance) * 15f
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = animOffsetX.value
                        translationY = animOffsetY.value
                        rotationZ = rotation
                    }
                    .pointerInput(currentIndex) {
                        detectDragGestures(
                            onDragEnd = {
                                val dominant = detectSwipeDirection(offsetX, offsetY, threshold)

                                fun flyOff(dir: SwipeDirection, then: () -> Unit) {
                                    scope.launch {
                                        val targetX = when (dir) {
                                            SwipeDirection.LEFT, SwipeDirection.UP_LEFT, SwipeDirection.DOWN_LEFT -> -flyDistance
                                            SwipeDirection.RIGHT, SwipeDirection.UP_RIGHT, SwipeDirection.DOWN_RIGHT -> flyDistance
                                            else -> animOffsetX.value
                                        }
                                        val targetY = when (dir) {
                                            SwipeDirection.UP, SwipeDirection.UP_LEFT, SwipeDirection.UP_RIGHT -> -flyDistance
                                            SwipeDirection.DOWN, SwipeDirection.DOWN_LEFT, SwipeDirection.DOWN_RIGHT -> flyDistance
                                            else -> animOffsetY.value
                                        }
                                        launch { animOffsetX.animateTo(targetX, tween(200)) }
                                        animOffsetY.animateTo(targetY, tween(200))
                                        then()
                                    }
                                }

                                when {
                                    dominant != null && dominant == undoDirection ->
                                        flyOff(dominant) { onUndo() }
                                    dominant != null && config.destinations.containsKey(dominant) ->
                                        flyOff(dominant) { onSwipe(dominant) }
                                    else -> scope.launch {
                                        launch { animOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                        animOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                    }
                                }
                                offsetX = 0f
                                offsetY = 0f
                            },
                            onDragCancel = {
                                scope.launch {
                                    launch { animOffsetX.animateTo(0f, spring()) }
                                    animOffsetY.animateTo(0f, spring())
                                }
                                offsetX = 0f
                                offsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                                scope.launch {
                                    animOffsetX.snapTo(animOffsetX.value + dragAmount.x)
                                    animOffsetY.snapTo(animOffsetY.value + dragAmount.y)
                                }
                            }
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photo.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = photo.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom hint labels
        DirectionHints(
            destinations = config.destinations,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        // Photo name
        Text(
            text = photo.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 108.dp)
                .padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun DirectionOverlay(
    direction: SwipeDirection,
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    overlayColor: Color = directionColors[direction] ?: Color.Gray,
    labelIcon: ImageVector = directionIcons[direction] ?: Icons.Default.ArrowForward
) {
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 0.35f else 0f,
        animationSpec = tween(150),
        label = "overlay_alpha"
    )
    val color = overlayColor

    Box(modifier = modifier) {
        // Gradient overlay from the direction edge
        val brush = when (direction) {
            SwipeDirection.LEFT -> Brush.horizontalGradient(listOf(color, Color.Transparent))
            SwipeDirection.RIGHT -> Brush.horizontalGradient(listOf(Color.Transparent, color))
            SwipeDirection.UP -> Brush.verticalGradient(listOf(color, Color.Transparent))
            SwipeDirection.DOWN -> Brush.verticalGradient(listOf(Color.Transparent, color))
            SwipeDirection.UP_LEFT -> Brush.linearGradient(listOf(color, Color.Transparent), start = Offset.Zero, end = Offset(Float.MAX_VALUE, Float.MAX_VALUE))
            SwipeDirection.UP_RIGHT -> Brush.linearGradient(listOf(color, Color.Transparent), start = Offset(Float.MAX_VALUE, 0f), end = Offset(0f, Float.MAX_VALUE))
            SwipeDirection.DOWN_LEFT -> Brush.linearGradient(listOf(color, Color.Transparent), start = Offset(0f, Float.MAX_VALUE), end = Offset(Float.MAX_VALUE, 0f))
            SwipeDirection.DOWN_RIGHT -> Brush.linearGradient(listOf(color, Color.Transparent), start = Offset(Float.MAX_VALUE, Float.MAX_VALUE), end = Offset.Zero)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
                .background(brush)
        )

        // Direction label
        val alignment = when (direction) {
            SwipeDirection.LEFT -> Alignment.CenterStart
            SwipeDirection.RIGHT -> Alignment.CenterEnd
            SwipeDirection.UP -> Alignment.TopCenter
            SwipeDirection.DOWN -> Alignment.BottomCenter
            SwipeDirection.UP_LEFT -> Alignment.TopStart
            SwipeDirection.UP_RIGHT -> Alignment.TopEnd
            SwipeDirection.DOWN_LEFT -> Alignment.BottomStart
            SwipeDirection.DOWN_RIGHT -> Alignment.BottomEnd
        }
        val padding = when (direction) {
            SwipeDirection.LEFT -> PaddingValues(start = 20.dp)
            SwipeDirection.RIGHT -> PaddingValues(end = 20.dp)
            SwipeDirection.UP -> PaddingValues(top = 80.dp)
            SwipeDirection.DOWN -> PaddingValues(bottom = 140.dp)
            SwipeDirection.UP_LEFT -> PaddingValues(start = 20.dp, top = 80.dp)
            SwipeDirection.UP_RIGHT -> PaddingValues(end = 20.dp, top = 80.dp)
            SwipeDirection.DOWN_LEFT -> PaddingValues(start = 20.dp, bottom = 140.dp)
            SwipeDirection.DOWN_RIGHT -> PaddingValues(end = 20.dp, bottom = 140.dp)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha * 3f)
                .padding(padding),
            contentAlignment = alignment
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = labelIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DirectionHints(
    destinations: Map<SwipeDirection, com.photoswipe.app.model.DestinationFolder>,
    modifier: Modifier = Modifier
) {
    val ordered = listOf(
        SwipeDirection.UP_LEFT, SwipeDirection.UP, SwipeDirection.UP_RIGHT,
        SwipeDirection.LEFT, SwipeDirection.RIGHT,
        SwipeDirection.DOWN_LEFT, SwipeDirection.DOWN, SwipeDirection.DOWN_RIGHT
    ).mapNotNull { dir -> destinations[dir]?.let { dir to it } }
    if (ordered.isEmpty()) return

    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ordered.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowItems.forEach { (direction, dest) ->
                    val color = directionColors[direction] ?: Color.Gray
                    val icon = directionIcons[direction] ?: Icons.Default.ArrowForward
                    HintChip(icon = icon, label = dest.name, color = color)
                }
            }
        }
    }
}

@Composable
private fun HintChip(icon: ImageVector, label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                "Loading photos...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun EmptyContent(sourceName: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Text(
                "No photos found in \"$sourceName\"",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            OutlinedButton(onClick = onBack) {
                Text("Go Back")
            }
        }
    }
}

@Composable
private fun DoneContent(processedCount: Int, totalCount: Int, onNewSession: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Text(
                "All done!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Sorted $processedCount of $totalCount photos.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onNewSession,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Session")
            }
        }
    }
}
