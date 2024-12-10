package io.nekohasekai.sfa.ui.profile

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.blacksquircle.ui.language.json.JsonLanguage
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.databinding.ActivityEditProfileContentBinding
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ktx.unwrap
import io.nekohasekai.sfa.ui.shared.AbstractActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditProfileContentActivity : AbstractActivity<ActivityEditProfileContentBinding>() {

    private var profile: Profile? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.title_edit_configuration)
        binding.editor.language = JsonLanguage()
        loadConfiguration()
    }

    private fun loadConfiguration() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                loadConfiguration0()
            }.onFailure {
                withContext(Dispatchers.Main) {
                    errorDialogBuilder(it)
                        .setPositiveButton(R.string.ok) { _, _ -> finish() }
                        .show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.edit_configutation_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_undo -> {
                if (binding.editor.canUndo()) binding.editor.undo()
                return true
            }

            R.id.action_redo -> {
                if (binding.editor.canRedo()) binding.editor.redo()
                return true
            }

            R.id.action_check -> {
                binding.progressView.isVisible = true
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching {
                        Libbox.checkConfig(binding.editor.text.toString())
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            errorDialogBuilder(it).show()
                        }
                    }
                    withContext(Dispatchers.Main) {
                        delay(200)
                        binding.progressView.isInvisible = true
                    }
                }
                return true
            }

            R.id.action_format -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching {
                        val content = Libbox.formatConfig(binding.editor.text.toString()).unwrap
                        if (binding.editor.text.toString() != content) {
                            withContext(Dispatchers.Main) {
                                binding.editor.setTextContent(content)
                            }
                        }
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            errorDialogBuilder(it).show()
                        }
                    }
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private suspend fun loadConfiguration0() {
        delay(200L)

        val profileId = intent.getLongExtra("profile_id", -1L)
        if (profileId == -1L) error("invalid arguments")
        val profile = ProfileManager.get(profileId) ?: error("invalid arguments")
        this.profile = profile
        val content = File(profile.typed.path).readText()
        withContext(Dispatchers.Main) {
            binding.editor.setTextContent(content)
            binding.editor.addTextChangedListener {
                binding.progressView.isVisible = true
                val newContent = it.toString()
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching {
                        File(profile.typed.path).writeText(newContent)
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            errorDialogBuilder(it)
                                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                                .show()
                        }
                    }
                    withContext(Dispatchers.Main) {
                        delay(200L)
                        binding.progressView.isInvisible = true
                    }
                }
            }
            binding.progressView.isInvisible = true
        }
    }

}