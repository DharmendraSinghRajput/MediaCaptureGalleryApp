package com.ssti.mediacapturegalleryapp.domain.model

import com.ssti.mediacapturegalleryapp.util.MediaType

data class MediaItem(
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val mediaType: MediaType,
    val createdAt: Long
)