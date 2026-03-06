package com.ssti.mediacapturegalleryapp.domain.usecase

import com.ssti.mediacapturegalleryapp.domain.model.MediaItem
import com.ssti.mediacapturegalleryapp.domain.repository.MediaRepository
import javax.inject.Inject

class DeleteMediaUseCase @Inject constructor(
    private val repository: MediaRepository
) {
    suspend operator fun invoke(mediaItem: MediaItem) = repository.deleteMedia(mediaItem)
}
