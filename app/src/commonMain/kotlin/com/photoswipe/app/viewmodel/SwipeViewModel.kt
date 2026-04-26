package com.photoswipe.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoswipe.app.model.PhotoItem
import com.photoswipe.app.model.SwipeDirection
import com.photoswipe.app.storage.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SwipeState(
    val photos: List<PhotoItem> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val processedCount: Int = 0,
    val lastAction: LastAction? = null
)

data class LastAction(
    val photo: PhotoItem,
    val direction: SwipeDirection
)

class SwipeViewModel(private val storage: Storage) : ViewModel() {

    private val _state = MutableStateFlow(SwipeState())
    val state: StateFlow<SwipeState> = _state.asStateFlow()

    init {
        loadPhotos()
    }

    private fun loadPhotos() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val photos = storage.loadPhotos()
                _state.value = _state.value.copy(
                    photos = photos,
                    currentIndex = 0,
                    isLoading = false,
                    processedCount = 0
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load photos: ${e.message}"
                )
            }
        }
    }

    fun swipePhoto(direction: SwipeDirection) {
        val state = _state.value
        val photo = state.photos.getOrNull(state.currentIndex) ?: return
        if (!storage.config.destinations.containsKey(direction)) return

        viewModelScope.launch {
            try {
                val success = storage.movePhoto(photo, direction)
                if (success) {
                    _state.value = _state.value.copy(
                        currentIndex = state.currentIndex + 1,
                        processedCount = state.processedCount + 1,
                        lastAction = LastAction(photo, direction),
                        errorMessage = null
                    )
                } else {
                    _state.value = _state.value.copy(
                        errorMessage = "Failed to move ${photo.name}"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = "Error: ${e.message}"
                )
            }
        }
    }

    fun undoLastAction() {
        val lastAction = _state.value.lastAction ?: return
        val state = _state.value

        viewModelScope.launch {
            try {
                val restored = storage.undoMove(lastAction.photo, lastAction.direction) ?: return@launch
                val photos = storage.loadPhotos()
                val restoredIndex = photos.indexOfFirst { it.id == restored.id }
                _state.value = _state.value.copy(
                    photos = photos,
                    currentIndex = if (restoredIndex >= 0) restoredIndex
                        else (state.currentIndex - 1).coerceAtLeast(0),
                    processedCount = (state.processedCount - 1).coerceAtLeast(0),
                    lastAction = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = "Undo failed: ${e.message}"
                )
            }
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
