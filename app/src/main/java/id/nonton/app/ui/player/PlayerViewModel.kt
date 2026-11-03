package id.nonton.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.nonton.app.core.CommentsPage
import id.nonton.app.core.ExtractorRepository
import id.nonton.app.core.MediaOption
import id.nonton.app.core.PlaybackData
import id.nonton.app.core.SettingsStore
import id.nonton.app.core.toPlaybackData
import id.nonton.app.core.userMessage
import id.nonton.app.ui.CommentUiModel
import id.nonton.app.ui.simpleViewModelFactory
import id.nonton.app.ui.toCommentUiModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

data class PlayerUiState(
    val url: String,
    val serviceId: Int,
    val isLoading: Boolean = true,
    val error: String? = null,
    val data: PlaybackData? = null,
    val selectedMedia: MediaOption? = null,
    val selectedLabel: String = "",
    val audioOnly: Boolean = false,
    val comments: List<CommentUiModel> = emptyList(),
    val commentsCount: Int = -1,
    val commentsLoading: Boolean = false,
    val commentsLoadingMore: Boolean = false,
    val commentsError: String? = null,
    val commentsDisabled: Boolean = false,
    val commentsNextPage: Page? = null,
)

class PlayerViewModel(
    private val repository: ExtractorRepository,
    private val settings: SettingsStore,
    initialUrl: String,
    initialServiceId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PlayerUiState(url = initialUrl, serviceId = initialServiceId)
    )
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var commentsJob: Job? = null
    private var commentsMoreJob: Job? = null

    init {
        load(initialUrl, initialServiceId)
    }

    fun open(url: String, serviceId: Int) {
        if (url == _state.value.url) return
        _state.update {
            it.copy(
                url = url,
                serviceId = serviceId,
                isLoading = true,
                error = null,
                data = null,
                selectedMedia = null,
                selectedLabel = "",
                audioOnly = false,
                comments = emptyList(),
                commentsCount = -1,
                commentsLoading = false,
                commentsLoadingMore = false,
                commentsError = null,
                commentsDisabled = false,
                commentsNextPage = null,
            )
        }
        load(url, serviceId)
    }

    fun retry() = load(_state.value.url, _state.value.serviceId)

    fun retryComments() {
        val current = _state.value
        loadComments(current.url, current.serviceId, reset = true)
    }

    fun loadMoreComments() {
        val current = _state.value
        val nextPage = current.commentsNextPage ?: return
        if (current.commentsLoading || current.commentsLoadingMore) return
        commentsMoreJob?.cancel()
        commentsMoreJob = viewModelScope.launch {
            _state.update { it.copy(commentsLoadingMore = true) }
            repository.comments(current.serviceId, current.url, nextPage)
                .onSuccess { page: CommentsPage ->
                    _state.update { old ->
                        old.copy(
                            comments = old.comments + page.items.map { it.toCommentUiModel() },
                            commentsNextPage = page.nextPage,
                            commentsLoadingMore = false,
                        )
                    }
                }
                .onFailure {
                    _state.update { old -> old.copy(commentsLoadingMore = false) }
                }
        }
    }

    fun selectVideo(option: MediaOption) {
        _state.update {
            it.copy(selectedMedia = option, selectedLabel = option.label, audioOnly = false)
        }
    }

    fun selectAudio(option: MediaOption) {
        _state.update {
            it.copy(selectedMedia = option, selectedLabel = option.label, audioOnly = true)
        }
    }

    private fun load(url: String, serviceId: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            repository.stream(serviceId, url)
                .onSuccess { info ->
                    val data = info.toPlaybackData()
                    val video = data.defaultVideo(settings.defaultQuality)
                    val audio = data.audioOptions.firstOrNull()
                    val selection = video ?: audio
                    if (selection == null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = "Tidak ada format media yang bisa diputar.",
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = null,
                                data = data,
                                selectedMedia = selection,
                                selectedLabel = selection.label,
                                audioOnly = selection.audioOnly,
                            )
                        }
                        loadComments(url, serviceId, reset = true)
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, error = throwable.userMessage())
                    }
                }
        }
    }

    private fun loadComments(url: String, serviceId: Int, reset: Boolean) {
        commentsJob?.cancel()
        commentsMoreJob?.cancel()
        _state.update {
            it.copy(
                commentsLoading = true,
                commentsLoadingMore = false,
                commentsError = null,
                commentsNextPage = null,
                comments = if (reset) emptyList() else it.comments,
                commentsCount = if (reset) -1 else it.commentsCount,
                commentsDisabled = false,
            )
        }
        commentsJob = viewModelScope.launch {
            repository.comments(serviceId, url)
                .onSuccess { page: CommentsPage ->
                    _state.update {
                        it.copy(
                            comments = page.items.map { item -> item.toCommentUiModel() },
                            commentsCount = page.totalCount,
                            commentsDisabled = page.disabled || page.items.isEmpty() &&
                                page.nextPage == null,
                            commentsNextPage = page.nextPage,
                            commentsLoading = false,
                            commentsError = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    val disabled = throwable.message
                        ?.contains("disabled", ignoreCase = true) == true
                    _state.update {
                        it.copy(
                            commentsLoading = false,
                            commentsDisabled = disabled,
                            commentsError = if (disabled) null else throwable.userMessage(),
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(
            repository: ExtractorRepository,
            settings: SettingsStore,
            url: String,
            serviceId: Int,
        ) = simpleViewModelFactory {
            PlayerViewModel(repository, settings, url, serviceId)
        }
    }
}
