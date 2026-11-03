package id.nonton.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.nonton.app.core.ExtractorRepository
import id.nonton.app.core.ServiceCatalog
import id.nonton.app.core.SettingsStore
import id.nonton.app.core.userMessage
import id.nonton.app.data.HistoryEntity
import id.nonton.app.data.LibraryRepository
import id.nonton.app.ui.VideoUiModel
import id.nonton.app.ui.simpleViewModelFactory
import id.nonton.app.ui.toUiModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.Locale

data class HomeFeed(
    val title: String = "",
    val subtitle: String? = null,
    val items: List<VideoUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
)

data class HomeUiState(
    val serviceId: Int,
    val filter: HomeFilter = HomeFilter.ALL,
    val trending: HomeFeed = HomeFeed(),
    val videos: HomeFeed = HomeFeed(),
) {
    private val active: HomeFeed
        get() = if (filter == HomeFilter.VIDEO) videos else trending

    val items: List<VideoUiModel> get() = active.items
    val title: String get() = active.title
    val subtitle: String? get() = active.subtitle
    val isLoading: Boolean get() = active.isLoading
    val isLoadingMore: Boolean get() = active.isLoadingMore
    val error: String? get() = active.error
    val hasMore: Boolean get() = active.hasMore

    val visibleItems: List<VideoUiModel> = when (filter) {
        HomeFilter.ALL -> trending.items
        HomeFilter.VIDEO -> videos.items
        HomeFilter.LIVE -> trending.items.filter { it.isLive }
    }
}

enum class HomeFilter { ALL, VIDEO, LIVE }

/**
 * Beranda:
 * - Semua/Live memakai feed trending (wilayah mengikuti locale perangkat).
 * - Video menampilkan feed yang dipersonalisasi dari riwayat tonton + kata kunci
 *   yang sering dicari, dicampur video populer Indonesia. Bila belum ada
 *   preferensi, fallback ke video populer (trending non-live) dengan lazy loading.
 */
