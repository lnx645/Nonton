package id.nonton.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.nonton.app.data.LibraryRepository
import id.nonton.app.data.PlaylistItemEntity
import id.nonton.app.ui.simpleViewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(
    private val repository: LibraryRepository,
    private val playlistId: Long,
) : ViewModel() {

    val items: Flow<List<PlaylistItemEntity>> = repository.playlistItems(playlistId)

    private val _name = MutableStateFlow("Playlist")
    val name: StateFlow<String> = _name.asStateFlow()

    init {
        viewModelScope.launch {
            repository.playlistName(playlistId)?.let { _name.value = it }
        }
    }

    fun removeItem(id: Long) {
        viewModelScope.launch { repository.removePlaylistItem(id) }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            onDeleted()
        }
    }

    companion object {
        fun factory(repository: LibraryRepository, playlistId: Long) =
            simpleViewModelFactory { PlaylistDetailViewModel(repository, playlistId) }
    }
}
