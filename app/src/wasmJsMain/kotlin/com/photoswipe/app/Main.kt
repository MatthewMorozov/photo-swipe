package com.photoswipe.app

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.photoswipe.app.storage.Storage
import com.photoswipe.app.ui.setup.SetupScreen
import com.photoswipe.app.ui.swipe.SwipeScreen
import com.photoswipe.app.ui.theme.PhotoSwipeTheme
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        PhotoSwipeTheme {
            var storage by remember { mutableStateOf<Storage?>(null) }
            if (storage == null) {
                SetupScreen(onStart = { storage = it })
            } else {
                SwipeScreen(
                    storage = storage!!,
                    onDone = { storage = null }
                )
            }
        }
    }
}
