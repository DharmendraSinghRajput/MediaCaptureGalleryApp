package com.ssti.mediacapturegalleryapp.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WatermarkUtil {
    private val deviceName: String
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

        val timestamp = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        
        val paintMain = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = workingBitmap.height / 35f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val paintSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textSize = workingBitmap.height / 45f
        }

        val mainText = fileName
        val subText = "$deviceName | $timestamp"

        val boundsMain = Rect()
        paintMain.getTextBounds(mainText, 0, mainText.length, boundsMain)
        
        val boundsSub = Rect()
        paintSub.getTextBounds(subText, 0, subText.length, boundsSub)
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

}
