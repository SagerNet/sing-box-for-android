package io.nekohasekai.sfa.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
import io.nekohasekai.sfa.ui.debug.DebugActivity
import io.nekohasekai.sfa.ui.profileoverride.ProfileOverrideActivity
import io.nekohasekai.sfa.vendor.Vendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentSettingsBinding.inflate(inflater, container, false)
        this.binding = binding
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
        val binding = binding ?: return
        binding.versionText.text = Libbox.version()
        binding.clearButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                activity.getExternalFilesDir(null)?.deleteRecursively()
                reloadSettings()
            }
        }
        if (!Vendor.checkUpdateAvailable()) {
            binding.checkUpdateEnabled.isVisible = false
            binding.checkUpdateButton.isVisible = false
        }
        binding.checkUpdateEnabled.addTextChangedListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val newValue = EnabledType.valueOf(it).boolValue
                Settings.checkUpdateEnabled = newValue
            }
        }
        binding.checkUpdateButton.setOnClickListener {
            Vendor.checkUpdate(activity, true)
        }
        binding.disableMemoryLimit.addTextChangedListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val newValue = EnabledType.valueOf(it).boolValue
                Settings.disableMemoryLimit = !newValue
            }
        }
        binding.dynamicNotificationEnabled.addTextChangedListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val newValue = EnabledType.valueOf(it).boolValue
                Settings.dynamicNotification = newValue
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
        binding.configureOverridesButton.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileOverrideActivity::class.java))
        }
        binding.communityButton.setOnClickListener {
            it.context.launchCustomTab("https://community.sagernet.org/")
        }
        binding.documentationButton.setOnClickListener {
            it.context.launchCustomTab("https://sing-box.sagernet.org/clients/android/")
        }
        binding.openDebugButton.setOnClickListener {
            startActivity(Intent(requireContext(), DebugActivity::class.java))
        }
        lifecycleScope.launch(Dispatchers.IO) {
            reloadSettings()
        }
    }

    private suspend fun reloadSettings() {
        val activity = activity ?: return
        val binding = binding ?: return
        val dataSize = Libbox.formatBytes(
            (activity.getExternalFilesDir(null) ?: activity.filesDir)
                .walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        )
        val checkUpdateEnabled = Settings.checkUpdateEnabled
        val removeBackgroundPermissionPage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Application.powerManager.isIgnoringBatteryOptimizations(Application.application.packageName)
        } else {
            true
        }
        val dynamicNotification = Settings.dynamicNotification
        withContext(Dispatchers.Main) {
            binding.dataSizeText.text = dataSize
            binding.checkUpdateEnabled.text = EnabledType.from(checkUpdateEnabled).name
            binding.checkUpdateEnabled.setSimpleItems(R.array.enabled)
            binding.disableMemoryLimit.text = EnabledType.from(!Settings.disableMemoryLimit).name
            binding.disableMemoryLimit.setSimpleItems(R.array.enabled)
            binding.backgroundPermissionCard.isGone = removeBackgroundPermissionPage
            binding.dynamicNotificationEnabled.text = EnabledType.from(dynamicNotification).name
            binding.dynamicNotificationEnabled.setSimpleItems(R.array.enabled)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

}