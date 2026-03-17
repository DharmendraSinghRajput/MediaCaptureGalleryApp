package com.ssti.mediacapturegalleryapp.util

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.transformer.*
import com.google.common.collect.ImmutableList
import com.ssti.mediacapturegalleryapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


object WatermarkUtil {

    private val deviceName: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL}".uppercase()

    private fun createWatermarkBitmap(context: Context, width: Int, height: Int, fileName: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val timestamp = SimpleDateFormat("MM/dd/yyyy, HH:mm:ss", Locale.getDefault()).format(Date())

        // Setup Paints with Shadows for readability on any background
        val paintMain = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = height * 0.40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 2f, 2f, Color.parseColor("#99000000"))
        }
        val paintSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = height * 0.28f
            setShadowLayer(4f, 2f, 2f, Color.parseColor("#99000000"))
        }

        // Draw Logo (Requirement: Small logo of XYZ Company)
        val logoDrawable = ContextCompat.getDrawable(context, R.drawable.ic_company_logo)
        var textLeft = 10f
        logoDrawable?.let {
            val logoSize = (height * 0.75f).toInt()
            val logoTop = (height - logoSize) / 2
            it.setBounds(0, logoTop, logoSize, logoTop + logoSize)
            it.draw(canvas)
            textLeft = logoSize + 20f
        }

        // Draw Text: Line 1 (File Name), Line 2 (Device | Timestamp)
        canvas.drawText(fileName, textLeft, height * 0.45f, paintMain)
        canvas.drawText("$deviceName | $timestamp", textLeft, height * 0.88f, paintSub)

        return bitmap
    }

    @OptIn(UnstableApi::class)
    suspend fun applyWatermarkToVideo(
        context: Context,
        inputUri: Uri,
        fileName: String,
        outputFile: File
    ): File = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            // High-res watermark for video frames
            val watermark = createWatermarkBitmap(context, 1000, 250, fileName)

            // Position at Bottom-Left (-0.85, -0.85) in Media3 coordinates
            val overlaySettings = OverlaySettings.Builder()
                .setScale(0.4f, 0.1f) 
                .setBackgroundFrameAnchor(-0.85f, -0.85f) 
                .setOverlayFrameAnchor(-1f, -1f)
                .build()

            val bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(watermark, overlaySettings)
            val overlayEffect = OverlayEffect(ImmutableList.of(bitmapOverlay))
            
            val transformer = Transformer.Builder(context).build()
            val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
                .setEffects(Effects(emptyList(), ImmutableList.of<Effect>(overlayEffect)))
                .build()

            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(outputFile)
                }
                override fun onError(composition: Composition, result: ExportResult, e: ExportException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            })

            try {
                transformer.start(editedMediaItem, outputFile.absolutePath)
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            continuation.invokeOnCancellation { transformer.cancel() }
        }
    }
    suspend fun applyWatermarkToImage(
        context: Context,
        inputBitmap: Bitmap,
        fileName: String,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val workingBitmap = inputBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(workingBitmap)
        
        val wmWidth = (workingBitmap.width * 0.45f).toInt()
        val wmHeight = (wmWidth * 0.25f).toInt()
        val watermark = createWatermarkBitmap(context, wmWidth, wmHeight, fileName)

        val margin = workingBitmap.width * 0.04f
        canvas.drawBitmap(watermark, margin, workingBitmap.height - wmHeight - margin, null)

        FileOutputStream(outputFile).use { out -> 
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) 
        }
        outputFile
    }
}
