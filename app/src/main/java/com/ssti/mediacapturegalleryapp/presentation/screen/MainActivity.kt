package com.ssti.mediacapturegalleryapp.presentation.fullscreen

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.ssti.mediacapturegalleryapp.R
import com.ssti.mediacapturegalleryapp.databinding.ActivityMainBinding
import com.ssti.mediacapturegalleryapp.domain.model.MediaItem
import com.ssti.mediacapturegalleryapp.presentation.gallery.GalleryUiState
import com.ssti.mediacapturegalleryapp.presentation.gallery.MediaAdapter
import com.ssti.mediacapturegalleryapp.presentation.gallery.MediaViewModel
import com.ssti.mediacapturegalleryapp.util.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * MainActivity: The primary entry point and Gallery screen.
 * Implements modern UI practices including SwipeRefresh, Theme Toggling, and System Insets.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val viewModel: MediaViewModel by viewModels()
    private lateinit var adapter: MediaAdapter
    private lateinit var mediaPickerHelper: MediaPickerHelper

    // Must be declared at class level
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.all { it.value }) {
            Toast.makeText(this, getString(R.string.error_delete_item), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        initHelpers()
        setupUI()
        observeViewModel()
        checkPermissions()
    }

    private fun initHelpers() {
        // Callback triggers UseCase which handles the permanent Canvas/Transformer watermarking
        mediaPickerHelper = MediaPickerHelper(this) { uri, mediaType ->
            viewModel.addMedia(uri, mediaType)
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        changeStatusBarColor(ContextCompat.getColor(this, R.color.primary))
        
        adapter = MediaAdapter(
            onItemClick = { openFullScreen(it) },
            onDeleteClick = { viewModel.deleteMedia(it) }
        )
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshMedia()
        }

        binding.addAttachmentFab.setOnClickListener {
            showAddAttachmentBottomSheet()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // UI State Observer
                launch {
                    viewModel.uiState.collectLatest { state ->
                        handleUiState(state)
                    }
                }
                // Error Event Observer
                launch {
                    viewModel.error.collectLatest { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                    }
                }
                // Success Event Observer
                launch {
                    viewModel.successMessage.collectLatest { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun handleUiState(state: GalleryUiState) {
        binding.swipeRefreshLayout.isRefreshing = state is GalleryUiState.Loading
        binding.progressBar.visibility = if (state is GalleryUiState.Loading) View.VISIBLE else View.GONE
        binding.addAttachmentFab.isEnabled = state !is GalleryUiState.Loading

        when (state) {
            is GalleryUiState.Success -> {
                adapter.submitList(state.mediaList)
                binding.emptyStateText.visibility = if (state.mediaList.isEmpty()) View.VISIBLE else View.GONE
            }
            is GalleryUiState.Error -> {
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    private fun showAddAttachmentBottomSheet() {
        BottomSheetUtils.showAddAttachmentBottomSheet(
            context = this,
            onCapturePhoto = { mediaPickerHelper.launchCameraForImage(createTempUri(".jpg")) },
            onRecordVideo = { mediaPickerHelper.launchCameraForVideo(createTempUri(".mp4")) },
            onSelectPhoto = { mediaPickerHelper.launchGalleryForImage() },
            onSelectVideo = { mediaPickerHelper.launchGalleryForVideo() }
        )
    }

    private fun createTempUri(extension: String): Uri {
        val file = File(getExternalFilesDir(null), "temp_media_${System.currentTimeMillis()}$extension")
        return FileProvider.getUriForFile(this, "${applicationContext.packageName}${Constants.FILE_PROVIDER_AUTHORITY}", file)
    }

    private fun openFullScreen(mediaItem: MediaItem) {
        val intent = Intent(this, FullScreenActivity::class.java).apply {
            putExtra(Constants.EXTRA_FILE_PATH, mediaItem.filePath)
            putExtra(Constants.EXTRA_MEDIA_TYPE, mediaItem.mediaType.name)
            putExtra(Constants.EXTRA_CREATED_AT, mediaItem.createdAt)
        }
        startActivity(intent)
    }

    private fun checkPermissions() {
        if (!PermissionUtils.hasPermissions(this)) {
            requestPermissionLauncher.launch(PermissionUtils.getRequiredPermissions())
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val themeSwitch = menu.findItem(R.id.action_toggle_theme).actionView?.findViewById<SwitchCompat>(R.id.themeSwitch)
        
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        themeSwitch?.isChecked = isDarkMode
        
        themeSwitch?.setOnCheckedChangeListener { _, isChecked ->
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
        return true
    }
}
