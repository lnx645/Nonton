package id.nonton.app.data

import kotlinx.coroutines.flow.Flow

class LibraryRepository(private val db: AppDatabase) {

    val history: Flow<List<HistoryEntity>> = db.historyDao().observeAll()
    val playlists: Flow<List<PlaylistSummary>> = db.playlistDao().observeSummaries()
    val downloads: Flow<List<DownloadEntity>> = db.downloadDao().observeAll()

    fun playlistItems(playlistId: Long): Flow<List<PlaylistItemEntity>> =
        db.playlistDao().observeItems(playlistId)

    suspend fun recordHistory(
        streamUrl: String,
        title: String,
        uploader: String?,
        thumbnailUrl: String?,
        serviceId: Int,
        durationSeconds: Long,
        positionMs: Long,
        durationMs: Long,
    ) {
        db.historyDao().upsert(
            HistoryEntity(
                streamUrl = streamUrl,
                title = title,
                uploader = uploader,
                thumbnailUrl = thumbnailUrl,
                serviceId = serviceId,
                durationSeconds = durationSeconds,
                positionMs = positionMs,
                durationMs = durationMs,
                watchedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteHistory(url: String) = db.historyDao().delete(url)
    suspend fun clearHistory() = db.historyDao().clear()

    suspend fun createPlaylist(name: String): Long =
        db.playlistDao().insertPlaylist(
            PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis())
        )

    suspend fun deletePlaylist(id: Long) = db.playlistDao().deletePlaylist(id)

    suspend fun addToPlaylist(
        playlistId: Long,
        streamUrl: String,
        title: String,
        uploader: String?,
        thumbnailUrl: String?,
        serviceId: Int,
        durationSeconds: Long,
    ): Boolean {
        val rowId = db.playlistDao().insertItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                streamUrl = streamUrl,
                title = title,
                uploader = uploader,
                thumbnailUrl = thumbnailUrl,
                serviceId = serviceId,
                durationSeconds = durationSeconds,
                addedAt = System.currentTimeMillis(),
            )
        )
        return rowId != -1L
    }

    suspend fun removePlaylistItem(id: Long) = db.playlistDao().deleteItem(id)

    suspend fun playlistName(id: Long): String? = db.playlistDao().getPlaylist(id)?.name

    suspend fun playlistItemsOnce(id: Long): List<PlaylistItemEntity> =
        db.playlistDao().getItems(id)

    suspend fun enqueueDownload(download: DownloadEntity): Long =
        db.downloadDao().insert(download)

    suspend fun downloadsOnce(): List<DownloadEntity> = db.downloadDao().getAll()

    suspend fun cancelDownload(id: Long) = db.downloadDao().cancel(id)
    suspend fun deleteDownload(id: Long) = db.downloadDao().delete(id)
}
