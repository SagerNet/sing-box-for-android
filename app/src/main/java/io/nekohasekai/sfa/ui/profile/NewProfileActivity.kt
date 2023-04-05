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
import io.nekohasekai.sfa.database.Profiles
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

class NewProfileActivity : AbstractActivity() {
    enum class FileSource(val formatted: String) {
        CreateNew("Create New"),
        Import("Import");
    }

    private var _binding: ActivityAddProfileBinding? = null
    private val binding get() = _binding!!

    private val importFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { fileURI ->
            if (fileURI != null) {
                binding.fileURL.editText?.setText(fileURI.toString())
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.title_new_profile)
        _binding = ActivityAddProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.name.removeErrorIfNotEmpty()
        binding.type.addTextChangedListener {
            when (it) {
                TypedProfile.Type.Local.name -> {
                    binding.localFields.isVisible = true
                }

                TypedProfile.Type.Remote.name -> {
                    binding.localFields.isVisible = false
                }
            }
        }
        binding.fileSourceMenu.addTextChangedListener {
            when (it) {
                FileSource.CreateNew.formatted -> {
                    binding.importFileButton.isVisible = false
                    binding.fileURL.isVisible = false
                }

                FileSource.Import.formatted -> {
                    binding.importFileButton.isVisible = true
                    binding.fileURL.isVisible = true
                }
            }
        }
        binding.importFileButton.setOnClickListener {
            startFilesForResult(importFile, "application/json")
        }
        binding.createProfile.setOnClickListener(this::createProfile)
    }

    private fun createProfile(view: View) {
        if (binding.name.showErrorIfEmpty()) {
            return
        }
        when (binding.type.text) {
            TypedProfile.Type.Local.name -> {
                when (binding.fileSourceMenu.text) {
                    FileSource.Import.formatted -> {
                        if (binding.fileURL.showErrorIfEmpty()) {
                            return
                        }
                    }
                }

            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                createProfile0()
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    errorDialogBuilder(e).show()
                }
            }
        }
    }

    private suspend fun createProfile0() {
        val typedProfile = TypedProfile()
        val profile = Profile(name = binding.name.text, typed = typedProfile)
        profile.userOrder = Profiles.nextOrder()
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
                        val sourceURL = binding.fileURL.text
                        val content: String
                        if (sourceURL.startsWith("content://")) {
                            val inputStream =
                                contentResolver.openInputStream(Uri.parse(sourceURL)) as InputStream
                            content = inputStream.use { it.bufferedReader().readText() }
                        } else if (sourceURL.startsWith("file://")) {
                            content = File(sourceURL).readText()
                        } else if (sourceURL.startsWith("http://")) {
                            content = HTTPClient().use { it.getString(sourceURL) }
                        } else {
                            error("unsupported source: $sourceURL")
                        }

                        Libbox.checkConfig(content)
                        configFile.writeText(content)
                    }
                }
                typedProfile.path = configFile.path
            }
        }
        Profiles.create(profile)
        withContext(Dispatchers.Main) {
            finish()
        }
    }

}