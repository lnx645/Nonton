package id.nonton.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
import id.nonton.app.ui.components.VideoRow
import id.nonton.app.ui.home.HomeFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    EndlessListEffect(
        listState = listState,
        enabled = state.nextPage != null && !state.isLoading && !state.isLoadingMore,
        onLoadMore = viewModel::loadMore,
    )

    LaunchedEffect(state.filter, state.submittedQuery) {
        listState.scrollToItem(0)
    }

    val visibleItems = state.visibleItems

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.nav_search),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null)
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clear) {
                        Icon(
                            Icons.Outlined.Clear,
                            contentDescription = stringResource(R.string.library_cancel),
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    viewModel.submit()
                    keyboard?.hide()
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        if (state.submittedQuery.isNotBlank()) {
            ContentFilterRow(
                selected = state.filter,
                onSelect = viewModel::selectFilter,
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = state.isLoading && state.items.isNotEmpty(),
                onRefresh = viewModel::retry,
                modifier = Modifier.fillMaxSize(),
            ) {
            when {
                state.isLoading && state.items.isEmpty() -> FullScreenLoading()
                state.error != null && state.items.isEmpty() -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::retry,
                )
                state.submittedQuery.isBlank() -> EmptyState(
                    title = "Cari apa hari ini?",
                    subtitle = stringResource(R.string.search_empty),
                    icon = Icons.Outlined.Search,
                )
                visibleItems.isEmpty() -> EmptyState(
                    title = if (state.filter == HomeFilter.LIVE) {
                        stringResource(R.string.home_empty_live)
                    } else {
                        stringResource(R.string.search_no_result)
                    },
                    subtitle = "Coba kata kunci lain atau ganti layanan.",
                    icon = Icons.Outlined.Search,
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(key = "header") {
                        Text(
                            text = stringResource(R.string.search_results_for, state.submittedQuery),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    items(visibleItems, key = { it.url }) { video ->
                        VideoRow(
                            video = video,
                            onClick = {
                                viewModel.recordOpenedResult()
                                context.openPlayer(video)
                            },
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
