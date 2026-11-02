package id.nonton.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.nonton.app.data.DownloadEntity
import id.nonton.app.data.HistoryEntity
import id.nonton.app.data.LibraryRepository
import id.nonton.app.data.PlaylistSummary
import id.nonton.app.ui.simpleViewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {

    val history: Flow<List<HistoryEntity>> = repository.history
    val playlists: Flow<List<PlaylistSummary>> = repository.playlists
    val downloads: Flow<List<DownloadEntity>> = repository.downloads

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun deleteHistory(url: String) {
        viewModelScope.launch { repository.deleteHistory(url) }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createPlaylist(name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { repository.deletePlaylist(id) }
    }

    fun cancelDownload(id: Long) {
        viewModelScope.launch { repository.cancelDownload(id) }
    }

    fun deleteDownload(id: Long) {
        viewModelScope.launch { repository.deleteDownload(id) }
    }

    companion object {
        fun factory(repository: LibraryRepository) =
            simpleViewModelFactory { LibraryViewModel(repository) }
    }
}
