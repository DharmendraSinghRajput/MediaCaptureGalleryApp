package com.ssti.mediacapturegalleryapp.util

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.ssti.mediacapturegalleryapp.databinding.BottomSheetAddAttachmentBinding

object BottomSheetUtils {

    fun showAddAttachmentBottomSheet(
        context: Context,
        onCapturePhoto: () -> Unit,
        onRecordVideo: () -> Unit,
        onSelectPhoto: () -> Unit,
        onSelectVideo: () -> Unit
    ) {
        val bottomSheet = BottomSheetDialog(context)
        val binding = BottomSheetAddAttachmentBinding.inflate(LayoutInflater.from(context))
        bottomSheet.setContentView(binding.root)

        binding.btnCapturePhoto.setOnClickListener {
            onCapturePhoto()
            bottomSheet.dismiss()
        }

        binding.btnRecordVideo.setOnClickListener {
            onRecordVideo()
            bottomSheet.dismiss()
        }

        binding.btnSelectPhoto.setOnClickListener {
            onSelectPhoto()
            bottomSheet.dismiss()
        }

        binding.btnSelectVideo.setOnClickListener {
            onSelectVideo()
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }
}
