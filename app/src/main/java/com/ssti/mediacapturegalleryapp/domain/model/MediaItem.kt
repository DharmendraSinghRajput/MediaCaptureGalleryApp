package com.ssti.mediacapturegalleryapp.domain.model

data class MediaItem(
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val mediaType: MediaType,
    val createdAt: Long
)

enum class MediaType {
    IMAGE, VIDEO
}
