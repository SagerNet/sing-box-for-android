package io.nekohasekai.sfa

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.nekohasekai.sfa.compose.ComposeActivity

class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launchIntent =
            Intent(this, ComposeActivity::class.java).apply {
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
