package io.nekohasekai.sfa.ui.profileoverride

import android.content.Intent
import android.os.Bundle
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.databinding.ActivityConfigOverrideBinding
import io.nekohasekai.sfa.ui.shared.AbstractActivity

class ProfileOverrideActivity :
    AbstractActivity<ActivityConfigOverrideBinding>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.profile_override)
        binding.switchPerAppProxy.isChecked = Settings.perAppProxyEnabled
        binding.switchPerAppProxy.setOnCheckedChangeListener { _, isChecked ->
            Settings.perAppProxyEnabled = isChecked
            binding.configureAppListButton.isEnabled = isChecked
        }
        binding.configureAppListButton.isEnabled = binding.switchPerAppProxy.isChecked

        binding.configureAppListButton.setOnClickListener {
            startActivity(Intent(this, PerAppProxyActivity::class.java))
        }
    }
}
