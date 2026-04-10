package com.photoswipe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.photoswipe.app.model.FolderConfig
import com.photoswipe.app.ui.setup.SetupScreen
import com.photoswipe.app.ui.swipe.SwipeScreen
import com.photoswipe.app.ui.theme.PhotoSwipeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoSwipeTheme {
                var config by remember { mutableStateOf<FolderConfig?>(null) }
                if (config == null) {
                    SetupScreen(onStart = { config = it })
                } else {
                    SwipeScreen(
                        config = config!!,
                        onDone = { config = null }
                    )
                }
            }
        }
    }
}
