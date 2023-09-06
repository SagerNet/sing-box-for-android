package io.nekohasekai.sfa.ui.profile

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
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

class NewProfileActivity : AbstractActivity() {
    enum class FileSource(val formatted: String) {
        CreateNew("Create New"),
        Import("Import");
    }

    private var binding: ActivityAddProfileBinding? = null
    private val importFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { fileURI ->
            val binding = binding ?: return@registerForActivityResult
            if (fileURI != null) {
                binding.sourceURL.editText?.setText(fileURI.toString())
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.title_new_profile)
        val binding = ActivityAddProfileBinding.inflate(layoutInflater)
        this.binding = binding
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.name.removeErrorIfNotEmpty()
        binding.type.addTextChangedListener {
            when (it) {
                TypedProfile.Type.Local.name -> {
                    binding.localFields.isVisible = true
                    binding.remoteFields.isVisible = false
                }

                TypedProfile.Type.Remote.name -> {
                    binding.localFields.isVisible = false
                    binding.remoteFields.isVisible = true
                }
            }
        }
        binding.fileSourceMenu.addTextChangedListener {
            when (it) {
                FileSource.CreateNew.formatted -> {
                    binding.importFileButton.isVisible = false
                    binding.sourceURL.isVisible = false
                }

                FileSource.Import.formatted -> {
                    binding.importFileButton.isVisible = true
                    binding.sourceURL.isVisible = true
                }
            }
        }
        binding.importFileButton.setOnClickListener {
            startFilesForResult(importFile, "application/json")
        }
        binding.createProfile.setOnClickListener(this::createProfile)
        intent.getStringExtra("importName")?.also { importName ->
            intent.getStringExtra("importURL")?.also { importURL ->
                binding.name.editText?.setText(importName)
                binding.type.text = TypedProfile.Type.Remote.name
                binding.remoteURL.editText?.setText(importURL)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    private fun createProfile(view: View) {
        val binding = binding ?: return
        if (binding.name.showErrorIfEmpty()) {
            return
        }
        when (binding.type.text) {
            TypedProfile.Type.Local.name -> {
                when (binding.fileSourceMenu.text) {
                    FileSource.Import.formatted -> {
                        if (binding.sourceURL.showErrorIfEmpty()) {
                            return
                        }
                    }
                }
            }

            TypedProfile.Type.Remote.name -> {
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
        val binding = binding ?: return
        val typedProfile = TypedProfile()
        val profile = Profile(name = binding.name.text, typed = typedProfile)
        profile.userOrder = ProfileManager.nextOrder()

        when (binding.type.text) {
            TypedProfile.Type.Local.name -> {
                typedProfile.type = TypedProfile.Type.Local
                val configDirectory = File(filesDir, "configs").also { it.mkdirs() }
                val configFile = File(configDirectory, "${profile.userOrder}.json")
                when (binding.fileSourceMenu.text) {
                    FileSource.CreateNew.formatted -> {
                        configFile.writeText("{}")
                    }

                    FileSource.Import.formatted -> {
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
                typedProfile.path = configFile.path
            }

            TypedProfile.Type.Remote.name -> {
                typedProfile.type = TypedProfile.Type.Remote
                val configDirectory = File(filesDir, "configs").also { it.mkdirs() }
                val configFile = File(configDirectory, "${profile.userOrder}.json")
                val remoteURL = binding.remoteURL.text
                val content = HTTPClient().use { it.getString(remoteURL) }
                Libbox.checkConfig(content)
                configFile.writeText(content)
                typedProfile.path = configFile.path
                typedProfile.remoteURL = remoteURL
                typedProfile.lastUpdated = Date()
            }
        }
        ProfileManager.create(profile)
        withContext(Dispatchers.Main) {
            binding.progressView.isVisible = false
            finish()
        }
    }

}