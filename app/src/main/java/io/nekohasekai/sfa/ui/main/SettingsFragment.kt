package io.nekohasekai.sfa.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.distribute.Distribute
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.constant.EnabledType
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.databinding.FragmentSettingsBinding
import io.nekohasekai.sfa.ktx.addTextChangedListener
import io.nekohasekai.sfa.ktx.launchCustomTab
import io.nekohasekai.sfa.ktx.setSimpleItems
import io.nekohasekai.sfa.ktx.text
import io.nekohasekai.sfa.ui.MainActivity
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

    private val requestIgnoreBatteryOptimizations = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        lifecycleScope.launch(Dispatchers.IO) {
            reloadSettings()
        }
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
        binding.disableMemoryLimit.addTextChangedListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val newValue = EnabledType.valueOf(it).boolValue
                Settings.disableMemoryLimit = !newValue
            }
        }
        binding.dontKillMyAppButton.setOnClickListener {
            it.context.launchCustomTab("https://dontkillmyapp.com/")
        }
        binding.requestIgnoreBatteryOptimizationsButton.setOnClickListener {
            requestIgnoreBatteryOptimizations.launch(
                Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${Application.application.packageName}")
                )
            )
        }
        binding.communityButton.setOnClickListener {
            it.context.launchCustomTab("https://community.sagernet.org/")
        }
        binding.documentationButton.setOnClickListener {
            it.context.launchCustomTab("http://sing-box.sagernet.org/installation/clients/sfa/")
        }
    }

    private suspend fun reloadSettings() {
        val activity = activity ?: return
        val dataSize = Libbox.formatBytes(
            (activity.getExternalFilesDir(null) ?: activity.filesDir)
                .walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        )
        val appCenterEnabled = Settings.analyticsAllowed == Settings.ANALYSIS_ALLOWED
        val checkUpdateEnabled = Settings.checkUpdateEnabled
        val removeBackgroudPermissionPage =
            Application.powerManager.isIgnoringBatteryOptimizations(Application.application.packageName)
        withContext(Dispatchers.Main) {
            binding.dataSizeText.text = dataSize
            binding.appCenterEnabled.text = EnabledType.from(appCenterEnabled).name
            binding.appCenterEnabled.setSimpleItems(R.array.enabled)
            binding.checkUpdateEnabled.isEnabled = appCenterEnabled
            binding.checkUpdateEnabled.text = EnabledType.from(checkUpdateEnabled).name
            binding.checkUpdateEnabled.setSimpleItems(R.array.enabled)
            binding.disableMemoryLimit.text = EnabledType.from(!Settings.disableMemoryLimit).name
            binding.disableMemoryLimit.setSimpleItems(R.array.enabled)
            binding.backgroundPermissionCard.isGone = removeBackgroudPermissionPage
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}