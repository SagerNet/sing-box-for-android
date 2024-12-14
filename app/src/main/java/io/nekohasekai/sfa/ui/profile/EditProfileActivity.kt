package io.nekohasekai.sfa.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.UpdateProfileWork
import io.nekohasekai.sfa.constant.EnabledType
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.databinding.ActivityEditProfileBinding
import io.nekohasekai.sfa.ktx.addTextChangedListener
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ktx.setSimpleItems
import io.nekohasekai.sfa.ktx.text
import io.nekohasekai.sfa.ui.shared.AbstractActivity
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

class EditProfileActivity : AbstractActivity<ActivityEditProfileBinding>() {

    private lateinit var profile: Profile
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.title_edit_profile)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                loadProfile()
            }.onFailure {
                withContext(Dispatchers.Main) {
                    errorDialogBuilder(it)
                        .setPositiveButton(R.string.ok) { _, _ -> finish() }
                        .show()
                }
            }
        }
    }

    private suspend fun loadProfile() {
        delay(200L)
        val profileId = intent.getLongExtra("profile_id", -1L)
        if (profileId == -1L) error("invalid arguments")
        profile = ProfileManager.get(profileId) ?: error("invalid arguments")
        withContext(Dispatchers.Main) {
            binding.name.text = profile.name
            binding.name.addTextChangedListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        profile.name = it
                        ProfileManager.update(profile)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            errorDialogBuilder(e).show()
                        }
                    }
                }
            }
            binding.type.text = profile.typed.type.getString(this@EditProfileActivity)
            binding.editButton.setOnClickListener {
                startActivity(
                    Intent(
                        this@EditProfileActivity,
                        EditProfileContentActivity::class.java
                    ).apply {
                        putExtra("profile_id", profile.id)
                    })
            }
            when (profile.typed.type) {
                TypedProfile.Type.Local -> {
                    binding.editButton.isVisible = true
                    binding.remoteFields.isVisible = false
                }

                TypedProfile.Type.Remote -> {
                    binding.editButton.isVisible = false
                    binding.remoteFields.isVisible = true
                    binding.remoteURL.text = profile.typed.remoteURL
                    binding.lastUpdated.text =
                        DateFormat.getDateTimeInstance().format(profile.typed.lastUpdated)
                    binding.autoUpdate.text = EnabledType.from(profile.typed.autoUpdate)
                        .getString(this@EditProfileActivity)
                    binding.autoUpdate.setSimpleItems(R.array.enabled)
                    binding.autoUpdateInterval.isVisible = profile.typed.autoUpdate
                    binding.autoUpdateInterval.text = profile.typed.autoUpdateInterval.toString()
                }
            }
            binding.remoteURL.addTextChangedListener(this@EditProfileActivity::updateRemoteURL)
            binding.autoUpdate.addTextChangedListener(this@EditProfileActivity::updateAutoUpdate)
            binding.autoUpdateInterval.addTextChangedListener(this@EditProfileActivity::updateAutoUpdateInterval)
            binding.updateButton.setOnClickListener(this@EditProfileActivity::updateProfile)
            binding.profileLayout.isVisible = true
            binding.progressView.isVisible = false
        }
    }


    private fun updateRemoteURL(newValue: String) {
        profile.typed.remoteURL = newValue
        updateProfile()
    }

    private fun updateAutoUpdate(newValue: String) {
        val boolValue = EnabledType.valueOf(this, newValue).boolValue
        if (profile.typed.autoUpdate == boolValue) {
            return
        }
        binding.autoUpdateInterval.isVisible = boolValue
        profile.typed.autoUpdate = boolValue
        if (boolValue) {
            lifecycleScope.launch(Dispatchers.IO) {
                UpdateProfileWork.reconfigureUpdater()
            }
        }
        updateProfile()
    }

    private fun updateAutoUpdateInterval(newValue: String) {
        if (newValue.isBlank()) {
            binding.autoUpdateInterval.error = getString(R.string.profile_input_required)
            return
        }
        val intValue = try {
            newValue.toInt()
        } catch (e: Exception) {
            binding.autoUpdateInterval.error = e.localizedMessage
            return
        }
        if (intValue < 15) {
            binding.autoUpdateInterval.error =
                getString(R.string.profile_auto_update_interval_minimum_hint)
            return
        }
        binding.autoUpdateInterval.error = null
        profile.typed.autoUpdateInterval = intValue
        updateProfile()
    }

    private fun updateProfile() {
        binding.progressView.isVisible = true
        lifecycleScope.launch(Dispatchers.IO) {
            delay(200L)
            try {
                ProfileManager.update(profile)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorDialogBuilder(e).show()
                }
            }
            withContext(Dispatchers.Main) {
                binding.progressView.isVisible = false
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun updateProfile(view: View) {
        binding.progressView.isVisible = true
        lifecycleScope.launch(Dispatchers.IO) {
            var selectedProfileUpdated = false
            try {
                val content = HTTPClient().use { it.getString(profile.typed.remoteURL) }
                Libbox.checkConfig(content)
                val file = File(profile.typed.path)
                if (file.readText() != content) {
                    File(profile.typed.path).writeText(content)
                    if (profile.id == Settings.selectedProfile) {
                        selectedProfileUpdated = true
                    }
                }
                profile.typed.lastUpdated = Date()
                ProfileManager.update(profile)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorDialogBuilder(e).show()
                }
            }
            withContext(Dispatchers.Main) {
                binding.lastUpdated.text =
                    DateFormat.getDateTimeInstance().format(profile.typed.lastUpdated)
                binding.progressView.isVisible = false
            }
            if (selectedProfileUpdated) {
                runCatching {
                    Libbox.newStandaloneCommandClient().serviceReload()
                }
            }
        }
    }

}