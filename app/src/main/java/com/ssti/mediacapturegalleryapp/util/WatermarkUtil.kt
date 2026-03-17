package com.ssti.mediacapturegalleryapp.util

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.text.SpannableString
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

/**
 * Utility to permanently embed watermarks into media files.
 * Style: "Shot on iQOO" professional look (Bottom-left corner).
 */
object WatermarkUtil {

    private val deviceName: String
        get() {
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            return if (model.startsWith(manufacturer, ignoreCase = true)) {
                model
            } else {
                "$manufacturer $model"
            }
        }

    /**
     * Permanently draws a professional "Shot on" watermark on an Image bitmap.
     * Placed at bottom-left with a subtle shadow for readability.
     */
    suspend fun applyWatermarkToImage(
        context: Context,
        inputBitmap: Bitmap,
        fileName: String,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val workingBitmap = inputBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(workingBitmap)
        
        // Match IQOO format: MM/dd/yyyy, HH:mm
        val timestamp = SimpleDateFormat("MM/dd/yyyy, HH:mm", Locale.getDefault()).format(Date())
        
        // Setup text appearance
        val paintMain = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = workingBitmap.height / 55f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(3f, 2f, 2f, Color.parseColor("#80000000"))
        }
        val paintSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = workingBitmap.height / 75f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            setShadowLayer(3f, 2f, 2f, Color.parseColor("#80000000"))
        }

        val line1 = deviceName
        val line2 = timestamp

        val bounds1 = Rect().also { paintMain.getTextBounds(line1, 0, line1.length, it) }
        
        val margin = workingBitmap.width / 25f
        val bottomPadding = workingBitmap.height / 20f
        
        // Draw Logo (If exists) or just Text
        val logoDrawable = ContextCompat.getDrawable(context, R.drawable.ic_company_logo)
        var textLeft = margin
        
        logoDrawable?.let {
            val logoSize = (bounds1.height() * 1.5f).toInt()
            val logoLeft = margin.toInt()
            val logoTop = (workingBitmap.height - bottomPadding - bounds1.height() * 1.5f).toInt()
            it.setBounds(logoLeft, logoTop, logoLeft + logoSize, logoTop + logoSize)
            it.draw(canvas)
            textLeft = (logoLeft + logoSize + margin / 3f)
        }

        // Draw Line 1 (Device Name)
        canvas.drawText(line1, textLeft, workingBitmap.height - bottomPadding - bounds1.height() * 0.5f, paintMain)
        
        // Draw Line 2 (Date Time)
        canvas.drawText(line2, textLeft, workingBitmap.height - bottomPadding + bounds1.height() * 0.8f, paintSub)

        FileOutputStream(outputFile).use { out -> 
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) 
        }
        outputFile
    }

    /**
     * Permanently burns a "Shot on" watermark into a Video file.
     */
    @OptIn(UnstableApi::class)
    suspend fun applyWatermarkToVideo(
        context: Context,
        inputUri: Uri,
        fileName: String,
        outputFile: File
    ): File = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val timestamp = SimpleDateFormat("MM/dd/yyyy, HH:mm", Locale.getDefault()).format(Date())
            val fullText = "$deviceName\n$timestamp"
            
            val spannableText = SpannableString(fullText).apply {
                val lineBreak = deviceName.length
                setSpan(StyleSpan(Typeface.BOLD), 0, lineBreak, 0)
                setSpan(RelativeSizeSpan(1.1f), 0, lineBreak, 0)
                setSpan(ForegroundColorSpan(Color.WHITE), 0, length, 0)
            }

            // Placed at bottom-left (-0.85 horizontally, -0.9 vertically)
            val textOverlay = TextOverlay.createStaticTextOverlay(
                spannableText, 
                OverlaySettings.Builder()
                    .setOverlayFrameAnchor(-0.85f, -0.9f)
                    .build()
            )

            val transformer = Transformer.Builder(context).build()
            val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
                .setEffects(Effects(emptyList(), ImmutableList.of<Effect>(OverlayEffect(ImmutableList.of(textOverlay)))))
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
}
