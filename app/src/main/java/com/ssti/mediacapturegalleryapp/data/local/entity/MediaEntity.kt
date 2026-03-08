package com.ssti.mediacapturegalleryapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ssti.mediacapturegalleryapp.domain.model.MediaItem
import com.ssti.mediacapturegalleryapp.util.MediaType

@Entity(tableName = "media_table")
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val mediaType: String,
    val createdAt: Long
)

fun MediaEntity.toMediaItem(): MediaItem {
    return MediaItem(
        id = id,
        filePath = filePath,
        fileName = fileName,
        mediaType = MediaType.valueOf(mediaType),
        createdAt = createdAt
    )
}

fun MediaItem.toMediaEntity(): MediaEntity {
    return MediaEntity(
        id = id,
        filePath = filePath,
        fileName = fileName,
        mediaType = mediaType.name,
        createdAt = createdAt
    )
}
