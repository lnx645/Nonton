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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import id.nonton.app.ui.components.EmptyState
import id.nonton.app.ui.components.EndlessListEffect
import id.nonton.app.ui.components.ErrorState
import id.nonton.app.ui.components.FullScreenLoading
import id.nonton.app.ui.components.LoadingFooter
import id.nonton.app.ui.components.ServiceChips
import id.nonton.app.ui.components.VideoCard

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
        enabled = state.nextPage != null && !state.isLoading && !state.isLoadingMore,
        onLoadMore = viewModel::loadMore,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        HomeHeader(onOpenSearch = onOpenSearch)
        ServiceChips(
            selectedServiceId = state.serviceId,
            onSelect = viewModel::selectService,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading && state.items.isEmpty() -> FullScreenLoading()
                state.error != null && state.items.isEmpty() -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::retry,
                )
                state.items.isEmpty() -> EmptyState(
                    title = "Belum ada video",
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
                            Text(
                                text = state.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${state.items.size} video",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(state.items, key = { it.url }) { video ->
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
