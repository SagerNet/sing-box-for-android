package io.nekohasekai.sfa.ui.debug

import android.content.Intent
import android.os.Bundle
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.databinding.ActivityDebugBinding
import io.nekohasekai.sfa.ui.shared.AbstractActivity

class DebugActivity : AbstractActivity<ActivityDebugBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.title_debug)
        binding.scanVPNButton.setOnClickListener {
            startActivity(Intent(this, VPNScanActivity::class.java))
        }
    }
}