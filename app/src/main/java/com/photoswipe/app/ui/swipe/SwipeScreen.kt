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

private val directionColors = mapOf(
    SwipeDirection.UP to Color(0xFF81C784),
    SwipeDirection.DOWN to Color(0xFF64B5F6),
    SwipeDirection.LEFT to Color(0xFFFFB74D),
    SwipeDirection.RIGHT to Color(0xFFCE93D8)
)

private val directionIcons = mapOf(
    SwipeDirection.UP to Icons.Default.ArrowUpward,
    SwipeDirection.DOWN to Icons.Default.ArrowDownward,
    SwipeDirection.LEFT to Icons.Default.ArrowBack,
    SwipeDirection.RIGHT to Icons.Default.ArrowForward
)

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

    // Active drag direction for overlay highlights
    val activeDrag: SwipeDirection? = when {
        abs(offsetX) > abs(offsetY) && abs(offsetX) > threshold / 2 ->
            if (offsetX > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
        abs(offsetY) > threshold / 2 ->
            if (offsetY > 0) SwipeDirection.DOWN else SwipeDirection.UP
        else -> null
    }

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
                                val dominant = if (abs(offsetX) > abs(offsetY)) {
                                    if (abs(offsetX) > threshold) {
                                        if (offsetX > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
                                    } else null
                                } else {
                                    if (abs(offsetY) > threshold) {
                                        if (offsetY > 0) SwipeDirection.DOWN else SwipeDirection.UP
                                    } else null
                                }

                                if (dominant != null && config.destinations.containsKey(dominant)) {
                                    // Fly off screen
                                    scope.launch {
                                        val targetX = when (dominant) {
                                            SwipeDirection.LEFT -> -flyDistance
                                            SwipeDirection.RIGHT -> flyDistance
                                            else -> animOffsetX.value
                                        }
                                        val targetY = when (dominant) {
                                            SwipeDirection.UP -> -flyDistance
                                            SwipeDirection.DOWN -> flyDistance
                                            else -> animOffsetY.value
                                        }
                                        launch { animOffsetX.animateTo(targetX, tween(200)) }
                                        animOffsetY.animateTo(targetY, tween(200))
                                        onSwipe(dominant)
                                    }
                                } else {
                                    // Snap back
                                    scope.launch {
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
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 0.35f else 0f,
        animationSpec = tween(150),
        label = "overlay_alpha"
    )
    val color = directionColors[direction] ?: Color.Gray

    Box(modifier = modifier) {
        // Gradient overlay from the direction edge
        val brush = when (direction) {
            SwipeDirection.LEFT -> Brush.horizontalGradient(listOf(color, Color.Transparent))
            SwipeDirection.RIGHT -> Brush.horizontalGradient(listOf(Color.Transparent, color))
            SwipeDirection.UP -> Brush.verticalGradient(listOf(color, Color.Transparent))
            SwipeDirection.DOWN -> Brush.verticalGradient(listOf(Color.Transparent, color))
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
        }
        val padding = when (direction) {
            SwipeDirection.LEFT, SwipeDirection.RIGHT -> PaddingValues(horizontal = 20.dp)
            SwipeDirection.UP -> PaddingValues(top = 80.dp)
            SwipeDirection.DOWN -> PaddingValues(bottom = 140.dp)
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
                    imageVector = directionIcons[direction] ?: Icons.Default.ArrowForward,
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
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(SwipeDirection.LEFT, SwipeDirection.UP, SwipeDirection.DOWN, SwipeDirection.RIGHT)
            .mapNotNull { dir -> destinations[dir]?.let { dir to it } }
            .forEach { (direction, dest) ->
                val color = directionColors[direction] ?: Color.Gray
                val icon = directionIcons[direction] ?: Icons.Default.ArrowForward
                HintChip(icon = icon, label = dest.name, color = color)
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
