package com.ssti.mediacapturegalleryapp.presentation.fullscreen

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.MediaController
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsControllerCompat
import coil.load
import com.ssti.mediacapturegalleryapp.R
import com.ssti.mediacapturegalleryapp.databinding.ActivityFullScreenBinding
import com.ssti.mediacapturegalleryapp.domain.model.MediaType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FullScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        changeStatusBarColor(primaryColor)
        val filePath = intent.getStringExtra("file_path") ?: return
        val mediaType = intent.getStringExtra("media_type") ?: return
        val createdAt = intent.getLongExtra("created_at", 0)

        binding.closeButton.setOnClickListener { finish() }

        // Setup the professional UI watermark
        setupWatermarkOverlay(createdAt, filePath)

        if (mediaType == MediaType.IMAGE.name) {
            showImage(filePath)
        } else {
            showVideo(filePath)
        }
    }

    private fun setupWatermarkOverlay(createdAt: Long, filePath: String) {
        if (createdAt > 0) {
            // Get Device Name (e.g., IQOO Z10 5G)
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            val deviceName = if (model.startsWith(manufacturer, ignoreCase = true)) {
                model
            } else {
                "$manufacturer $model"
            }.uppercase()

            // Format Date (e.g., 03/06/2026 ,19:01)
            val formattedDate = SimpleDateFormat("dd/MM/yyyy ,HH:mm", Locale.getDefault()).format(Date(createdAt))

            // Get FileName from path
            val fileName = File(filePath).name

            // Layout content: Line 1: FileName, Line 2: DeviceName | Timestamp
            val watermarkText = "$fileName\n$deviceName | $formattedDate"

            binding.tvWatermarkOverlay.text = watermarkText
            binding.tvWatermarkOverlay.visibility = View.VISIBLE
        }
    }

    private fun showImage(filePath: String) {
       // binding.watermarkContainer.visibility = View.GONE
        binding.fullImageView.visibility = View.VISIBLE

        binding.videoView.visibility = View.GONE
        binding.fullImageView.load(filePath)
    }

    private fun showVideo(filePath: String) {
        binding.videoView.visibility = View.VISIBLE
        binding.fullImageView.visibility = View.GONE

        // Add playback controls (Pause, Seek)
        val mediaController = MediaController(this)
        mediaController.setAnchorView(binding.videoView)
        binding.videoView.setMediaController(mediaController)

        binding.videoView.setVideoURI(Uri.parse(filePath))

        // Auto-play when ready
        binding.videoView.setOnPreparedListener { 
            binding.videoView.start() 
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.videoView.stopPlayback()
    }
    private fun changeStatusBarColor(color: Int) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = color

        // Automatically adjust icon colors (Light/Dark) based on the background brightness
        val isLightBackground = ColorUtils.calculateLuminance(color) > 0.5
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = isLightBackground
    }
}
