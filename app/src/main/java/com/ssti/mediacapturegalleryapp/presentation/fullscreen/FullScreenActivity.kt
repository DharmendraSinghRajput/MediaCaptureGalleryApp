package com.ssti.mediacapturegalleryapp.presentation.fullscreen

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan
import android.graphics.Typeface
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FullScreenActivity : AppCompatActivity() {
    val mBinding by lazy { ActivityFullScreenBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(mBinding.root)
        mBinding.apply {
            val primaryColor = ContextCompat.getColor(this@FullScreenActivity, R.color.primary)
            changeStatusBarColor(primaryColor)
            closeButton.setOnClickListener { finish() }

            val filePath = intent.getStringExtra(Constants.EXTRA_FILE_PATH) ?: return
            val mediaType = intent.getStringExtra(Constants.EXTRA_MEDIA_TYPE) ?: return
            val createdAt = intent.getLongExtra(Constants.EXTRA_CREATED_AT, 0)

            setupWatermarkOverlay(createdAt, filePath)

            if (mediaType == MediaType.IMAGE.name) {
                showImage(filePath)
            } else {
                showVideo(filePath)
            }
        }
    }
    private fun setupWatermarkOverlay(createdAt: Long, filePath: String) {
        if (createdAt <= 0) return

        val deviceName = getFormattedDeviceName()
        val formattedDate = SimpleDateFormat(Constants.DATE_FORMAT_DISPLAY, Locale.getDefault()).format(Date(createdAt))
        val fileName = File(filePath).name
        
        val line1 = fileName
        val line2 = "$deviceName | $formattedDate"
        val fullText = "$line1\n$line2"
        
        val spannable = SpannableString(fullText).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, line1.length, 0)
            setSpan(RelativeSizeSpan(1.1f), 0, line1.length, 0)
        }
        
        mBinding.tvWatermarkOverlay.text = spannable
        mBinding.watermarkContainer.visibility = View.VISIBLE
    }

    private fun getFormattedDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }.uppercase()
    }

    private fun showImage(filePath: String) {
        mBinding.fullImageView.apply {
            visibility = View.VISIBLE
            load(filePath)
        }
        mBinding.videoView.visibility = View.GONE
    }

    private fun showVideo(filePath: String) {
        mBinding.videoView.apply {
            visibility = View.VISIBLE
            setMediaController(MediaController(this@FullScreenActivity).apply {
                setAnchorView(mBinding.videoView)
            })
            setVideoURI(Uri.parse(filePath))
            setOnPreparedListener { start() }
        }
        mBinding.fullImageView.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        mBinding.videoView.stopPlayback()
    }

    private fun changeStatusBarColor(color: Int) {
        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = color
        }
        val isLightBackground = ColorUtils.calculateLuminance(color) > 0.5
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = isLightBackground
    }
}
