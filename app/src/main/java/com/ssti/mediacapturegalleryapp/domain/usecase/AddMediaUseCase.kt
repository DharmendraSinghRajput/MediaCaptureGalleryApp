package com.ssti.mediacapturegalleryapp.domain.usecase

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ssti.mediacapturegalleryapp.domain.model.MediaItem
import com.ssti.mediacapturegalleryapp.domain.repository.MediaRepository
import com.ssti.mediacapturegalleryapp.util.MediaType
import com.ssti.mediacapturegalleryapp.util.WatermarkUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

class AddMediaUseCase @Inject constructor(
    private val repository: MediaRepository,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(uri: Uri, mediaType: MediaType) = withContext(Dispatchers.IO) {
        val fileName = "MEDIA_${System.currentTimeMillis()}"
        val outputDir = context.getExternalFilesDir(null)
        val extension = if (mediaType == MediaType.IMAGE) ".jpg" else ".mp4"
        val outputFile = File(outputDir, "$fileName$extension")

        if (mediaType == MediaType.IMAGE) {
            // Permanent watermark for images
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap?.let {
                WatermarkUtil.applyWatermarkToImage(context, it, fileName, outputFile)
            }
        } else {
            // Permanent watermark for videos
            WatermarkUtil.applyWatermarkToVideo(context, uri, fileName, outputFile)
        }

        // Save to public gallery
        saveToPublicGallery(outputFile, fileName, mediaType)

        val mediaItem = MediaItem(
            filePath = outputFile.absolutePath,
            fileName = fileName,
            mediaType = mediaType,
            createdAt = System.currentTimeMillis()
        )
        repository.addMedia(mediaItem)
    }

    private fun saveToPublicGallery(file: File, fileName: String, mediaType: MediaType) {
        val contentResolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, if (mediaType == MediaType.IMAGE) "image/jpeg" else "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val directory = if (mediaType == MediaType.IMAGE) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/MediaCaptureGallery")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (mediaType == MediaType.IMAGE) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = contentResolver.insert(collection, contentValues)
        itemUri?.let { uri ->
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(file).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }
        }
    }
}
