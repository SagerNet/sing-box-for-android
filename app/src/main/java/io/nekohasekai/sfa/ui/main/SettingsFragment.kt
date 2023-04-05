package io.nekohasekai.sfa.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        onCreate()
        return binding.root
    }

    private fun onCreate() {
        val activity = activity ?: return
        binding.versionText.text = Libbox.version()
        binding.clearButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                activity.getExternalFilesDir(null)?.deleteRecursively()
                loadDateSize()
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            loadDateSize()
        }
    }

    private suspend fun loadDateSize() {
        val activity = activity ?: return
        val dataSize = Libbox.formatBytes(
            (activity.getExternalFilesDir(null) ?: activity.filesDir)
                .walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        )
        withContext(Dispatchers.Main) {
            binding.dataSizeText.text = dataSize
        }
    }

}