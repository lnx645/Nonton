package id.nonton.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.nonton.app.core.ExtractorRepository
import id.nonton.app.core.MediaOption
import id.nonton.app.core.PlaybackData
import id.nonton.app.core.SettingsStore
import id.nonton.app.core.toPlaybackData
import id.nonton.app.core.userMessage
import id.nonton.app.ui.simpleViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val url: String,
    val serviceId: Int,
    val isLoading: Boolean = true,
    val error: String? = null,
    val data: PlaybackData? = null,
    val selectedMedia: MediaOption? = null,
    val selectedLabel: String = "",
    val audioOnly: Boolean = false,
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
            )
        }
        load(url, serviceId)
    }

    fun retry() = load(_state.value.url, _state.value.serviceId)

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
            settings: SettingsStore,
            url: String,
            serviceId: Int,
        ) = simpleViewModelFactory {
            PlayerViewModel(repository, settings, url, serviceId)
        }
    }
}
