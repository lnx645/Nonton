package id.nonton.app.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.nonton.app.R
import id.nonton.app.core.formatBytes
import id.nonton.app.data.DownloadEntity
import id.nonton.app.data.DownloadStatus
import id.nonton.app.openPlayer
import id.nonton.app.ui.components.EmptyState
import id.nonton.app.ui.components.PlaylistCover
import id.nonton.app.ui.components.VideoRow
import id.nonton.app.ui.components.VideoThumbnail
import id.nonton.app.ui.toUiModel

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenPlaylist: (Long) -> Unit,
) {
    val history by viewModel.history.collectAsStateWithLifecycle(emptyList())
    val playlists by viewModel.playlists.collectAsStateWithLifecycle(emptyList())
    val downloads by viewModel.downloads.collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showNewPlaylist by remember { mutableStateOf(false) }
    var showClearHistory by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.nav_library),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.library_history)) },
                icon = { Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(20.dp)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.library_playlists)) },
                icon = { Icon(Icons.Outlined.PlaylistPlay, contentDescription = null, modifier = Modifier.size(20.dp)) },
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(stringResource(R.string.library_downloads)) },
                icon = { Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(20.dp)) },
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> HistoryTab(
                    history = history,
                    onOpen = { item -> context.openPlayer(item.toUiModel(), item.positionMs) },
                    onDelete = viewModel::deleteHistory,
                    onClear = { showClearHistory = true },
                )
                1 -> PlaylistsTab(
                    playlists = playlists,
                    onCreate = { showNewPlaylist = true },
                    onOpen = onOpenPlaylist,
                    onDelete = viewModel::deletePlaylist,
                )
                else -> DownloadsTab(
                    downloads = downloads,
                    onCancel = viewModel::cancelDownload,
                    onDelete = viewModel::deleteDownload,
                    onOpen = { download ->
                        download.localUri?.let { rawUri ->
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(rawUri), download.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(intent) }
                        }
                    },
                )
            }
        }
    }

    if (showNewPlaylist) {
        CreatePlaylistDialog(
            onDismiss = { showNewPlaylist = false },
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showNewPlaylist = false
            },
        )
    }

    if (showClearHistory) {
        AlertDialog(
            onDismissRequest = { showClearHistory = false },
            title = { Text(stringResource(R.string.library_clear_history)) },
            text = { Text(stringResource(R.string.library_clear_history_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearHistory = false
                }) { Text(stringResource(R.string.library_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistory = false }) {
                    Text(stringResource(R.string.library_cancel))
                }
            },
        )
    }
}

@Composable
private fun HistoryTab(
    history: List<id.nonton.app.data.HistoryEntity>,
    onOpen: (id.nonton.app.data.HistoryEntity) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.library_history_empty),
            subtitle = "Video yang kamu tonton akan muncul di sini.",
            icon = Icons.Outlined.History,
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${history.size} tayangan",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.library_clear_history))
                }
            }
        }
        items(history, key = { it.streamUrl }) { item ->
            VideoRow(
                video = item.toUiModel(),
                showService = true,
                onClick = { onOpen(item) },
                trailing = {
                    IconButton(onClick = { onDelete(item.streamUrl) }) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.library_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<id.nonton.app.data.PlaylistSummary>,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (playlists.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyState(
                title = stringResource(R.string.library_playlists_empty),
                subtitle = "Kumpulkan video favoritmu menjadi satu.",
                icon = Icons.Outlined.PlaylistPlay,
                modifier = Modifier.weight(1f, fill = false),
            )
            Button(onClick = onCreate, modifier = Modifier.padding(bottom = 32.dp)) {
                Text(stringResource(R.string.library_new_playlist))
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${playlists.size} playlist",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCreate) {
                Text(stringResource(R.string.library_new_playlist))
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                PlaylistCard(
                    name = playlist.name,
                    itemCount = playlist.itemCount,
                    onClick = { onOpen(playlist.id) },
                    onDelete = { onDelete(playlist.id) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    name: String,
    itemCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box {
            PlaylistCover(name = name, modifier = Modifier.fillMaxWidth())
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                    )
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.library_items_count, itemCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadsTab(
    downloads: List<DownloadEntity>,
    onCancel: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onOpen: (DownloadEntity) -> Unit,
) {
    if (downloads.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.library_downloads_empty),
            subtitle = "Unduh video atau audio untuk ditonton offline.",
            icon = Icons.Outlined.Download,
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(downloads, key = { it.id }) { download ->
            DownloadRow(
                download = download,
                onCancel = { onCancel(download.id) },
                onDelete = { onDelete(download.id) },
                onOpen = { onOpen(download) },
            )
        }
    }
}

@Composable
private fun DownloadRow(
    download: DownloadEntity,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VideoThumbnail(
            video = id.nonton.app.ui.VideoUiModel(
                url = download.streamUrl,
                title = download.title,
                uploader = download.uploader,
                thumbnailUrl = download.thumbnailUrl,
                serviceId = download.serviceId,
                durationSeconds = 0,
            ),
            modifier = Modifier.width(124.dp),
            cornerRadius = 10,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = download.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(if (download.kind == id.nonton.app.data.DownloadKind.AUDIO) "Audio" else "Video")
                    append(" · ${download.qualityLabel}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))

            when (download.status) {
                DownloadStatus.QUEUED -> {
                    Text(
                        text = "Menunggu antrean",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                DownloadStatus.DOWNLOADING -> {
                    LinearProgressIndicator(
                        progress = { download.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append("${download.progress}%")
                            if (download.totalBytes > 0) {
                                append(" · ${formatBytes(download.bytesDownloaded)} / ${formatBytes(download.totalBytes)}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DownloadStatus.COMPLETED -> {
                    Text(
                        text = "Selesai" + if (download.totalBytes > 0) " · ${formatBytes(download.totalBytes)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                DownloadStatus.FAILED -> {
                    Text(
                        text = download.error ?: stringResource(R.string.download_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                else -> {
                    Text(
                        text = "Dibatalkan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (download.status) {
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(R.string.download_cancel))
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        TextButton(onClick = onOpen) {
                            Text(stringResource(R.string.download_open))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.download_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_new_playlist)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.library_playlist_name)) },
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.library_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_cancel))
            }
        },
    )
}
