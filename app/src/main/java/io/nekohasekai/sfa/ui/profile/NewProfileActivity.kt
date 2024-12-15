package io.nekohasekai.sfa.ui.profile

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
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
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class NewProfileActivity : AbstractActivity() {
    enum class FileSource(val formatted: String) {
        CreateNew("Create New"),
        Import("Import");
    }
    companion object {
        const val BASE_URL = "https://raw.githubusercontent.com/rtlvpn/junk/main/"
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

        intent.getStringExtra("importName")?.also { importName ->
            intent.getStringExtra("importURL")?.also { importURL ->
                binding.name.editText?.setText(importName)
                binding.type.text = TypedProfile.Type.Remote.name
                binding.remoteURL.editText?.setText(importURL)
                binding.localFields.isVisible = false
                binding.remoteFields.isVisible = true
                binding.autoUpdateInterval.text = "60"
            }
        }

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
                    if (binding.autoUpdateInterval.text.toIntOrNull() == null) {
                        binding.autoUpdateInterval.text = "60"
                    }
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
        binding.autoUpdateInterval.addTextChangedListener(this::updateAutoUpdateInterval)
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
        val fileID = ProfileManager.nextFileID()
        val configDirectory = File(filesDir, "configs").also { it.mkdirs() }
        val configFile = File(configDirectory, "$fileID.json")
        typedProfile.path = configFile.path

        when (binding.type.text) {
            TypedProfile.Type.Local.name -> {
                typedProfile.type = TypedProfile.Type.Local

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
            }

            TypedProfile.Type.Remote.name -> {
                typedProfile.type = TypedProfile.Type.Remote
                val secretText = binding.remoteURL.text // User enters the secret text here
                val md5Hash = md5(secretText)
                val fileURL = "$BASE_URL$md5Hash"
                // Add your secret key here
                val secretKey = "vealcalmbeatherofulldame"  // replace with your key
                // Check if the file exists
                val url = URL(fileURL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                val fileExists = connection.responseCode == HttpURLConnection.HTTP_OK
                if (fileExists) {
                    val encryptedContent = HTTPClient().use { it.getString(fileURL) }

                    // The key is now the secret text itself
                    val keySpec = SecretKeySpec(secretText.toByteArray(), "AES")
                    // Decrypt the AES
                    val json = JSONObject(encryptedContent)
                    val iv = Base64.decode(json.getString("iv"), Base64.DEFAULT)
                    val ct = Base64.decode(json.getString("ciphertext"), Base64.DEFAULT)
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    val ivSpec = IvParameterSpec(iv)
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                    val decryptedContent = String(cipher.doFinal(ct))
                    Libbox.checkConfig(decryptedContent)
                    configFile.writeText(decryptedContent)
                    typedProfile.remoteURL = fileURL
                typedProfile.lastUpdated = Date()
                typedProfile.autoUpdate = EnabledType.valueOf(binding.autoUpdate.text).boolValue
                binding.autoUpdateInterval.text.toIntOrNull()?.also {
                    typedProfile.autoUpdateInterval = it
                    }
                } else {
                    // Handle the case where the file does not exist
                    // ...
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
    }

    // Helper function to generate MD5 hash
    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.fold("", { str, it -> str + "%02x".format(it) })
    }
}