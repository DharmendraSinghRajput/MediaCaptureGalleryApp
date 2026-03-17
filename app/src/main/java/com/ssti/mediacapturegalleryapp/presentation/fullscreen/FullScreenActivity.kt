package com.ssti.mediacapturegalleryapp.presentation.fullscreen

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil.load
import com.ssti.mediacapturegalleryapp.R
import com.ssti.mediacapturegalleryapp.databinding.ActivityFullScreenBinding
import com.ssti.mediacapturegalleryapp.util.Constants
import com.ssti.mediacapturegalleryapp.util.MediaType

/**
 * FullScreenActivity provides an immersive viewing experience for both photos and videos.
 * Adheres to Section 3.2 of the Assignment:
 * - Uses Media3 ExoPlayer for high-performance video playback.
 * - Supports autoplay for videos.
 * - Clean UI as watermark is permanently burned into the media.
 */
class FullScreenActivity : AppCompatActivity() {

    private val binding by lazy { ActivityFullScreenBinding.inflate(layoutInflater) }
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupUI()
        handleIntent()
    }

    private fun setupUI() {
        // Match status bar with background for immersion
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
            load(filePath) {
                crossfade(true)
            }
        }
        binding.playerView.visibility = View.GONE
    }

    private fun showVideo(filePath: String) {
        binding.playerView.visibility = View.VISIBLE
        binding.fullImageView.visibility = View.GONE

        exoPlayer = ExoPlayer.Builder(this).build().apply {
            binding.playerView.player = this
            
            val mediaItem = MediaItem.fromUri(filePath)
            setMediaItem(mediaItem)
            
            prepare()
            
            // Requirement 3.2: Open full screen video player and autoplay
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    private fun changeStatusBarColor(color: Int) {
        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = color
        }
        // Ensure system icons are visible on the selected color
        val isLightBackground = ColorUtils.calculateLuminance(color) > 0.5
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = isLightBackground
    }

    override fun onStop() {
        super.onStop()
        // Release player when not visible to save resources
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}
