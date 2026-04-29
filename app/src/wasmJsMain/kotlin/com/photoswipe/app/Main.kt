package com.photoswipe.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.photoswipe.app.storage.Storage
import com.photoswipe.app.ui.setup.SetupScreen
import com.photoswipe.app.ui.swipe.SwipeScreen
import com.photoswipe.app.ui.theme.PhotoSwipeTheme
import kotlinx.browser.document
import kotlin.math.min

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        PhotoSwipeTheme {
            PhoneFrame {
                var storage by remember { mutableStateOf<Storage?>(null) }
                val current = storage
                if (current == null) {
                    SetupScreen(onStart = { storage = it })
                } else {
                    SwipeScreen(
                        storage = current,
                        onDone = { storage = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E10)),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val targetW = min(maxWidth.value, 390f).dp
            val targetH = min(maxHeight.value, 844f).dp
            Box(
                modifier = Modifier
                    .size(targetW, targetH)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color(0xFF050507))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF26262A),
                        shape = RoundedCornerShape(36.dp)
                    )
                    .padding(6.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(MaterialTheme.colorScheme.background)
            ) {
                content()
            }
        }
    }
}