class HomeViewModel(
    private val repository: ExtractorRepository,
    private val settings: SettingsStore,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(
            serviceId = settings.selectedServiceId.value,
            trending = HomeFeed(
                title = "Trending di ${ServiceCatalog.nameFor(settings.selectedServiceId.value)}",
            ),
            videos = HomeFeed(title = "Video populer Indonesia"),
        )
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var trendingJob: Job? = null
    private var trendingMoreJob: Job? = null
    private var videoJob: Job? = null
    private var videoMoreJob: Job? = null

    private var historyCache: List<HistoryEntity> = emptyList()
    private var videoCursor: VideoCursor? = null
    private var loadedVideoSignature: String? = null

    init {
        viewModelScope.launch {
            library.history.collect { historyCache = it }
        }
        loadTrending()
    }

    fun selectService(serviceId: Int) {
        if (serviceId == _state.value.serviceId) return
        settings.setSelectedService(serviceId)
        videoCursor = null
        loadedVideoSignature = null
        trendingCursor = null
        _state.update {
            it.copy(
                serviceId = serviceId,
                trending = HomeFeed(
                    title = "Trending di ${ServiceCatalog.nameFor(serviceId)}",
                    isLoading = true,
                ),
                videos = HomeFeed(title = "Video populer Indonesia"),
            )
        }
        loadTrending()
        if (_state.value.filter == HomeFilter.VIDEO) loadVideoFeed()
    }

    fun retry() {
        if (_state.value.filter == HomeFilter.VIDEO) loadVideoFeed() else loadTrending()
    }

    fun selectFilter(filter: HomeFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter) }
        if (filter == HomeFilter.VIDEO) ensureVideoFeed()
    }

    fun loadMore() {
        if (_state.value.filter == HomeFilter.VIDEO) loadVideoMore() else loadTrendingMore()
    }

    private fun ensureVideoFeed() {
        val current = _state.value.videos
        if (current.isLoading) return
        val signature = videoSignature()
        if (current.items.isEmpty() || signature != loadedVideoSignature || current.error != null) {
            loadVideoFeed()
        }
    }

    private fun loadTrending() {
        trendingJob?.cancel()
        trendingMoreJob?.cancel()
        trendingCursor = null
        val serviceId = _state.value.serviceId
        _state.update {
            it.copy(trending = it.trending.copy(isLoading = true, isLoadingMore = false, error = null))
        }

        trendingJob = viewModelScope.launch {
            repository.trending(serviceId)
                .onSuccess { page ->
                    trendingCursor = page.nextPage
                    _state.update {
                        it.copy(
                            trending = it.trending.copy(
                                items = page.items.map { item -> item.toUiModel() },
                                isLoading = false,
                                error = null,
                                hasMore = page.nextPage != null,
                            ),
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            trending = it.trending.copy(
                                isLoading = false,
                                error = throwable.userMessage(),
                            ),
                        )
                    }
                }
        }
    }

    private fun loadTrendingMore() {
        val current = _state.value.trending
        if (current.isLoading || current.isLoadingMore || !current.hasMore) return
        val serviceId = _state.value.serviceId
        val nextPage = trendingCursor ?: return

        trendingMoreJob?.cancel()
        _state.update { it.copy(trending = it.trending.copy(isLoadingMore = true)) }
        trendingMoreJob = viewModelScope.launch {
            repository.trending(serviceId, nextPage)
                .onSuccess { page ->
                    trendingCursor = page.nextPage
                    _state.update { old ->
                        old.copy(
                            trending = old.trending.copy(
                                items = (old.trending.items + page.items.map { it.toUiModel() })
                                    .distinctBy { it.url },
                                hasMore = page.nextPage != null,
                                isLoadingMore = false,
                            ),
                        )
                    }
                }
                .onFailure {
                    _state.update { old ->
                        old.copy(trending = old.trending.copy(isLoadingMore = false))
                    }
                }
        }
    }

    private var trendingCursor: Page? = null

    private fun loadVideoFeed() {
        videoJob?.cancel()
        videoMoreJob?.cancel()
        videoCursor = null

        val serviceId = _state.value.serviceId
        val queries = buildPersonalQueries(historyCache)
        val personalized = queries.isNotEmpty()
        val primary = queries.firstOrNull()
        loadedVideoSignature = videoSignature()

        _state.update {
            it.copy(
                videos = HomeFeed(
                    title = if (personalized) "Video untuk Anda" else "Video populer Indonesia",
                    subtitle = if (personalized) {
                        "Dari riwayat tontonan & pencarianmu"
                    } else {
                        "Populer di wilayah Indonesia"
                    },
                    isLoading = true,
                ),
            )
        }

        videoJob = viewModelScope.launch {
            val trendingDeferred = async { repository.trending(serviceId) }
            val primaryDeferred = primary?.let { query ->
                async { repository.search(serviceId, query) }
            }
            val secondaryDeferred = queries.getOrNull(1)?.let { query ->
                async { repository.search(serviceId, query) }
            }

            val trendingResult = trendingDeferred.await()
            val primaryResult = primaryDeferred?.await()
            val secondaryResult = secondaryDeferred?.await()

            val collected = mutableListOf<VideoUiModel>()
            primaryResult?.getOrNull()?.let { page ->
                collected += page.items
                    .filter { it.streamType != StreamType.LIVE_STREAM }
                    .map { it.toUiModel() }
            }
            secondaryResult?.getOrNull()?.let { page ->
                collected += page.items
                    .filter { it.streamType != StreamType.LIVE_STREAM }
                    .map { it.toUiModel() }
            }
            trendingResult.getOrNull()?.let { page ->
                collected += page.items
                    .filter { it.streamType != StreamType.LIVE_STREAM }
                    .map { it.toUiModel() }
            }

            val cursor = VideoCursor(
                query = primary,
                searchNext = primaryResult?.getOrNull()?.nextPage,
                trendingNext = trendingResult.getOrNull()?.nextPage,
            )
            videoCursor = cursor

            val failure = listOfNotNull(
                primaryResult?.exceptionOrNull(),
                secondaryResult?.exceptionOrNull(),
                trendingResult.exceptionOrNull(),
            ).firstOrNull()
            val items = collected.distinctBy { it.url }

            _state.update {
                it.copy(
                    videos = it.videos.copy(
                        items = items,
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = cursor.hasMore,
                        error = if (items.isEmpty()) failure?.userMessage() else null,
                    ),
                )
            }
        }
    }

    private fun loadVideoMore() {
        val cursor = videoCursor ?: return
        val current = _state.value.videos
        if (current.isLoading || current.isLoadingMore || !current.hasMore) return

        val serviceId = _state.value.serviceId
        videoMoreJob?.cancel()
        _state.update { it.copy(videos = it.videos.copy(isLoadingMore = true)) }

        videoMoreJob = viewModelScope.launch {
            var searchNext = cursor.searchNext
            var trendingNext = cursor.trendingNext
            val collected = mutableListOf<VideoUiModel>()
            var lastError: Throwable? = null

            if (searchNext != null && cursor.query != null) {
                repository.search(serviceId, cursor.query, searchNext)
                    .onSuccess { page ->
                        searchNext = page.nextPage
                        collected += page.items
                            .filter { it.streamType != StreamType.LIVE_STREAM }
                            .map { it.toUiModel() }
                    }
                    .onFailure {
                        lastError = it
                        searchNext = null
                    }
            }

            if (trendingNext != null) {
                repository.trending(serviceId, trendingNext)
                    .onSuccess { page ->
                        trendingNext = page.nextPage
                        collected += page.items
                            .filter { it.streamType != StreamType.LIVE_STREAM }
                            .map { it.toUiModel() }
                    }
                    .onFailure {
                        lastError = it
                        trendingNext = null
                    }
            }

            val nextCursor = VideoCursor(cursor.query, searchNext, trendingNext)
            videoCursor = nextCursor

            _state.update { old ->
                val merged = (old.videos.items + collected).distinctBy { it.url }
                old.copy(
                    videos = old.videos.copy(
                        items = merged,
                        isLoadingMore = false,
                        hasMore = nextCursor.hasMore,
                        error = if (merged.isEmpty()) {
                            lastError?.userMessage()
                        } else {
                            old.videos.error
                        },
                    ),
                )
            }
        }
    }

    /**
     * Kata kunci preferensi: pencarian yang paling sering dilakukan, lalu nama
     * channel yang paling sering ditonton. URL video tidak dipakai langsung,
     * tetapi judul/uploader riwayat menjadi sinyal minat.
     */
    private fun buildPersonalQueries(history: List<HistoryEntity>): List<String> {
        val searches = settings.searchHistory.value
            .asSequence()
            .filter { it.length >= 3 }
            .groupingBy { it.lowercase(Locale.ROOT) }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .mapNotNull { entry ->
                settings.searchHistory.value.lastOrNull {
                    it.lowercase(Locale.ROOT) == entry.key
                }
            }
            .take(2)
            .toList()

        val uploaders = history
            .asSequence()
            .mapNotNull { it.uploader?.trim() }
            .filter { it.length >= 3 && !it.equals("YouTube", ignoreCase = true) }
            .groupingBy { it.lowercase(Locale.ROOT) }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .mapNotNull { entry ->
                history.mapNotNull { it.uploader?.trim() }.lastOrNull {
                    it.lowercase(Locale.ROOT) == entry.key
                }
            }
            .take(2)
            .toList()

        return (searches + uploaders)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(3)
    }

    private fun videoSignature(): String {
        val queries = buildPersonalQueries(historyCache)
        val recent = historyCache.take(10).joinToString(",") { it.streamUrl }
        return queries.joinToString("|") { it.lowercase(Locale.ROOT) } + "::" + recent
    }

    private data class VideoCursor(
        val query: String?,
        val searchNext: Page?,
        val trendingNext: Page?,
    ) {
        val hasMore: Boolean get() = searchNext != null || trendingNext != null
    }

    companion object {
        fun factory(
            repository: ExtractorRepository,
            settings: SettingsStore,
            library: LibraryRepository,
        ) = simpleViewModelFactory { HomeViewModel(repository, settings, library) }
    }
}
