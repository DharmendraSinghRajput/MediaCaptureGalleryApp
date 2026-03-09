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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.ssti.mediacapturegalleryapp.R
import com.ssti.mediacapturegalleryapp.databinding.ActivityMainBinding
import com.ssti.mediacapturegalleryapp.databinding.BottomSheetAddAttachmentBinding
import com.ssti.mediacapturegalleryapp.domain.model.MediaItem
import com.ssti.mediacapturegalleryapp.presentation.gallery.GalleryUiState
import com.ssti.mediacapturegalleryapp.presentation.gallery.MediaAdapter
import com.ssti.mediacapturegalleryapp.presentation.gallery.MediaViewModel
import com.ssti.mediacapturegalleryapp.util.Constants
import com.ssti.mediacapturegalleryapp.util.MediaType
import com.ssti.mediacapturegalleryapp.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    val mBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val viewModel: MediaViewModel by viewModels()
    private lateinit var adapter: MediaAdapter
    private var tempUri: Uri? = null
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.all { it.value }) {
            Toast.makeText(this, "Permissions required for camera and storage", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                tempUri?.let { viewModel.addMedia(it, MediaType.IMAGE) }
            }
        }

    private val recordVideoLauncher =
        registerForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
            if (success) {
                tempUri?.let { viewModel.addMedia(it, MediaType.VIDEO) }
            }
        }

    private val pickPhotoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { viewModel.addMedia(it, MediaType.IMAGE) }
        }

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { viewModel.addMedia(it, MediaType.VIDEO) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(mBinding.root)
        mBinding.apply {
            setSupportActionBar(toolbar)
            val primaryColor = ContextCompat.getColor(this@MainActivity, R.color.primary)
            changeStatusBarColor(primaryColor)
            setupRecyclerView()
            setupListeners()
            observeViewModel()
            checkPermissions()
        }
    }

    private fun changeStatusBarColor(color: Int) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = color

        val isLightBackground = ColorUtils.calculateLuminance(color) > 0.5
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            isLightBackground
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        val menuItem = menu.findItem(R.id.action_toggle_theme)
        val actionView = menuItem.actionView
        val themeSwitch = actionView?.findViewById<SwitchCompat>(R.id.themeSwitch)

        val isDarkModeActive =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        themeSwitch?.setOnCheckedChangeListener(null)
        themeSwitch?.isChecked = isDarkModeActive

        themeSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        return true
    }

    private fun setupRecyclerView() {
        adapter = MediaAdapter(
            onItemClick = { openFullScreen(it) },
            onDeleteClick = { viewModel.deleteMedia(it) }
        )
        mBinding.recyclerView.layoutManager = LinearLayoutManager(this)
        mBinding.recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        mBinding.addAttachmentFab.setOnClickListener {
            showAddAttachmentBottomSheet()
        }

        mBinding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshMedia()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        when (state) {
                            is GalleryUiState.Loading -> {
                                mBinding.swipeRefreshLayout.isRefreshing = true
                                mBinding.progressBar.visibility =
                                    if (!mBinding.swipeRefreshLayout.isRefreshing) View.VISIBLE else View.GONE
                                mBinding.addAttachmentFab.isEnabled = false
                            }

                            is GalleryUiState.Success -> {
                                mBinding.swipeRefreshLayout.isRefreshing = false
                                mBinding.progressBar.visibility = View.GONE
                                mBinding.addAttachmentFab.isEnabled = true
                                adapter.submitList(state.mediaList)
                                mBinding.emptyStateText.visibility =
                                    if (state.mediaList.isEmpty()) View.VISIBLE else View.GONE
                            }

                            is GalleryUiState.Error -> {
                                mBinding.swipeRefreshLayout.isRefreshing = false
                                mBinding.progressBar.visibility = View.GONE
                                mBinding.addAttachmentFab.isEnabled = true
                                Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG)
                                    .show()
                            }
                        }
                    }
                }
                launch {
                    viewModel.error.collectLatest { errorMessage ->
                        Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
                launch {
                    viewModel.successMessage.collectLatest { message ->
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showAddAttachmentBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        val bottomSheetBinding = BottomSheetAddAttachmentBinding.inflate(layoutInflater)
        bottomSheet.setContentView(bottomSheetBinding.root)

        bottomSheetBinding.btnCapturePhoto.setOnClickListener {
            tempUri = createTempUri(".jpg")
            tempUri?.let { takePhotoLauncher.launch(it) }
            bottomSheet.dismiss()
        }

        bottomSheetBinding.btnRecordVideo.setOnClickListener {
            tempUri = createTempUri(".mp4")
            tempUri?.let { recordVideoLauncher.launch(it) }
            bottomSheet.dismiss()
        }

        bottomSheetBinding.btnSelectPhoto.setOnClickListener {
            pickPhotoLauncher.launch("image/*")
            bottomSheet.dismiss()
        }

        bottomSheetBinding.btnSelectVideo.setOnClickListener {
            pickVideoLauncher.launch("video/*")
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun createTempUri(extension: String): Uri {
        val file =
            File(getExternalFilesDir(null), "temp_media_${System.currentTimeMillis()}$extension")
        return FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}${Constants.FILE_PROVIDER_AUTHORITY}",
            file
        )
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
}