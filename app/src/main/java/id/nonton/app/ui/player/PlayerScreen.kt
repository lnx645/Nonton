@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package id.nonton.app.ui.player

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import id.nonton.app.R
import id.nonton.app.container
import id.nonton.app.core.MediaOption
import id.nonton.app.core.PlaybackData
import id.nonton.app.core.ServiceCatalog
import id.nonton.app.core.formatCount
import id.nonton.app.core.formatViewsAndDate
import id.nonton.app.core.sanitizeFileName
import id.nonton.app.data.DownloadEntity
import id.nonton.app.data.DownloadKind
import id.nonton.app.download.DownloadService
import id.nonton.app.ui.VideoUiModel
import id.nonton.app.ui.components.EmptyState
import id.nonton.app.ui.components.ErrorState
import id.nonton.app.ui.components.FullScreenLoading
import id.nonton.app.ui.components.VideoRow
import id.nonton.app.ui.library.CreatePlaylistDialog
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

        mediaController.setMediaItem(media.toMediaItem(data), startPosition)
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(videoHeight)
                    .background(Color.Black)
            ) {
                PlayerSurface(controller = controller, inPip = inPip)

                if (state.audioOnly && !inPip) {
                    Icon(
                        imageVector = Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(58.dp),
                    )
                }

                if (!inPip) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
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
                            .statusBarsPadding()
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

        if (!inPip) {
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
private fun PlayerSurface(controller: MediaController?, inPip: Boolean) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = !inPip
                controllerShowTimeoutMs = 3_500
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setShowNextButton(false)
                setShowPreviousButton(false)
                setRepeatToggleModes(0)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { view ->
            view.player = controller
            view.useController = !inPip
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
    onShowDownload: () -> Unit,
    onShowSave: () -> Unit,
    onShare: () -> Unit,
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "title") {
            Column {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                ServiceCatalog.byId(data.info.serviceId).color,
                                CircleShape,
                            )
                    )
                    Text(
                        text = ServiceCatalog.nameFor(data.info.serviceId),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    if (state.audioOnly) {
                        Text(
                            text = " · Audio",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (data.isLive) {
                        Text(
                            text = " · LIVE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        data.uploader?.takeIf { it.isNotBlank() }?.let { append(it) }
                        val viewsAndDate = formatViewsAndDate(
                            data.info.viewCount,
                            data.info.textualUploadDate,
                        )
                        if (viewsAndDate.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(viewsAndDate)
                        }
                        if (data.info.likeCount >= 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${formatCount(data.info.likeCount)} suka")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerActionButton(
                    icon = Icons.Outlined.Download,
                    label = stringResource(R.string.player_download),
                    onClick = onShowDownload,
                )
                PlayerActionButton(
                    icon = Icons.Outlined.BookmarkAdd,
                    label = stringResource(R.string.player_save),
                    onClick = onShowSave,
                )
                PlayerActionButton(
                    icon = Icons.Outlined.Tune,
                    label = stringResource(R.string.player_quality),
                    onClick = onShowQuality,
                )
                PlayerActionButton(
                    icon = Icons.Outlined.Share,
                    label = stringResource(R.string.player_share),
                    onClick = onShare,
                )
            }
        }

        if (data.description.isNotBlank()) {
            item(key = "description") {
                DescriptionCard(text = data.description)
            }
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
private fun PlayerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DescriptionCard(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.player_description),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (text.length > 160) {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(if (expanded) "Sembunyikan" else "Selengkapnya")
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

private fun MediaOption.toMediaItem(data: PlaybackData): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(data.title)
        .setArtist(data.uploader ?: "")
        .setArtworkUri(data.bestThumbnail?.let(Uri::parse))
        .build()

    return MediaItem.Builder()
        .setUri(url)
        .setMimeType(mimeType)
        .setMediaMetadata(metadata)
        .build()
}
