package io.nekohasekai.sfa.ui.profile

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.constant.EnabledType
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.databinding.ActivityAddProfileBinding
import io.nekohasekai.sfa.ktx.addTextChangedListener
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ktx.removeErrorIfNotEmpty
import io.nekohasekai.sfa.ktx.showErrorIfEmpty
import io.nekohasekai.sfa.ktx.startFilesForResult
import io.nekohasekai.sfa.ktx.text
import io.nekohasekai.sfa.ui.shared.AbstractActivity
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Date

class NewProfileActivity : AbstractActivity<ActivityAddProfileBinding>() {
    enum class FileSource(@StringRes var stringId: Int) {
        CreateNew(R.string.profile_source_create_new),
        Import(R.string.profile_source_import);
    }

    private val importFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { fileURI ->
            if (fileURI != null) {
                binding.sourceURL.editText?.setText(fileURI.toString())
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.title_new_profile)

        intent.getStringExtra("importName")?.also { importName ->
            intent.getStringExtra("importURL")?.also { importURL ->
                binding.name.editText?.setText(importName)
                binding.type.text = getString(TypedProfile.Type.Remote.stringId)
                binding.remoteURL.editText?.setText(importURL)
                binding.localFields.isVisible = false
                binding.remoteFields.isVisible = true
                binding.autoUpdateInterval.text = "60"
            }
        }

        binding.name.removeErrorIfNotEmpty()
        binding.type.addTextChangedListener {
            when (it) {
                getString(TypedProfile.Type.Local.stringId) -> {
                    binding.localFields.isVisible = true
                    binding.remoteFields.isVisible = false
                }

                getString(TypedProfile.Type.Remote.stringId) -> {
                    binding.localFields.isVisible = false
                    binding.remoteFields.isVisible = true
                    if (binding.autoUpdateInterval.text.toIntOrNull() == null) {
                        binding.autoUpdateInterval.text = "60"
                    }
                }
            }
        }
        binding.fileSourceMenu.addTextChangedListener {
            when (it) {
                getString(FileSource.CreateNew.stringId) -> {
                    binding.importFileButton.isVisible = false
                    binding.sourceURL.isVisible = false
                }

                getString(FileSource.Import.stringId) -> {
                    binding.importFileButton.isVisible = true
                    binding.sourceURL.isVisible = true
                }
            }
        }
        binding.importFileButton.setOnClickListener {
            startFilesForResult(importFile, "application/json")
        }
        binding.createProfile.setOnClickListener(this::createProfile)
        binding.autoUpdateInterval.addTextChangedListener(this::updateAutoUpdateInterval)
    }

    private fun createProfile(@Suppress("UNUSED_PARAMETER") view: View) {
        if (binding.name.showErrorIfEmpty()) {
            return
        }
        when (binding.type.text) {
            getString(TypedProfile.Type.Local.stringId) -> {
                when (binding.fileSourceMenu.text) {
                    getString(FileSource.Import.stringId) -> {
                        if (binding.sourceURL.showErrorIfEmpty()) {
                            return
                        }
                    }
                }
            }

            getString(TypedProfile.Type.Remote.stringId) -> {
                if (binding.remoteURL.showErrorIfEmpty()) {
                    return
                }
            }
        }
        binding.progressView.isVisible = true
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                createProfile0()
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    binding.progressView.isVisible = false
                    errorDialogBuilder(e).show()
                }
            }
        }
    }

    private suspend fun createProfile0() {
        val typedProfile = TypedProfile()
        val profile = Profile(name = binding.name.text, typed = typedProfile)
        profile.userOrder = ProfileManager.nextOrder()
        val fileID = ProfileManager.nextFileID()
        val configDirectory = File(filesDir, "configs").also { it.mkdirs() }
        val configFile = File(configDirectory, "$fileID.json")
        typedProfile.path = configFile.path

        when (binding.type.text) {
            getString(TypedProfile.Type.Local.stringId) -> {
                typedProfile.type = TypedProfile.Type.Local

                when (binding.fileSourceMenu.text) {
                    getString(FileSource.CreateNew.stringId) -> {
                        configFile.writeText("{}")
                    }

                    getString(FileSource.Import.stringId) -> {
                        val sourceURL = binding.sourceURL.text
                        val content = if (sourceURL.startsWith("content://")) {
                            val inputStream =
                                contentResolver.openInputStream(Uri.parse(sourceURL)) as InputStream
                            inputStream.use { it.bufferedReader().readText() }
                        } else if (sourceURL.startsWith("file://")) {
                            File(sourceURL).readText()
                        } else if (sourceURL.startsWith("http://") || sourceURL.startsWith("https://")) {
                            HTTPClient().use { it.getString(sourceURL) }
                        } else {
                            error("unsupported source: $sourceURL")
                        }
                        Libbox.checkConfig(content)
                        configFile.writeText(content)
                    }
                }
            }

            getString(TypedProfile.Type.Remote.stringId) -> {
                typedProfile.type = TypedProfile.Type.Remote
                val remoteURL = binding.remoteURL.text
                val content = HTTPClient().use { it.getString(remoteURL) }
                Libbox.checkConfig(content)
                configFile.writeText(content)
                typedProfile.remoteURL = remoteURL
                typedProfile.lastUpdated = Date()
                typedProfile.autoUpdate =
                    EnabledType.valueOf(binding.autoUpdate.text, this).boolValue
                binding.autoUpdateInterval.text.toIntOrNull()?.also {
                    typedProfile.autoUpdateInterval = it
                }
            }
        }
        ProfileManager.create(profile)
        withContext(Dispatchers.Main) {
            binding.progressView.isVisible = false
            finish()
        }
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
    }


}