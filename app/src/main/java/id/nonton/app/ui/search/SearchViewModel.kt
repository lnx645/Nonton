package id.nonton.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.nonton.app.core.ExtractorRepository
import id.nonton.app.core.SettingsStore
import id.nonton.app.core.userMessage
import id.nonton.app.ui.VideoUiModel
import id.nonton.app.ui.home.HomeFilter
import id.nonton.app.ui.simpleViewModelFactory
import id.nonton.app.ui.toUiModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

data class SearchUiState(
    val serviceId: Int,
    val query: String = "",
    val submittedQuery: String = "",
    val items: List<VideoUiModel> = emptyList(),
    val filter: HomeFilter = HomeFilter.ALL,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val nextPage: Page? = null,
) {
    val visibleItems: List<VideoUiModel> = when (filter) {
        HomeFilter.ALL -> items
        HomeFilter.VIDEO -> items.filter { !it.isLive }
        HomeFilter.LIVE -> items.filter { it.isLive }
    }
}

class SearchViewModel(
    private val repository: ExtractorRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SearchUiState(serviceId = settings.selectedServiceId.value)
    )
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var moreJob: Job? = null
    private var debounceJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        // Live search: cari otomatis setelah pengguna berhenti mengetik.
        debounceJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank() || trimmed == _state.value.submittedQuery) return
        debounceJob = viewModelScope.launch {
            delay(600)
            _state.update { it.copy(submittedQuery = trimmed) }
            search(reset = true)
        }
    }

    fun selectService(serviceId: Int) {
        if (serviceId == _state.value.serviceId) return
        settings.setSelectedService(serviceId)
        _state.update { it.copy(serviceId = serviceId) }
        if (_state.value.submittedQuery.isNotBlank()) {
            search(reset = true)
        }
    }

    fun submit() {
        val query = _state.value.query.trim()
        if (query.isBlank()) return
        debounceJob?.cancel()
        if (query == _state.value.submittedQuery && _state.value.items.isNotEmpty()) return
        _state.update { it.copy(submittedQuery = query) }
        search(reset = true)
    }

    fun retry() = search(reset = true)

    fun selectFilter(filter: HomeFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter) }
    }

    fun clear() {
        searchJob?.cancel()
        moreJob?.cancel()
        debounceJob?.cancel()
        _state.update {
            it.copy(
                query = "",
                submittedQuery = "",
                items = emptyList(),
                error = null,
                isLoading = false,
                nextPage = null,
            )
        }
    }

    fun loadMore() {
        val current = _state.value
        val nextPage = current.nextPage ?: return
        if (current.isLoading || current.isLoadingMore || current.submittedQuery.isBlank()) return

        moreJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            repository.search(current.serviceId, current.submittedQuery, nextPage)
                .onSuccess { page ->
                    _state.update { old ->
                        old.copy(
                            items = (old.items + page.items.map { it.toUiModel() })
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

    private fun search(reset: Boolean) {
        searchJob?.cancel()
        moreJob?.cancel()
        val current = _state.value
        if (current.submittedQuery.isBlank()) return

        _state.update {
            it.copy(
                isLoading = true,
                isLoadingMore = false,
                error = null,
                items = if (reset) emptyList() else it.items,
                nextPage = null,
            )
        }

        searchJob = viewModelScope.launch {
            repository.search(current.serviceId, current.submittedQuery)
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            items = page.items.map { item -> item.toUiModel() },
                            nextPage = page.nextPage,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.userMessage(),
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(repository: ExtractorRepository, settings: SettingsStore) =
            simpleViewModelFactory { SearchViewModel(repository, settings) }
    }
}
