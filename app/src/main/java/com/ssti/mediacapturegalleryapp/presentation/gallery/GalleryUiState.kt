package com.ssti.mediacapturegalleryapp.presentation.gallery

import com.ssti.mediacapturegalleryapp.domain.model.MediaItem

sealed class GalleryUiState {
    object Loading : GalleryUiState()
    data class Success(val mediaList: List<MediaItem>) : GalleryUiState()
    data class Error(val message: String) : GalleryUiState()
}
