package io.nekohasekai.sfa.ui.profileoverride

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.constant.PerAppProxyUpdateType
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.databinding.ActivityConfigOverrideBinding
import io.nekohasekai.sfa.ktx.addTextChangedListener
import io.nekohasekai.sfa.ktx.setSimpleItems
import io.nekohasekai.sfa.ktx.text
import io.nekohasekai.sfa.ui.shared.AbstractActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileOverrideActivity :
    AbstractActivity<ActivityConfigOverrideBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.title_profile_override)
        binding.switchPerAppProxy.isChecked = Settings.perAppProxyEnabled
        binding.switchPerAppProxy.setOnCheckedChangeListener { _, isChecked ->
            Settings.perAppProxyEnabled = isChecked
            binding.perAppProxyUpdateOnChange.isEnabled = binding.switchPerAppProxy.isChecked
            binding.configureAppListButton.isEnabled = isChecked
        }
        binding.perAppProxyUpdateOnChange.isEnabled = binding.switchPerAppProxy.isChecked
        binding.configureAppListButton.isEnabled = binding.switchPerAppProxy.isChecked

        binding.perAppProxyUpdateOnChange.addTextChangedListener {
            lifecycleScope.launch(Dispatchers.IO) {
                Settings.perAppProxyUpdateOnChange =
                    PerAppProxyUpdateType.valueOf(this@ProfileOverrideActivity, it).value()
            }
        }

        binding.configureAppListButton.setOnClickListener {
            startActivity(Intent(this, PerAppProxyActivity::class.java))
        }
        lifecycleScope.launch(Dispatchers.IO) {
            reloadSettings()
        }
    }

    private suspend fun reloadSettings() {
        val perAppUpdateOnChange = Settings.perAppProxyUpdateOnChange
        withContext(Dispatchers.Main) {
            binding.perAppProxyUpdateOnChange.text =
                PerAppProxyUpdateType.valueOf(perAppUpdateOnChange)
                    .getString(this@ProfileOverrideActivity)
            binding.perAppProxyUpdateOnChange.setSimpleItems(R.array.per_app_proxy_update_on_change_value)
        }
    }
}