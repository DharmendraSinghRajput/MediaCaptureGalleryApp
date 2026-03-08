package com.ssti.mediacapturegalleryapp.presentation.gallery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssti.mediacapturegalleryapp.domain.model.MediaItem
import com.ssti.mediacapturegalleryapp.domain.usecase.AddMediaUseCase
import com.ssti.mediacapturegalleryapp.domain.usecase.DeleteMediaUseCase
import com.ssti.mediacapturegalleryapp.domain.usecase.GetMediaListUseCase
import com.ssti.mediacapturegalleryapp.util.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaViewModel @Inject constructor(private val getMediaListUseCase: GetMediaListUseCase, private val addMediaUseCase: AddMediaUseCase, private val deleteMediaUseCase: DeleteMediaUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()
    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error.asSharedFlow()
    private val _successMessage = MutableSharedFlow<String>()
    val successMessage: SharedFlow<String> = _successMessage.asSharedFlow()
    private var loadJob: Job? = null
    init {
        loadMedia()
    }
    fun refreshMedia() {
        loadMedia()
    }

    private fun loadMedia() {
        loadJob?.cancel()
        _uiState.value = GalleryUiState.Loading
        loadJob = viewModelScope.launch {
            getMediaListUseCase()
                .catch { e ->
                    _uiState.value = GalleryUiState.Error(e.message ?: "Failed to load media")
                }
                .collectLatest { list ->
                    _uiState.value = GalleryUiState.Success(list)
                }
        }
    }

    fun addMedia(uri: Uri, mediaType: MediaType) {
        viewModelScope.launch {
            _uiState.value = GalleryUiState.Loading
            try {
                addMediaUseCase(uri, mediaType)
                _successMessage.emit("Media added with watermark")
            } catch (e: Exception) {
                _error.emit(e.message ?: "Failed to process media")
                loadMedia() // Refresh state
            }
        }
    }

    fun deleteMedia(mediaItem: MediaItem) {
        viewModelScope.launch {
            try {
                deleteMediaUseCase(mediaItem)
                _successMessage.emit("Item deleted")
            } catch (e: Exception) {
                _error.emit(e.message ?: "Failed to delete item")
            }
        }
    }
}
