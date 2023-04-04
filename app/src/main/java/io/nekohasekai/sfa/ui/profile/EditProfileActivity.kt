package io.nekohasekai.sfa.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.Profiles
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.databinding.ActivityEditProfileBinding
import io.nekohasekai.sfa.ktx.addTextChangedListener
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ktx.text
import io.nekohasekai.sfa.ui.shared.AbstractActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditProfileActivity : AbstractActivity() {

    private var _binding: ActivityEditProfileBinding? = null
    private val binding get() = _binding!!
    private var _profile: Profile? = null
    private val profile get() = _profile!!
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.title_edit_profile)
        _binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                loadProfile()
            }.onFailure {
                errorDialogBuilder(it)
                    .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                    .show()
            }
        }
    }

    private suspend fun loadProfile() {
        delay(200L)

        val profileId = intent.getLongExtra("profile_id", -1L)
        if (profileId == -1L) error("invalid arguments")
        _profile = Profiles.getProfile(profileId) ?: error("invalid arguments")
        withContext(Dispatchers.Main) {
            binding.name.text = profile.name
            binding.name.addTextChangedListener {
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        profile.name = it
                        Profiles.updateProfile(profile)
                    } catch (e: Exception) {
                        errorDialogBuilder(e).show()
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
                }

                else -> {}
            }
            binding.checkButton.setOnClickListener(this@EditProfileActivity::checkProfile)
            binding.profileLayout.isVisible = true
            binding.progressView.isVisible = false
        }
    }

    private fun checkProfile(button: View) {
        binding.progressView.isVisible = true
        GlobalScope.launch(Dispatchers.IO) {
            delay(200)
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

}