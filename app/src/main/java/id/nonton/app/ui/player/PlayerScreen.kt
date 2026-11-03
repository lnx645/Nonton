@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package id.nonton.app.ui.player

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import id.nonton.app.core.bestDateText
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import id.nonton.app.R
import id.nonton.app.container
import id.nonton.app.openChannel
import id.nonton.app.core.MediaOption
import id.nonton.app.core.PlaybackData
import id.nonton.app.core.formatCount
import id.nonton.app.core.formatDuration
import id.nonton.app.core.formatViewsAndDate
import id.nonton.app.core.sanitizeFileName
import id.nonton.app.data.DownloadEntity
import id.nonton.app.data.DownloadKind
import id.nonton.app.download.DownloadService
import id.nonton.app.ui.CommentUiModel
import id.nonton.app.ui.VideoUiModel
import id.nonton.app.ui.components.EmptyState
import id.nonton.app.ui.components.ErrorState
import id.nonton.app.ui.components.FullScreenLoading
import id.nonton.app.ui.components.VideoRow
import id.nonton.app.ui.library.CreatePlaylistDialog
import id.nonton.app.PlayerIntents
import id.nonton.app.ui.toUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.math.min

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    controller: MediaController?,
    initialPositionMs: Long,
    inPip: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectVideo: (MediaOption) -> Unit,
    onSelectAudio: (MediaOption) -> Unit,
    onOpenRelated: (VideoUiModel) -> Unit,
    onEnterPip: () -> Unit,
    onLoadMoreComments: () -> Unit,
    onRetryComments: () -> Unit,
) {
    val context = LocalContext.current
    val library = context.container.library
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showQuality by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var lastStreamUrl by remember { mutableStateOf<String?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlayingCtl by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(controller, playbackSpeed) {
        controller?.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(controller, state.url) {
        val mc = controller ?: return@LaunchedEffect
        controlsVisible = true
        isPlayingCtl = mc.isPlaying
        while (true) {
            isPlayingCtl = mc.isPlaying
            positionMs = mc.currentPosition.coerceAtLeast(0L)
            durationMs = (if (mc.duration > 0) mc.duration else 0L)
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible, isPlayingCtl, state.url) {
        if (controlsVisible && isPlayingCtl) {
            delay(3_000)
            controlsVisible = false
        }
    }

    val contextActivity = context as? Activity
    fun toggleFullscreen() {
        val activity = contextActivity ?: return
        isFullscreen = !isFullscreen
        // Status bar tetap terlihat; hanya navigation bar bawah yang disembunyikan
        // agar tidak menghalangi kontrol player (muncul lagi saat di-swipe).
        val insets = androidx.core.view.WindowCompat.getInsetsController(
            activity.window, activity.window.decorView,
        )
        if (isFullscreen) {
            insets.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            insets.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insets.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        }
        // Video mengisi layar secara penuh mengikuti orientasi landscape.
        activity.requestedOrientation = if (isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    val playlists by library.playlists.collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(controller, state.url, state.selectedMedia?.url) {
        val mediaController = controller ?: return@LaunchedEffect
        val media = state.selectedMedia ?: return@LaunchedEffect
        val data = state.data ?: return@LaunchedEffect

        val currentUri = mediaController.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri == media.url) {
            if (lastStreamUrl == null) lastStreamUrl = state.url
            return@LaunchedEffect
        }

        val startPosition = when {
            lastStreamUrl == null -> initialPositionMs
            lastStreamUrl != state.url -> 0L
            else -> mediaController.currentPosition.coerceAtLeast(0L)
        }
        lastStreamUrl = state.url

        mediaController.setMediaItem(media.toMediaItem(data, state.url), startPosition)
        mediaController.prepare()
        mediaController.play()
    }

    LaunchedEffect(state.url, state.data?.title, controller) {
        val data = state.data ?: return@LaunchedEffect
        val fallbackDuration = (data.durationSeconds * 1000L).coerceAtLeast(0L)

        suspend fun save(positionMs: Long, durationMs: Long) {
            runCatching {
                library.recordHistory(
                    streamUrl = state.url,
                    title = data.title,
                    uploader = data.uploader,
                    thumbnailUrl = data.bestThumbnail,
                    serviceId = data.info.serviceId,
                    durationSeconds = data.durationSeconds,
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = durationMs.coerceAtLeast(0L),
                )
            }
        }

        try {
            while (true) {
                delay(5_000)
                val mediaController = controller ?: continue
                val duration = mediaController.duration.takeIf { it > 0 } ?: fallbackDuration
                save(mediaController.currentPosition, duration)
            }
        } finally {
            withContext(NonCancellable) {
                val mediaController = controller
                if (mediaController != null) {
                    val duration = mediaController.duration.takeIf { it > 0 } ?: fallbackDuration
                    save(mediaController.currentPosition, duration)
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(if (inPip) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        val videoHeight = if (inPip) {
            maxHeight
        } else {
            (kotlin.math.min(maxWidth.value * 9f / 16f, maxHeight.value * 0.62f)).dp
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (isFullscreen && !inPip) {
                // Fullscreen ala YouTube: video mengisi layar, info disembunyikan.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        VideoLayer(
                            controller = controller,
                            state = state,
                            inPip = inPip,
                            underStatusBar = true,
                            controlsVisible = controlsVisible,
                            isPlaying = isPlayingCtl,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            isFullscreen = isFullscreen,
                            onToggleControls = { controlsVisible = !controlsVisible },
                            onTogglePlay = {
                                controller?.let {
                                    if (it.isPlaying) it.pause() else it.play()
                                }
                            },
                            onSeek = { ms -> controller?.seekTo(ms) },
                            onToggleFullscreen = { toggleFullscreen() },
                            onBack = onBack,
                            onEnterPip = onEnterPip,
                            onSwipeDown = onEnterPip,
                        )
                    }
                }
            } else {
                if (!inPip) {
                    // Status bar solid hitam seperti YouTube, video mulai di bawahnya.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(Color.Black),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(videoHeight)
                        .background(Color.Black),
                ) {
                    VideoLayer(
                        controller = controller,
                        state = state,
                        inPip = inPip,
                        underStatusBar = false,
                        controlsVisible = controlsVisible,
                        isPlaying = isPlayingCtl,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isFullscreen = isFullscreen,
                        onToggleControls = { controlsVisible = !controlsVisible },
                        onTogglePlay = {
                            controller?.let {
                                if (it.isPlaying) it.pause() else it.play()
                            }
                        },
                        onSeek = { ms -> controller?.seekTo(ms) },
                        onToggleFullscreen = { toggleFullscreen() },
                        onBack = onBack,
                        onEnterPip = onEnterPip,
                        onSwipeDown = onEnterPip,
                    )
                }

                if (!inPip) {
                    when {
                    state.isLoading -> Box(Modifier.fillMaxSize()) { FullScreenLoading() }
                    state.error != null -> ErrorState(
                        message = state.error.orEmpty(),
                        onRetry = onRetry,
                    )
                    state.data != null -> PlayerContent(
                        data = state.data!!,
                        state = state,
                        onOpenRelated = onOpenRelated,
                        onShowQuality = { showQuality = true },
                        onShowSpeed = { showSpeed = true },
                        onShowDownload = { showDownload = true },
                        onShowSave = { showSave = true },
                        onShare = {
                            val shareUrl = state.data?.info?.originalUrl
                                ?.takeIf { it.isNotBlank() }
                                ?: state.url
                            val sendIntent = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, shareUrl)
                            context.startActivity(
                                Intent.createChooser(
                                    sendIntent,
                                    context.getString(R.string.player_share),
                                )
                            )
                        },
                        onToggleAudio = {
                            val current = state.data ?: return@PlayerContent
                            if (state.audioOnly) {
                                val video = current.progressiveVideos.firstOrNull()
                                    ?: current.videoOptions.firstOrNull()
                                if (video != null) onSelectVideo(video)
                            } else {
                                current.preferredAudio?.let(onSelectAudio)
                            }
                        },
                        onLoadMoreComments = onLoadMoreComments,
                        onRetryComments = onRetryComments,
                    )
                    else -> Box(Modifier.fillMaxSize()) {
                        Text(
                            text = "Tidak ada media.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
    }

        if (!inPip && !isFullscreen) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp),
            )
        }
    }

    val data = state.data
    if (data != null) {
        if (showQuality) {
            QualitySheet(
                data = data,
                selectedUrl = state.selectedMedia?.url,
                audioOnly = state.audioOnly,
                onSelectVideo = {
                    onSelectVideo(it)
                    showQuality = false
                },
                onSelectAudio = {
                    onSelectAudio(it)
                    showQuality = false
                },
                onDismiss = { showQuality = false },
            )
        }

        if (showSpeed) {
            val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
            AlertDialog(
                onDismissRequest = { showSpeed = false },
                title = { Text("Kecepatan pemutaran") },
                text = {
                    Column {
                        speeds.forEach { speed ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playbackSpeed = speed
                                        controller?.setPlaybackSpeed(speed)
                                        showSpeed = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = playbackSpeed == speed,
                                    onClick = {
                                        playbackSpeed = speed
                                        controller?.setPlaybackSpeed(speed)
                                        showSpeed = false
                                    },
                                )
                                Text(
                                    text = if (speed == 1f) "Normal (1x)" else "${speed}x",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeed = false }) {
                        Text(stringResource(R.string.library_cancel))
                    }
                },
            )
        }

        if (showDownload) {
            DownloadSheet(
                data = data,
                onDownload = { option ->
                    enqueueDownload(
                        context = context,
                        data = data,
                        streamUrl = state.url,
                        option = option,
                        onQueued = {
                            showDownload = false
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.download_queued)
                                )
                            }
                        },
                        onError = { message ->
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        },
                    )
                },
                onDismiss = { showDownload = false },
            )
        }

        fun addToPlaylist(playlistId: Long, playlistName: String) {
            scope.launch {
                val added = library.addToPlaylist(
                    playlistId = playlistId,
                    streamUrl = state.url,
                    title = data.title,
                    uploader = data.uploader,
                    thumbnailUrl = data.bestThumbnail,
                    serviceId = data.info.serviceId,
                    durationSeconds = data.durationSeconds,
                )
                snackbarHostState.showSnackbar(
                    if (added) {
                        context.getString(R.string.library_added_to_playlist, playlistName)
                    } else {
                        "Video sudah ada di playlist ini."
                    }
                )
            }
        }

        if (showSave) {
            if (playlists.isEmpty()) {
                AlertDialog(
                    onDismissRequest = { showSave = false },
                    title = { Text(stringResource(R.string.library_choose_playlist)) },
                    text = { Text(stringResource(R.string.library_no_playlist)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showSave = false
                            showCreatePlaylist = true
                        }) { Text(stringResource(R.string.library_new_playlist)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSave = false }) {
                            Text(stringResource(R.string.library_cancel))
                        }
                    },
                )
            } else {
                AlertDialog(
                    onDismissRequest = { showSave = false },
                    title = { Text(stringResource(R.string.library_choose_playlist)) },
                    text = {
                        LazyColumn(modifier = Modifier.heightIn(max = 330.dp)) {
                            items(playlists, key = { it.id }) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showSave = false
                                            addToPlaylist(playlist.id, playlist.name)
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PlaylistPlay,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 12.dp)
                                    ) {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.library_items_count,
                                                playlist.itemCount,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showSave = false
                            showCreatePlaylist = true
                        }) { Text(stringResource(R.string.library_new_playlist)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSave = false }) {
                            Text(stringResource(R.string.library_cancel))
                        }
                    },
                )
            }
        }

        if (showCreatePlaylist) {
            CreatePlaylistDialog(
                onDismiss = { showCreatePlaylist = false },
                onCreate = { name ->
                    scope.launch {
                        val id = library.createPlaylist(name)
                        showCreatePlaylist = false
                        val added = library.addToPlaylist(
                            playlistId = id,
                            streamUrl = state.url,
                            title = data.title,
                            uploader = data.uploader,
                            thumbnailUrl = data.bestThumbnail,
                            serviceId = data.info.serviceId,
                            durationSeconds = data.durationSeconds,
                        )
                        snackbarHostState.showSnackbar(
                            if (added) {
                                context.getString(R.string.library_added_to_playlist, name)
                            } else {
                                "Gagal menambahkan ke playlist."
                            }
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun CompactSeekBar(
    positionMs: Long,
    durationMs: Long,
    dragValue: Float?,
    onDrag: (Float) -> Unit,
    onSeek: (Long) -> Unit,
) {
    val fraction = dragValue
        ?: (if (durationMs > 0) positionMs.toFloat() / durationMs else 0f)
            .coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .pointerInput(durationMs) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    onDrag((down.position.x / w).coerceIn(0f, 1f))
                    var done = false
                    while (!done) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            done = true
                        } else if (event.changes.all { !it.pressed }) {
                            val f = (change.position.x / w).coerceIn(0f, 1f)
                            onSeek((f * durationMs).toLong())
                            change.consume()
                            done = true
                        } else {
                            onDrag((change.position.x / w).coerceIn(0f, 1f))
                            change.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val barHeight = 3.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(barHeight)
                .background(Color(0xFFFF0000), RoundedCornerShape(2.dp)),
        )
        val density = LocalDensity.current
        val thumbSize = 10.dp
        val xOffset = with(density) {
            ((maxWidth.toPx() * fraction) - thumbSize.toPx() / 2).coerceAtLeast(0f).toDp()
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = xOffset)
                .size(thumbSize)
                .background(Color(0xFFFF0000), CircleShape),
        )
    }
}

@Composable
private fun SimplePlayerControls(
    visible: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    isLive: Boolean,
    isFullscreen: Boolean,
    onToggle: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onSwipeDown: () -> Unit,
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var swipeAccum by remember { mutableStateOf(0f) }
    var skipFlash by remember { mutableStateOf<String?>(null) }
    // Visibilitas kontrol SEBELUM toggle tap pertama: dipakai double-tap
    // untuk mengembalikan toggle tap pertama (tanpa jeda/delay).
    var tapToggleFrom by remember { mutableStateOf<Boolean?>(null) }
    val total = durationMs.coerceAtLeast(0L)

    // Ref agar gesture detector tidak restart tiap ada update posisi (500ms),
    // yang sebelumnya membatalkan tap/double-tap/swipe yang sedang berjalan.
    val toggleRef = rememberUpdatedState(onToggle)
    val seekRef = rememberUpdatedState(onSeek)
    val swipeRef = rememberUpdatedState(onSwipeDown)
    val positionRef = rememberUpdatedState(positionMs)
    val totalRef = rememberUpdatedState(total)
    val liveRef = rememberUpdatedState(isLive)
    val visibleRef = rememberUpdatedState(visible)

    LaunchedEffect(skipFlash) {
        if (skipFlash != null) {
            delay(700)
            skipFlash = null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { swipeAccum = 0f },
                    onDragCancel = { swipeAccum = 0f },
                ) { _, dragAmount ->
                    swipeAccum += dragAmount
                    if (swipeAccum > 120f) {
                        swipeAccum = 0f
                        swipeRef.value()
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    // Tap langsung toggle (instan, tanpa delay).
                    onTap = {
                        tapToggleFrom = visibleRef.value
                        toggleRef.value()
                    },
                    // Double-tap: kembalikan toggle dari tap pertama supaya
                    // kontrol tidak kedip, lalu seek. Ala YouTube.
                    onDoubleTap = { offset ->
                        if (tapToggleFrom == true) toggleRef.value()
                        tapToggleFrom = null
                        if (liveRef.value || totalRef.value <= 0) return@detectTapGestures
                        val forward = offset.x >= size.width / 2
                        val target = (positionRef.value + if (forward) 10_000 else -10_000)
                            .coerceIn(0L, totalRef.value)
                        seekRef.value(target)
                        skipFlash = if (forward) "+10 detik" else "-10 detik"
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(62.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                        ),
                    )
                    .padding(horizontal = 4.dp)
                    .padding(bottom = 2.dp),
            ) {
                if (!isLive && total > 0) {
                    CompactSeekBar(
                        positionMs = positionMs,
                        durationMs = total,
                        dragValue = dragValue,
                        onDrag = { dragValue = it },
                        onSeek = {
                            onSeek(it)
                            dragValue = null
                        },
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp),
                ) {
                    IconButton(onClick = onTogglePlay, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    if (isLive) {
                        Text(
                            text = stringResource(R.string.live),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .background(Color(0xFFFF0000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    } else {
                        Text(
                            text = if (total > 0) {
                                "${formatDuration(positionMs / 1000)} / ${formatDuration(total / 1000)}"
                            } else {
                                formatDuration(positionMs / 1000)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Outlined.FullscreenExit
                            else Icons.Outlined.Fullscreen,
                            contentDescription = "Layar penuh",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            } // tutup Box + AnimatedVisibility kontrol
        }
        skipFlash?.let { flash ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.65f),
            ) {
                Text(
                    text = flash,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun VideoLayer(
    controller: MediaController?,
    state: PlayerUiState,
    inPip: Boolean,
    underStatusBar: Boolean,
    controlsVisible: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    isFullscreen: Boolean,
    onToggleControls: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onEnterPip: () -> Unit,
    onSwipeDown: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PlayerSurface(controller = controller, inPip = inPip)

    if (!inPip) {
        SimplePlayerControls(
            visible = controlsVisible,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            isLive = state.data?.isLive == true,
            isFullscreen = isFullscreen,
            onToggle = onToggleControls,
            onTogglePlay = onTogglePlay,
            onSeek = onSeek,
            onToggleFullscreen = onToggleFullscreen,
            onSwipeDown = onSwipeDown,
        )
    }

    if (state.audioOnly && !inPip) {
        Icon(
            imageVector = Icons.Outlined.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 72.dp)
                .size(58.dp),
        )
    }

    if (!inPip) {
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
        // Top scrim for readable overlay buttons
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(88.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Transparent,
                        )
                    )
                )
        )

        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .then(if (underStatusBar) Modifier.statusBarsPadding() else Modifier)
                .padding(10.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .then(if (underStatusBar) Modifier.statusBarsPadding() else Modifier)
                .padding(10.dp),
        ) {
            IconButton(onClick = onEnterPip) {
                Icon(
                    imageVector = Icons.Outlined.PictureInPictureAlt,
                    contentDescription = stringResource(R.string.player_pip),
                    tint = Color.White,
                )
            }
        }
            }
        }
        // tutup Box pembungkus isi video
        }
    }
}

@Composable
private fun PlayerSurface(controller: MediaController?, inPip: Boolean) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setShowNextButton(false)
                setShowPreviousButton(false)
                setRepeatToggleModes(0)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { view ->
            view.player = controller
            view.useController = false
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun PlayerContent(
    data: PlaybackData,
    state: PlayerUiState,
    onOpenRelated: (VideoUiModel) -> Unit,
    onShowQuality: () -> Unit,
    onShowSpeed: () -> Unit,
    onShowDownload: () -> Unit,
    onShowSave: () -> Unit,
    onShare: () -> Unit,
    onToggleAudio: () -> Unit,
    onLoadMoreComments: () -> Unit,
    onRetryComments: () -> Unit,
) {
    val related = remember(data) {
        data.info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { it.toUiModel() }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(state.url) {
        listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            YouTubeHeader(
                data = data,
                state = state,
                onShowQuality = onShowQuality,
            )
        }

        item(key = "actions") {
            YouTubeActionRow(
                data = data,
                state = state,
                onShowQuality = onShowQuality,
                onShowSpeed = onShowSpeed,
                onShowDownload = onShowDownload,
                onShowSave = onShowSave,
                onShare = onShare,
                onToggleAudio = onToggleAudio,
            )
        }

        if (data.description.isNotBlank() || data.isLive) {
            item(key = "description") {
                YouTubeDescriptionCard(data = data)
            }
        }

        item(key = "comments") {
            CommentsSection(
                state = state,
                onLoadMore = onLoadMoreComments,
                onRetry = onRetryComments,
            )
        }

        if (related.isNotEmpty()) {
            item(key = "related-header") {
                Text(
                    text = stringResource(R.string.player_related),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            items(related, key = { it.url }) { video ->
                VideoRow(
                    video = video,
                    onClick = { onOpenRelated(video) },
                )
            }
        }
    }
}

@Composable
private fun YouTubeHeader(
    data: PlaybackData,
    state: PlayerUiState,
    onShowQuality: () -> Unit,
) {
    var titleExpanded by rememberSaveable(state.url) { mutableStateOf(false) }
    val isLive = data.isLive
    val context = LocalContext.current
    val uploadInstant = remember(data) {
        runCatching { data.info.uploadDate?.offsetDateTime()?.toInstant() }.getOrNull()
    }
    val idDate = remember(data) {
        bestDateText(data.info.textualUploadDate, uploadInstant)
    }

    Column {
        if (isLive) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LiveBadge()
                val watchers = data.info.viewCount
                Text(
                    text = if (watchers >= 0) {
                        "${formatCount(watchers)} menonton"
                    } else {
                        "Sedang live"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = data.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = if (titleExpanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { titleExpanded = !titleExpanded },
        )
        if (state.audioOnly && !isLive) {
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.player_audio_mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val channelUrl = data.uploaderUrl
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !channelUrl.isNullOrBlank()) {
                        if (!channelUrl.isNullOrBlank()) {
                            context.openChannel(channelUrl, data.info.serviceId)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChannelAvatar(name = data.uploader)
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp),
                ) {
                Text(
                    text = data.uploader?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.unknown_uploader),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                val subtitle = if (isLive) {
                    idDate.ifBlank { "Streaming langsung" }
                } else {
                    formatViewsAndDate(data.info.viewCount, idDate)
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
            if (!isLive) {
                Surface(
                    onClick = onShowQuality,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(17.dp),
                        )
                        Text(
                            text = state.selectedLabel
                                .substringBefore("·").trim()
                                .ifBlank { stringResource(R.string.player_quality) },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = state.selectedLabel.ifBlank { "Live" },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelAvatar(name: String?) {
    val initial = name?.trim()?.firstOrNull()?.uppercase() ?: "N"
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun LiveBadge() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFFE53935),
    ) {
        Text(
            text = stringResource(R.string.live),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun YouTubeActionRow(
    data: PlaybackData,
    state: PlayerUiState,
    onShowQuality: () -> Unit,
    onShowSpeed: () -> Unit,
    onShowDownload: () -> Unit,
    onShowSave: () -> Unit,
    onShare: () -> Unit,
    onToggleAudio: () -> Unit,
) {
    val isLive = data.isLive
    val canDownload = !isLive && data.progressiveVideos.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlayerActionButton(
            icon = Icons.Outlined.Share,
            label = stringResource(R.string.player_share),
            onClick = onShare,
        )
        if (canDownload) {
            PlayerActionButton(
                icon = Icons.Outlined.Download,
                label = stringResource(R.string.player_download),
                onClick = onShowDownload,
            )
        }
        PlayerActionButton(
            icon = Icons.Outlined.BookmarkAdd,
            label = stringResource(R.string.player_save),
            onClick = onShowSave,
        )
        if (data.audioOptions.isNotEmpty()) {
            PlayerActionButton(
                icon = Icons.Outlined.MusicNote,
                label = "Audio",
                onClick = onToggleAudio,
                selected = state.audioOnly,
            )
        }
        if (!isLive) {
            PlayerActionButton(
                icon = Icons.Outlined.Speed,
                label = "Kecepatan",
                onClick = onShowSpeed,
            )
            PlayerActionButton(
                icon = Icons.Outlined.Tune,
                label = stringResource(R.string.player_quality),
                onClick = onShowQuality,
            )
        }
    }
    if (isLive) {
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Streaming langsung — kualitas mengikuti jaringan, kecepatan & unduh nonaktif.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlayerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun YouTubeDescriptionCard(data: PlaybackData) {
    var expanded by rememberSaveable(data.info.url) { mutableStateOf(false) }
    val isLive = data.isLive
    val idDate = remember(data) {
        bestDateText(
            data.info.textualUploadDate,
            runCatching { data.info.uploadDate?.offsetDateTime()?.toInstant() }.getOrNull(),
        )
    }
    val metaLine = if (isLive) {
        buildString {
            if (data.info.viewCount >= 0) {
                append("${formatCount(data.info.viewCount)} menonton")
            }
            if (idDate.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(idDate)
            }
            if (isEmpty()) append("Streaming langsung")
        }
    } else {
        buildString {
            append(formatViewsAndDate(data.info.viewCount, idDate))
            if (data.info.likeCount >= 0) {
                if (isNotEmpty()) append("  ")
                append("·  ${formatCount(data.info.likeCount)} suka")
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = metaLine,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (data.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = data.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (isLive) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Streaming langsung. Ketuk untuk membuka kualitas adaptif yang tersedia.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (expanded) "Tutup" else "...selengkapnya",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun CommentsSection(
    state: PlayerUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    var showAll by rememberSaveable(state.url) { mutableStateOf(false) }
    val isLive = state.data?.isLive == true

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isLive) "Live chat" else "Komentar",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!isLive && state.commentsCount >= 0) {
                Text(
                    text = " (${formatCount(state.commentsCount.toLong())})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            state.commentsLoading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
            state.commentsDisabled -> Text(
                text = if (isLive) {
                    "Live chat tidak tersedia untuk siaran ini."
                } else {
                    "Komentar dinonaktifkan untuk video ini."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.commentsError != null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = state.commentsError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            state.comments.isEmpty() -> Text(
                text = "Belum ada komentar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                state.comments.take(2).forEach { comment ->
                    CommentRow(comment = comment)
                    Spacer(Modifier.height(10.dp))
                }
                TextButton(
                    onClick = { showAll = true },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = if (isLive) {
                            "Lihat semua chat"
                        } else if (state.commentsCount > 2) {
                            "Lihat semua ${state.commentsCount} komentar"
                        } else {
                            "Lihat semua komentar"
                        },
                    )
                }
            }
        }
    }

    if (showAll) {
        CommentsSheet(
            state = state,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            onDismiss = { showAll = false },
        )
    }
}

@Composable
private fun CommentsSheet(
    state: PlayerUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isLive = state.data?.isLive == true

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Text(
                text = if (isLive) {
                    "Live chat"
                } else if (state.commentsCount >= 0) {
                    "Komentar (${formatCount(state.commentsCount.toLong())})"
                } else {
                    "Komentar"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.comments, key = { it.id }) { comment ->
                    CommentRow(comment = comment, fullText = true)
                }
                if (state.commentsError != null) {
                    item(key = "comments-error") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = state.commentsError.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                        }
                    }
                }
                if (state.commentsLoadingMore) {
                    item(key = "comments-loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(26.dp))
                        }
                    }
                } else if (state.commentsNextPage != null) {
                    item(key = "comments-more") {
                        TextButton(onClick = onLoadMore) {
                            Text(stringResource(R.string.load_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommentUiModel,
    fullText: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!comment.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = comment.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = comment.author.trim().firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (comment.dateText.isNotBlank()) {
                    Text(
                        text = " · ${comment.dateText}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (comment.pinned) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        text = "Disematkan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = comment.text.ifBlank { "(komentar kosong)" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (fullText) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (comment.likeCount >= 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ThumbUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = formatCount(comment.likeCount.toLong()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (comment.hearted) {
                        Text(
                            text = "♥ oleh kreator",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QualitySheet(
    data: PlaybackData,
    selectedUrl: String?,
    audioOnly: Boolean,
    onSelectVideo: (MediaOption) -> Unit,
    onSelectAudio: (MediaOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.player_quality),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 430.dp)) {
                if (data.videoOptions.isEmpty()) {
                    item {
                        Text(
                            text = "Tidak ada pilihan video.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                items(data.videoOptions, key = { it.url }) { option ->
                    OptionRow(
                        label = option.label,
                        subtitle = if (option.adaptive) "Adaptif" else null,
                        selected = !audioOnly && option.url == selectedUrl,
                        onClick = { onSelectVideo(option) },
                    )
                }
                if (data.audioOptions.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        Text(
                            text = stringResource(R.string.player_audio_only),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    items(data.audioOptions, key = { it.url }) { option ->
                        OptionRow(
                            label = option.label,
                            subtitle = "Audio",
                            selected = audioOnly && option.url == selectedUrl,
                            onClick = { onSelectAudio(option) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DownloadSheet(
    data: PlaybackData,
    onDownload: (MediaOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.download_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.download_video)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.download_audio)) },
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                if (tab == 0) {
                    if (data.progressiveVideos.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.download_no_video),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                    items(data.progressiveVideos, key = { it.url }) { option ->
                        DownloadOptionRow(option = option, onClick = { onDownload(option) })
                    }
                } else {
                    if (data.audioOptions.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.download_no_audio),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                    items(data.audioOptions, key = { it.url }) { option ->
                        DownloadOptionRow(option = option, onClick = { onDownload(option) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadOptionRow(option: MediaOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = option.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = option.mimeType,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.Download,
            contentDescription = stringResource(R.string.download_start),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun enqueueDownload(
    context: android.content.Context,
    data: PlaybackData,
    streamUrl: String,
    option: MediaOption,
    onQueued: () -> Unit,
    onError: (String) -> Unit,
) {
    val kind = if (option.audioOnly) DownloadKind.AUDIO else DownloadKind.VIDEO
    val extension = extensionForMime(option.mimeType)
    val fileName = "${sanitizeFileName(data.title)}-${sanitizeFileName(option.label)}.$extension"

    val entity = DownloadEntity(
        streamUrl = streamUrl,
        title = data.title,
        uploader = data.uploader,
        thumbnailUrl = data.bestThumbnail,
        serviceId = data.info.serviceId,
        kind = kind,
        qualityLabel = option.label,
        mediaUrl = option.url,
        mimeType = option.mimeType,
        fileName = fileName,
    )

    val scope = kotlinx.coroutines.MainScope()
    scope.launch {
        try {
            context.container.library.enqueueDownload(entity)
            DownloadService.start(context)
            onQueued()
        } catch (e: Exception) {
            onError(e.message ?: "Gagal memulai unduhan.")
        } finally {
            scope.cancel()
        }
    }
}

private fun extensionForMime(mime: String): String = when (mime) {
    "video/mp4" -> "mp4"
    "video/webm" -> "webm"
    "video/3gpp" -> "3gp"
    "audio/mp4" -> "m4a"
    "audio/webm" -> "webm"
    "audio/mpeg" -> "mp3"
    "audio/ogg" -> "ogg"
    "audio/opus" -> "opus"
    "audio/flac" -> "flac"
    else -> "bin"
}

private fun MediaOption.toMediaItem(data: PlaybackData, streamUrl: String): MediaItem {
    val extras = android.os.Bundle().apply {
        putString(PlayerIntents.EXTRA_URL, streamUrl)
        putInt(PlayerIntents.EXTRA_SERVICE_ID, data.info.serviceId)
        putString(PlayerIntents.EXTRA_TITLE, data.title)
        putString(PlayerIntents.EXTRA_THUMBNAIL, data.bestThumbnail)
        putString(PlayerIntents.EXTRA_UPLOADER, data.uploader)
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(data.title)
        .setArtist(data.uploader ?: "")
        .setArtworkUri(data.bestThumbnail?.let(Uri::parse))
        .setExtras(extras)
        .build()

    return MediaItem.Builder()
        .setUri(url)
        .setMimeType(mimeType)
        .setMediaMetadata(metadata)
        .build()
}
