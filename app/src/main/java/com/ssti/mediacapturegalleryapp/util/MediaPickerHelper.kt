package com.ssti.mediacapturegalleryapp.util

import android.net.Uri
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts

class MediaPickerHelper(
    caller: ActivityResultCaller,
    private val onMediaResult: (Uri, MediaType) -> Unit
) {
    private var tempUri: Uri? = null

    private val takePhotoLauncher = caller.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempUri?.let { onMediaResult(it, MediaType.IMAGE) }
        }
    }

    private val recordVideoLauncher = caller.registerForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) {
            tempUri?.let { onMediaResult(it, MediaType.VIDEO) }
        }
    }

    private val pickPhotoLauncher = caller.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onMediaResult(it, MediaType.IMAGE) }
    }

    private val pickVideoLauncher = caller.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onMediaResult(it, MediaType.VIDEO) }
    }

    fun launchCameraForImage(uri: Uri) {
        tempUri = uri
        takePhotoLauncher.launch(uri)
    }

    fun launchCameraForVideo(uri: Uri) {
        tempUri = uri
        recordVideoLauncher.launch(uri)
    }

    fun launchGalleryForImage() {
        pickPhotoLauncher.launch("image/*")
    }

    fun launchGalleryForVideo() {
        pickVideoLauncher.launch("video/*")
    }
}
