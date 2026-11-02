package id.nonton.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.nonton.app.core.ExtractorRepository
import id.nonton.app.core.ServiceCatalog
import id.nonton.app.core.SettingsStore
import id.nonton.app.core.userMessage
import id.nonton.app.ui.VideoUiModel
import id.nonton.app.ui.simpleViewModelFactory
import id.nonton.app.ui.toUiModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

data class HomeUiState(
    val serviceId: Int,
    val title: String = "",
    val items: List<VideoUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val nextPage: Page? = null,
)

class HomeViewModel(
    private val repository: ExtractorRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(
            serviceId = settings.selectedServiceId.value,
            title = "Trending di ${ServiceCatalog.nameFor(settings.selectedServiceId.value)}",
        )
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var moreJob: Job? = null

    init {
        loadTrending()
    }

    fun selectService(serviceId: Int) {
        if (serviceId == _state.value.serviceId) return
        settings.setSelectedService(serviceId)
        _state.update {
            it.copy(
                serviceId = serviceId,
                title = "Trending di ${ServiceCatalog.nameFor(serviceId)}",
                items = emptyList(),
                nextPage = null,
            )
        }
        loadTrending()
    }

    fun retry() = loadTrending()

    fun loadMore() {
        val current = _state.value
        val nextPage = current.nextPage ?: return
        if (current.isLoading || current.isLoadingMore) return

        moreJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            repository.trending(current.serviceId, nextPage)
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

    private fun loadTrending() {
        loadJob?.cancel()
        moreJob?.cancel()
        val serviceId = _state.value.serviceId
        _state.update { it.copy(isLoading = true, isLoadingMore = false, error = null) }

        loadJob = viewModelScope.launch {
            repository.trending(serviceId)
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
            simpleViewModelFactory { HomeViewModel(repository, settings) }
    }
}
