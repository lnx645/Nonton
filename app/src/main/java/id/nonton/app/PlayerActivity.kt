package id.nonton.app

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import id.nonton.app.playback.PlaybackService
import id.nonton.app.ui.player.PlayerScreen
import id.nonton.app.ui.player.PlayerViewModel
import id.nonton.app.ui.theme.NontonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlayerActivity : ComponentActivity() {

    private val settings get() = applicationContext.container.settings
    private val library get() = applicationContext.container.library

    private val viewModel: PlayerViewModel by viewModels {
        PlayerViewModel.factory(
            applicationContext.container.extractor,
            applicationContext.container.settings,
            intent.getStringExtra(PlayerIntents.EXTRA_URL).orEmpty(),
            intent.getIntExtra(PlayerIntents.EXTRA_SERVICE_ID, 0),
        )
    }

    private var controller by mutableStateOf<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var inPip by mutableStateOf(false)
    private var isPlaying = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            if (playing) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(
                this@PlayerActivity,
                "Pemutaran gagal: ${error.errorCodeName}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(PlayerIntents.EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }

        val initialPosition = intent.getLongExtra(PlayerIntents.EXTRA_START_POSITION, 0L)
        connectController()

        setContent {
            val container = applicationContext.container
            val themeMode by container.settings.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by container.settings.dynamicColor.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }
            NontonTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                PlayerScreen(
                    state = state,
                    controller = controller,
                    initialPositionMs = initialPosition,
                    inPip = inPip,
                    onBack = { finish() },
                    onRetry = viewModel::retry,
                    onSelectVideo = viewModel::selectVideo,
                    onSelectAudio = viewModel::selectAudio,
                    onOpenRelated = { video -> viewModel.open(video.url, video.serviceId) },
                    onEnterPip = { enterPipIfPossible() },
                    onLoadMoreComments = viewModel::loadMoreComments,
                    onRetryComments = viewModel::retryComments,
                )
            }
        }
    }

    private fun connectController() {
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { mediaController ->
                    controller = mediaController
                    mediaController.addListener(playerListener)
                    isPlaying = mediaController.isPlaying
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = intent.getStringExtra(PlayerIntents.EXTRA_URL) ?: return
        val serviceId = intent.getIntExtra(PlayerIntents.EXTRA_SERVICE_ID, 0)
        if (url.isNotBlank()) {
            viewModel.open(url, serviceId)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (settings.pipEnabled.value && isPlaying) {
            enterPipIfPossible()
        }
    }

    private fun enterPipIfPossible() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast("Picture-in-Picture butuh Android 8+")
            return
        }
        if (isInPictureInPictureMode) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            toast("HP ini tidak mendukung Picture-in-Picture")
            return
        }
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        val ok = runCatching { enterPictureInPictureMode(params) }.getOrDefault(false)
        android.util.Log.d("PlayerActivity", "enterPictureInPictureMode result=$ok")
        if (!ok) toast("Gagal masuk mode PiP")
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        val controllerCompat = WindowCompat.getInsetsController(window, window.decorView)
        controllerCompat.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isInPictureInPictureMode) {
            controllerCompat.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controllerCompat.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onStop() {
        super.onStop()
        recordHistory()
    }

    private fun recordHistory() {
        val state = viewModel.state.value
        val data = state.data ?: return
        val position = controller?.currentPosition ?: 0L
        val duration = controller?.duration?.takeIf { it > 0 }
            ?: (data.durationSeconds * 1000L)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                library.recordHistory(
                    streamUrl = state.url,
                    title = data.title,
                    uploader = data.uploader,
                    thumbnailUrl = data.bestThumbnail,
                    serviceId = state.serviceId,
                    durationSeconds = data.durationSeconds,
                    positionMs = position,
                    durationMs = duration,
                )
            }
        }
    }

    override fun onDestroy() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}
