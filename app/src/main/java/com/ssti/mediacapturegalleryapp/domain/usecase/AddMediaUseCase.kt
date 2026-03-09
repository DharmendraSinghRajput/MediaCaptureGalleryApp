package com.ssti.mediacapturegalleryapp.domain.usecase

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.ssti.mediacapturegalleryapp.domain.model.MediaItem
import com.ssti.mediacapturegalleryapp.domain.repository.MediaRepository
import com.ssti.mediacapturegalleryapp.util.MediaType
import com.ssti.mediacapturegalleryapp.util.WatermarkUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            WatermarkUtil.applyWatermarkToImage(context, bitmap, fileName, outputFile)
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        val mediaItem = MediaItem(
            filePath = outputFile.absolutePath,
            fileName = fileName,
            mediaType = mediaType,
            createdAt = System.currentTimeMillis()
        )
        repository.addMedia(mediaItem)
    }
}
