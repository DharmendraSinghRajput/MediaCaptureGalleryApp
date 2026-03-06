package com.ssti.mediacapturegalleryapp.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.ssti.mediacapturegalleryapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


object WatermarkUtil {

    private val deviceModel: String
        get() {
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            return if (model.startsWith(manufacturer, ignoreCase = true)) {
                model
            } else {
                "$manufacturer $model"
            }.uppercase()
        }


    suspend fun applyWatermarkToImage(
        context: Context,
        inputBitmap: Bitmap,
        fileName: String,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val workingBitmap = inputBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(workingBitmap)
        val timestamp = SimpleDateFormat("dd/MM/yyyy ,HH:mm", Locale.getDefault()).format(Date())
        val paintMain = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = workingBitmap.height / 35f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val paintSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textSize = workingBitmap.height / 45f
        }

        val mainText = deviceModel
        val subText = timestamp

        val boundsMain = Rect()
        paintMain.getTextBounds(mainText, 0, mainText.length, boundsMain)
        
        val boundsSub = Rect()
        paintSub.getTextBounds(subText, 0, subText.length, boundsSub)

        // 1. Draw solid black background bar at the bottom
        val bgPaint = Paint().apply { color = Color.BLACK }
        val padding = 40f
        val rectHeight = boundsMain.height() + boundsSub.height() + padding * 2
        
        canvas.drawRect(
            0f,
            workingBitmap.height - rectHeight,
            workingBitmap.width.toFloat(),
            workingBitmap.height.toFloat(),
            bgPaint
        )

        FileOutputStream(outputFile).use { out ->
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        outputFile
    }

    @OptIn(UnstableApi::class)
    suspend fun applyWatermarkToVideo(
        context: Context,
        inputUri: Uri,
        fileName: String,
        outputFile: File
    ): File = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val timestamp = SimpleDateFormat("dd/MM/yyyy ,HH:mm", Locale.getDefault()).format(Date())
            val fullText = "$deviceModel\n$timestamp"
            
            val spannableText = SpannableString(fullText)
            val lineBreak = deviceModel.length
            
            // Apply visual hierarchy to video text (Line 1 Bold/Large, Line 2 Smaller/Gray)
            spannableText.setSpan(StyleSpan(Typeface.BOLD), 0, lineBreak, 0)
            spannableText.setSpan(RelativeSizeSpan(1.2f), 0, lineBreak, 0)
            spannableText.setSpan(ForegroundColorSpan(Color.WHITE), 0, lineBreak, 0)
            
            spannableText.setSpan(ForegroundColorSpan(Color.LTGRAY), lineBreak + 1, fullText.length, 0)
            spannableText.setSpan(RelativeSizeSpan(0.8f), lineBreak + 1, fullText.length, 0)
            
            // Background for the text area
            spannableText.setSpan(BackgroundColorSpan(Color.BLACK), 0, fullText.length, 0)

            val textOverlay = TextOverlay.createStaticTextOverlay(
                spannableText,
                OverlaySettings.Builder()
                    .setOverlayFrameAnchor(0f, -0.92f) // Centered horizontally, near bottom vertically
                    .build()
            )

            // Add Logo to video
            val logoBitmap = drawableToBitmap(context, R.drawable.ic_company_logo)
            val bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(
                logoBitmap,
                OverlaySettings.Builder()
                    .setScale(0.08f, 0.08f)
                    .setOverlayFrameAnchor(-0.85f, -0.92f) // Bottom left area
                    .build()
            )

            val overlayEffect = OverlayEffect(ImmutableList.of(textOverlay, bitmapOverlay))
            val effects = Effects(emptyList(), ImmutableList.of<Effect>(overlayEffect))

            val transformer = Transformer.Builder(context).build()
            val mediaItem = MediaItem.fromUri(inputUri)
            val editedMediaItem = EditedMediaItem.Builder(mediaItem).setEffects(effects).build()

            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(outputFile)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            })

            try {
                transformer.start(editedMediaItem, outputFile.absolutePath)
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            // Ensure resources are released and processing stops if coroutine is cancelled
            continuation.invokeOnCancellation {
                transformer.cancel()
            }
        }
    }

    /**
     * Helper to convert a drawable resource to a Bitmap for overlay purposes.
     */
    private fun drawableToBitmap(context: Context, drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableId)!!
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
