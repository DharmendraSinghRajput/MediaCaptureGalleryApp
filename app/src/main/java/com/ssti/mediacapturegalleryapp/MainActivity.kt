package com.ssti.mediacapturegalleryapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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
import com.ssti.mediacapturegalleryapp.databinding.ActivityMainBinding
import com.ssti.mediacapturegalleryapp.databinding.BottomSheetAddAttachmentBinding
import com.ssti.mediacapturegalleryapp.domain.model.MediaItem
import com.ssti.mediacapturegalleryapp.domain.model.MediaType
import com.ssti.mediacapturegalleryapp.presentation.fullscreen.FullScreenActivity
import com.ssti.mediacapturegalleryapp.presentation.gallery.GalleryUiState
import com.ssti.mediacapturegalleryapp.presentation.gallery.MediaAdapter
import com.ssti.mediacapturegalleryapp.presentation.gallery.MediaViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MediaViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MediaAdapter

    private var tempUri: Uri? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.all { it.value }) {
            Toast.makeText(this, "Permissions required for camera and storage", Toast.LENGTH_SHORT).show()
        }
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempUri?.let { viewModel.addMedia(it, MediaType.IMAGE) }
        }
    }

    private val recordVideoLauncher = registerForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) {
            tempUri?.let { viewModel.addMedia(it, MediaType.VIDEO) }
        }
    }

    private val pickPhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.addMedia(it, MediaType.IMAGE) }
    }

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.addMedia(it, MediaType.VIDEO) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        
        // Status Bar Color logic
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        changeStatusBarColor(primaryColor)

        setupRecyclerView()
        setupListeners()
        observeViewModel()
        checkPermissions()
    }

    private fun changeStatusBarColor(color: Int) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = color
        
        val isLightBackground = ColorUtils.calculateLuminance(color) > 0.5
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = isLightBackground
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        
        val menuItem = menu.findItem(R.id.action_toggle_theme)
        val actionView = menuItem.actionView
        val themeSwitch = actionView?.findViewById<SwitchCompat>(R.id.themeSwitch)
        
        // PHONE SETTINGS के हिसाब से switch को sync करना
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkModeActive = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        
        // Listener हटाकर state set करें ताकि loop न बने
        themeSwitch?.setOnCheckedChangeListener(null)
        themeSwitch?.isChecked = isDarkModeActive
        
        // दोबारा Listener जोड़ें manual click के लिए
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
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.addAttachmentFab.setOnClickListener {
            showAddAttachmentBottomSheet()
        }
        
        binding.swipeRefreshLayout.setOnRefreshListener {
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
                                binding.swipeRefreshLayout.isRefreshing = true
                                binding.progressBar.visibility = if (!binding.swipeRefreshLayout.isRefreshing) View.VISIBLE else View.GONE
                                binding.addAttachmentFab.isEnabled = false
                            }
                            is GalleryUiState.Success -> {
                                binding.swipeRefreshLayout.isRefreshing = false
                                binding.progressBar.visibility = View.GONE
                                binding.addAttachmentFab.isEnabled = true
                                adapter.submitList(state.mediaList)
                                binding.emptyStateText.visibility = if (state.mediaList.isEmpty()) View.VISIBLE else View.GONE
                            }
                            is GalleryUiState.Error -> {
                                binding.swipeRefreshLayout.isRefreshing = false
                                binding.progressBar.visibility = View.GONE
                                binding.addAttachmentFab.isEnabled = true
                                Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
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
        val file = File(getExternalFilesDir(null), "temp_media_${System.currentTimeMillis()}$extension")
        return FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", file)
    }

    private fun openFullScreen(mediaItem: MediaItem) {
        val intent = Intent(this, FullScreenActivity::class.java).apply {
            putExtra("file_path", mediaItem.filePath)
            putExtra("media_type", mediaItem.mediaType.name)
            putExtra("created_at", mediaItem.createdAt)
        }
        startActivity(intent)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }
}
