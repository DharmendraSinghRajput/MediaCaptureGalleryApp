package com.ssti.mediacapturegalleryapp.data.repository

import com.ssti.mediacapturegalleryapp.data.local.dao.MediaDao
import com.ssti.mediacapturegalleryapp.data.local.entity.toMediaEntity
import com.ssti.mediacapturegalleryapp.data.local.entity.toMediaItem
import com.ssti.mediacapturegalleryapp.domain.model.MediaItem
import com.ssti.mediacapturegalleryapp.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

class MediaRepositoryImpl @Inject constructor(
    private val mediaDao: MediaDao
) : MediaRepository {
    override fun getMediaList(): Flow<List<MediaItem>> {
        return mediaDao.getAllMedia().map { entities ->
            entities.map { it.toMediaItem() }
        }
    }

    override suspend fun addMedia(mediaItem: MediaItem) {
        mediaDao.insertMedia(mediaItem.toMediaEntity())
    }

    override suspend fun deleteMedia(mediaItem: MediaItem) {
        mediaDao.deleteMedia(mediaItem.toMediaEntity())
        val file = File(mediaItem.filePath)
        if (file.exists()) {
            file.delete()
        }
    }
}
