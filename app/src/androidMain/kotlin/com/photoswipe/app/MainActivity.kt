package com.photoswipe.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import com.photoswipe.app.storage.Storage
import com.photoswipe.app.ui.setup.SetupScreen
import com.photoswipe.app.ui.swipe.SwipeScreen
import com.photoswipe.app.ui.theme.PhotoSwipeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoSwipeTheme {
                ApplyAndroidWindowChrome()
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
}

@Composable
private fun ApplyAndroidWindowChrome() {
    val view = LocalView.current
    val background = MaterialTheme.colorScheme.background
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
}
