package com.ssti.mediacapturegalleryapp.domain.repository

import com.ssti.mediacapturegalleryapp.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getMediaList(): Flow<List<MediaItem>>
    suspend fun addMedia(mediaItem: MediaItem)
    suspend fun deleteMedia(mediaItem: MediaItem)
}
