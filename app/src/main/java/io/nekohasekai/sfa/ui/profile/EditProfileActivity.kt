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
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.databinding.ActivityEditProfileBinding
import io.nekohasekai.sfa.ktx.addTextChangedListener
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ktx.setSimpleItems
import io.nekohasekai.sfa.ktx.shareProfile
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

class EditProfileActivity : AbstractActivity() {

    private var binding: ActivityEditProfileBinding? = null
    private var _profile: Profile? = null
    private val profile get() = _profile!!
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.title_edit_profile)
        val binding = ActivityEditProfileBinding.inflate(layoutInflater)
        this.binding = binding
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                loadProfile()
            }.onFailure {
                withContext(Dispatchers.Main) {
                    errorDialogBuilder(it)
                        .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                        .show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    private suspend fun loadProfile() {
        val binding = binding ?: return
        delay(200L)

        val profileId = intent.getLongExtra("profile_id", -1L)
        if (profileId == -1L) error("invalid arguments")
        _profile = ProfileManager.get(profileId) ?: error("invalid arguments")
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
            binding.type.text = profile.typed.type.name
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
                    binding.shareURLButton.isVisible = false
                }

                TypedProfile.Type.Remote -> {
                    binding.editButton.isVisible = false
                    binding.remoteFields.isVisible = true
                    binding.shareURLButton.isVisible = true
                    binding.remoteURL.text = profile.typed.remoteURL
                    binding.lastUpdated.text =
                        DateFormat.getDateTimeInstance().format(profile.typed.lastUpdated)
                    binding.autoUpdate.text = EnabledType.from(profile.typed.autoUpdate).name
                    binding.autoUpdate.setSimpleItems(R.array.enabled)
                    binding.autoUpdateInterval.isVisible = profile.typed.autoUpdate
                    binding.autoUpdateInterval.text = profile.typed.autoUpdateInterval.toString()
                }
            }
            binding.remoteURL.addTextChangedListener(this@EditProfileActivity::updateRemoteURL)
            binding.autoUpdate.addTextChangedListener(this@EditProfileActivity::updateAutoUpdate)
            binding.autoUpdateInterval.addTextChangedListener(this@EditProfileActivity::updateAutoUpdateInterval)
            binding.updateButton.setOnClickListener(this@EditProfileActivity::updateProfile)
            binding.checkButton.setOnClickListener(this@EditProfileActivity::checkProfile)
            binding.shareButton.setOnClickListener(this@EditProfileActivity::shareProfile)
            binding.shareURLButton.setOnClickListener(this@EditProfileActivity::shareProfileURL)
            binding.profileLayout.isVisible = true
            binding.progressView.isVisible = false
        }
    }


    private fun updateRemoteURL(newValue: String) {
        profile.typed.remoteURL = newValue
        updateProfile()
    }

    private fun updateAutoUpdate(newValue: String) {
        val binding = binding ?: return
        val boolValue = EnabledType.valueOf(newValue).boolValue
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
        val binding = binding ?: return
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
        val binding = binding ?: return
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

    private fun updateProfile(view: View) {
        val binding = binding ?: return
        binding.progressView.isVisible = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val content = HTTPClient().use { it.getString(profile.typed.remoteURL) }
                Libbox.checkConfig(content)
                File(profile.typed.path).writeText(content)
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
        }
    }

    private fun checkProfile(button: View) {
        val binding = binding ?: return
        binding.progressView.isVisible = true
        lifecycleScope.launch(Dispatchers.IO) {
            delay(200L)
            try {
                Libbox.checkConfig(File(profile.typed.path).readText())
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

    private fun shareProfile(button: View) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                shareProfile(profile)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorDialogBuilder(e).show()
                }
            }
        }
    }

    private fun shareProfileURL(button: View) {
        try {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("application/octet-stream")
                        .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .putExtra(
                            Intent.EXTRA_STREAM,
                            Libbox.generateRemoteProfileImportLink(
                                profile.name,
                                profile.typed.remoteURL
                            )
                        ),
                    getString(com.google.android.material.R.string.abc_shareactionprovider_share_with)
                )
            )
        } catch (e: Exception) {
            errorDialogBuilder(e).show()
        }
    }

}