package id.nonton.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY watchedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Upsert
    suspend fun upsert(item: HistoryEntity)

    @Query("DELETE FROM history WHERE streamUrl = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("SELECT * FROM history WHERE streamUrl = :url LIMIT 1")
    suspend fun find(url: String): HistoryEntity?
}

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name, p.createdAt,
               (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS itemCount
        FROM playlists p
        ORDER BY p.createdAt DESC
        """
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylist(id: Long): PlaylistEntity?

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: PlaylistItemEntity): Long

    @Query("DELETE FROM playlist_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    suspend fun getItems(playlistId: Long): List<PlaylistItemEntity>
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    suspend fun getAll(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextQueued(): DownloadEntity?

    @Query("UPDATE downloads SET status = 'QUEUED', progress = 0 WHERE status = 'DOWNLOADING'")
    suspend fun resetDownloading()

    @Query(
        """
        UPDATE downloads
        SET status = :status, progress = :progress, bytesDownloaded = :bytes,
            totalBytes = :total, localUri = :localUri, error = :error
        WHERE id = :id
        """
    )
    suspend fun update(
        id: Long,
        status: String,
        progress: Int,
        bytes: Long,
        total: Long,
        localUri: String?,
        error: String?,
    )

    @Insert
    suspend fun insert(download: DownloadEntity): Long

    @Query("UPDATE downloads SET status = 'CANCELLED' WHERE id = :id")
    suspend fun cancel(id: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'QUEUED'")
    suspend fun queuedCount(): Int
}
