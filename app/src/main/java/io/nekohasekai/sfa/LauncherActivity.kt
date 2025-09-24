package io.nekohasekai.sfa

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.nekohasekai.sfa.compose.ComposeActivity
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.ui.MainActivity
import kotlinx.coroutines.runBlocking

class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val useComposeUI =
            runBlocking {
                Settings.useComposeUI
            }

        val targetActivity =
            if (useComposeUI) {
                ComposeActivity::class.java
            } else {
                MainActivity::class.java
            }

        val launchIntent =
            Intent(this, targetActivity).apply {
                // Transfer any intent data from launcher
                intent?.let {
                    action = it.action
                    data = it.data
                    it.extras?.let { extras -> putExtras(extras) }
                }
            }

        startActivity(launchIntent)
        finish()
    }
}
