package id.nonton.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.nonton.app.R
import id.nonton.app.openPlayer
import id.nonton.app.ui.components.ContentFilterRow
import id.nonton.app.ui.components.EmptyState
import id.nonton.app.ui.components.EndlessListEffect
import id.nonton.app.ui.components.ErrorState
import id.nonton.app.ui.components.FullScreenLoading
import id.nonton.app.ui.components.LoadingFooter
import id.nonton.app.ui.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSearch: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    EndlessListEffect(
        listState = listState,
        enabled = state.hasMore && !state.isLoading && !state.isLoadingMore,
        onLoadMore = viewModel::loadMore,
    )

    LaunchedEffect(state.filter) {
        listState.scrollToItem(0)
    }

    val visibleItems = state.visibleItems

    // Kalau hasil filter masih sedikit/kosong, otomatis muat halaman berikut.
    // Feed video populer Indonesia bisa berisi banyak live per halaman, jadi
    // butuh beberapa halaman sebelum daftar video terisi.
    var autoTries by remember(state.filter) { mutableStateOf(0) }
    val autoLimit = if (state.filter == HomeFilter.VIDEO) 6 else 3
    LaunchedEffect(
        state.filter, visibleItems.size, state.hasMore,
        state.isLoading, state.isLoadingMore, autoTries,
    ) {
        if (state.filter != HomeFilter.ALL &&
            visibleItems.size < 4 &&
            state.hasMore &&
            !state.isLoading && !state.isLoadingMore &&
            autoTries < autoLimit
        ) {
            autoTries++
            viewModel.loadMore()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HomeHeader(onOpenSearch = onOpenSearch)
        ContentFilterRow(
            selected = state.filter,
            onSelect = viewModel::selectFilter,
        )

        Box(modifier = Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = state.isLoading && visibleItems.isNotEmpty(),
                onRefresh = viewModel::retry,
                modifier = Modifier.fillMaxSize(),
            ) {
            when {
                state.isLoading && visibleItems.isEmpty() -> FullScreenLoading()
                state.error != null && visibleItems.isEmpty() -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::retry,
                )
                visibleItems.isEmpty() -> EmptyState(
                    title = if (state.filter == HomeFilter.LIVE) {
                        stringResource(R.string.home_empty_live)
                    } else {
                        "Belum ada video"
                    },
                    subtitle = "Coba layanan lain atau muat ulang.",
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item(key = "header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                state.subtitle?.let { subtitle ->
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "${visibleItems.size} video",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(visibleItems, key = { it.url }) { video ->
                        VideoCard(
                            video = video,
                            onClick = { context.openPlayer(video) },
                        )
                    }
                    if (state.isLoadingMore) {
                        item(key = "loading-more") { LoadingFooter() }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun HomeHeader(onOpenSearch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.size(10.dp))
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenSearch) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.nav_search),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
