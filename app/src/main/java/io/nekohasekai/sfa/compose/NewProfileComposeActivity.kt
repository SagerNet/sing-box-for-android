package io.nekohasekai.sfa.compose

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.nekohasekai.sfa.compose.screen.configuration.NewProfileScreen
import io.nekohasekai.sfa.compose.theme.SFATheme

class NewProfileComposeActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_IMPORT_NAME = "import_name"
        const val EXTRA_IMPORT_URL = "import_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val importName = intent.getStringExtra(EXTRA_IMPORT_NAME)
        val importUrl = intent.getStringExtra(EXTRA_IMPORT_URL)

        setContent {
            SFATheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    NewProfileScreen(
                        importName = importName,
                        importUrl = importUrl,
                        onNavigateBack = { finish() },
                        onProfileCreated = { profileId ->
                            val resultIntent =
                                Intent().apply {
                                    putExtra(EXTRA_PROFILE_ID, profileId)
                                }
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        },
                    )
                }
            }
        }
    }
}
