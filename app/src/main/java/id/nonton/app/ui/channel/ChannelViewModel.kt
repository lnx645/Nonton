package id.nonton.app.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.nonton.app.core.ChannelData
import id.nonton.app.core.ExtractorRepository
import id.nonton.app.core.userMessage
import id.nonton.app.ui.VideoUiModel
import id.nonton.app.ui.home.HomeFilter
import id.nonton.app.ui.simpleViewModelFactory
import id.nonton.app.ui.toUiModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

data class ChannelUiState(
    val url: String,
    val serviceId: Int,
    val isLoading: Boolean = true,
    val error: String? = null,
    val name: String = "",
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val subscriberText: String = "",
    val verified: Boolean = false,
    val description: String = "",
    val videos: List<VideoUiModel> = emptyList(),
    val filter: HomeFilter = HomeFilter.ALL,
    val isLoadingMore: Boolean = false,
    val nextPage: Page? = null,
) {
    val visibleVideos: List<VideoUiModel> = when (filter) {
        HomeFilter.ALL -> videos
        HomeFilter.VIDEO -> videos.filter { !it.isLive }
        HomeFilter.LIVE -> videos.filter { it.isLive }
    }
}

class ChannelViewModel(
    private val repository: ExtractorRepository,
    initialUrl: String,
    initialServiceId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChannelUiState(url = initialUrl, serviceId = initialServiceId)
    )
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var moreJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    fun selectFilter(filter: HomeFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter) }
    }

    fun loadMore() {
        val current = _state.value
        val nextPage = current.nextPage ?: return
        if (current.isLoading || current.isLoadingMore) return
        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            repository.channelMore(current.serviceId, current.url, nextPage)
                .onSuccess { page ->
                    _state.update { old ->
                        old.copy(
                            videos = (old.videos + page.items.map { it.toUiModel() })
                                .distinctBy { it.url },
                            nextPage = page.nextPage,
                            isLoadingMore = false,
                        )
                    }
                }
                .onFailure {
                    _state.update { old -> old.copy(isLoadingMore = false) }
                }
        }
    }

    private fun load() {
        loadJob?.cancel()
        moreJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            repository.channel(_state.value.serviceId, _state.value.url)
                .onSuccess { data: ChannelData ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            name = data.info.name.orEmpty(),
                            avatarUrl = data.info.avatars.maxByOrNull { img ->
                                img.width.coerceAtLeast(0) * img.height.coerceAtLeast(0)
                            }?.url,
                            bannerUrl = data.info.banners.maxByOrNull { img ->
                                img.width.coerceAtLeast(0) * img.height.coerceAtLeast(0)
                            }?.url,
                            subscriberText = formatSubscriberId(data.info.subscriberCount),
                            verified = data.info.isVerified,
                            description = data.info.description.orEmpty(),
                            videos = data.videos.map { item -> item.toUiModel() },
                            nextPage = data.nextPage,
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, error = throwable.userMessage())
                    }
                }
        }
    }

    companion object {
        fun factory(
            repository: ExtractorRepository,
            url: String,
            serviceId: Int,
        ) = simpleViewModelFactory {
            ChannelViewModel(repository, url, serviceId)
        }
    }
}

private fun formatSubscriberId(count: Long): String {
    if (count < 0) return ""
    return when {
        count >= 1_000_000 -> "${trim(count / 1_000_000.0)} jt subscriber"
        count >= 1_000 -> "${trim(count / 1_000.0)} rb subscriber"
        else -> "$count subscriber"
    }
}

private fun trim(value: Double): String =
    if (value >= 10) value.toInt().toString()
    else String.format(java.util.Locale("id", "ID"), "%.1f", value)
