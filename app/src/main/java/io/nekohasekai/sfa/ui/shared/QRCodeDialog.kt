package io.nekohasekai.sfa.ui.shared

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sfa.databinding.FragmentQrcodeDialogBinding

class QRCodeDialog(private val bitmap: Bitmap) :
    BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentQrcodeDialogBinding.inflate(inflater, container, false)
        val behavior = BottomSheetBehavior.from(binding.qrcodeLayout)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        binding.qrCode.setImageBitmap(bitmap)
        return binding.root
    }

}