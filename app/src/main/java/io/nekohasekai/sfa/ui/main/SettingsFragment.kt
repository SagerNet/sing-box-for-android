package io.nekohasekai.sfa.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.analytics.Analytics
import com.microsoft.appcenter.distribute.Distribute
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.databinding.DialogFeedbackBinding
import io.nekohasekai.sfa.databinding.FragmentSettingsBinding
import io.nekohasekai.sfa.ktx.addTextChangedListener
import io.nekohasekai.sfa.ktx.setSimpleItems
import io.nekohasekai.sfa.ktx.showErrorIfEmpty
import io.nekohasekai.sfa.ktx.text
import io.nekohasekai.sfa.ui.MainActivity
import io.nekohasekai.sfa.ui.shared.EnabledType
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
        val activity = activity as MainActivity? ?: return
        binding.versionText.text = Libbox.version()
        binding.clearButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                activity.getExternalFilesDir(null)?.deleteRecursively()
                reloadSettings()
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            reloadSettings()
        }
        binding.appCenterEnabled.addTextChangedListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val allowed = EnabledType.valueOf(it).boolValue
                Settings.analyticsAllowed =
                    if (allowed) Settings.ANALYSIS_ALLOWED else Settings.ANALYSIS_DISALLOWED
                withContext(Dispatchers.Main) {
                    binding.checkUpdateEnabled.isEnabled = allowed
                    binding.feedbackButton.isEnabled = allowed
                }
                if (!allowed) {
                    AppCenter.setEnabled(false)
                } else {
                    if (!AppCenter.isConfigured()) {
                        activity.startAnalysisInternal()
                    }
                    AppCenter.setEnabled(true)
                }
            }
        }
        binding.checkUpdateEnabled.addTextChangedListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val newValue = EnabledType.valueOf(it).boolValue
                Settings.checkUpdateEnabled = newValue
                if (!newValue) {
                    Distribute.disableAutomaticCheckForUpdate()
                }
            }
        }

        binding.feedbackButton.setOnClickListener(::showFeedbackDialog)
    }

    private fun showFeedbackDialog(view: View) {
        val binding = DialogFeedbackBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(view.context)
            .setTitle(R.string.title_feedback)
            .setView(binding.root)
            .create()
        binding.feedbackButton.setOnClickListener send@{
            if (binding.message.showErrorIfEmpty()) {
                return@send
            }
            dialog.dismiss()
            Analytics.trackEvent("feedback", mapOf("message" to binding.message.text))
        }
        dialog.show()
    }

    private suspend fun reloadSettings() {
        val activity = activity ?: return
        val dataSize = Libbox.formatBytes(
            (activity.getExternalFilesDir(null) ?: activity.filesDir)
                .walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        )
        val appCenterEnabled = Settings.analyticsAllowed == Settings.ANALYSIS_ALLOWED
        val checkUpdateEnabled = Settings.checkUpdateEnabled
        withContext(Dispatchers.Main) {
            binding.dataSizeText.text = dataSize
            binding.appCenterEnabled.text = EnabledType.from(appCenterEnabled).name
            binding.appCenterEnabled.setSimpleItems(R.array.enabled)
            binding.checkUpdateEnabled.isEnabled = appCenterEnabled
            binding.checkUpdateEnabled.text = EnabledType.from(checkUpdateEnabled).name
            binding.checkUpdateEnabled.setSimpleItems(R.array.enabled)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}