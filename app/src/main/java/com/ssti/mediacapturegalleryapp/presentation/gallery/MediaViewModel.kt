package com.ssti.mediacapturegalleryapp.presentation.gallery

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssti.mediacapturegalleryapp.R
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
class MediaViewModel @Inject constructor(
    application: Application,
    private val getMediaListUseCase: GetMediaListUseCase,
    private val addMediaUseCase: AddMediaUseCase,
    private val deleteMediaUseCase: DeleteMediaUseCase) : AndroidViewModel(application) {

    private val context = application.applicationContext

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
                    _uiState.value = GalleryUiState.Error(context.getString(R.string.error_failed_to_load))
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
                _successMessage.emit(context.getString(R.string.msg_media_added))
            } catch (e: Exception) {
                _error.emit(context.getString(R.string.error_process_media))
                loadMedia()
            }
        }
    }

    fun deleteMedia(mediaItem: MediaItem) {
        viewModelScope.launch {
            try {
                deleteMediaUseCase(mediaItem)
                _successMessage.emit(context.getString(R.string.msg_item_deleted))
            } catch (e: Exception) {
                _error.emit(context.getString(R.string.error_delete_item))
            }
        }
    }
}
