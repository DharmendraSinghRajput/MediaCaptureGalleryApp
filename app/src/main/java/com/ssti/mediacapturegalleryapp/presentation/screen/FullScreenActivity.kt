package com.ssti.mediacapturegalleryapp.presentation.fullscreen

import android.net.Uri
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
import com.ssti.mediacapturegalleryapp.util.Constants
import com.ssti.mediacapturegalleryapp.util.MediaType

/**
 * FullScreenActivity: A clean, immersive viewer for watermarked media.
 * Fulfills Requirement 3.2: 
 * - Uses standard VideoView for media playback.
 * - Supports autoplay.
 * - Minimal UI as watermark is permanently burned into the media frames.
 */
class FullScreenActivity : AppCompatActivity() {

    private val binding by lazy { ActivityFullScreenBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupSystemUI()
        handleIntent()
    }

    private fun setupSystemUI() {
        // Make status bar black for an immersive full-screen experience
        changeStatusBarColor(ContextCompat.getColor(this, android.R.color.black))
        binding.closeButton.setOnClickListener { finish() }
    }

    private fun handleIntent() {
        val filePath = intent.getStringExtra(Constants.EXTRA_FILE_PATH) ?: return
        val mediaTypeString = intent.getStringExtra(Constants.EXTRA_MEDIA_TYPE) ?: return

        if (mediaTypeString == MediaType.IMAGE.name) {
            showImage(filePath)
        } else {
            showVideo(filePath)
        }
    }

    private fun showImage(filePath: String) {
        binding.fullImageView.apply {
            visibility = View.VISIBLE
            load(filePath)
        }
        binding.videoView.visibility = View.GONE
    }

    private fun showVideo(filePath: String) {
        binding.videoView.visibility = View.VISIBLE
        binding.fullImageView.visibility = View.GONE

        // Using VideoView with MediaController for standard playback
        val mediaController = MediaController(this)
        mediaController.setAnchorView(binding.videoView)
        binding.videoView.setMediaController(mediaController)
        
        binding.videoView.setVideoURI(Uri.parse(filePath))
        
        // Autoplay requirement
        binding.videoView.setOnPreparedListener { 
            binding.videoView.start() 
        }
    }

    private fun changeStatusBarColor(color: Int) {
        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = color
        }
        val isLightBackground = ColorUtils.calculateLuminance(color) > 0.5
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = isLightBackground
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.videoView.stopPlayback()
    }
}
