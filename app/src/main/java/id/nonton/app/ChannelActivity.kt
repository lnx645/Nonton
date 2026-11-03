package id.nonton.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.nonton.app.ui.channel.ChannelScreen
import id.nonton.app.ui.channel.ChannelViewModel
import id.nonton.app.ui.theme.NontonTheme

class ChannelActivity : ComponentActivity() {

    private val viewModel: ChannelViewModel by viewModels {
        ChannelViewModel.factory(
            applicationContext.container.extractor,
            intent.getStringExtra(ChannelIntents.EXTRA_URL).orEmpty(),
            intent.getIntExtra(ChannelIntents.EXTRA_SERVICE_ID, 0),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (intent.getStringExtra(ChannelIntents.EXTRA_URL).isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            val container = applicationContext.container
            val themeMode by container.settings.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by container.settings.dynamicColor.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            NontonTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                ChannelScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                )
            }
        }
    }
}
