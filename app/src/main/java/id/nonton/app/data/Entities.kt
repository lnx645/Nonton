package id.nonton.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val streamUrl: String,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val serviceId: Int,
    val durationSeconds: Long,
    val positionMs: Long,
    val durationMs: Long,
    val watchedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("playlistId"), Index(value = ["playlistId", "streamUrl"], unique = true)],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val streamUrl: String,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val serviceId: Int,
    val durationSeconds: Long,
    val addedAt: Long,
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val itemCount: Int,
)

object DownloadStatus {
    const val QUEUED = "QUEUED"
    const val DOWNLOADING = "DOWNLOADING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}

object DownloadKind {
    const val VIDEO = "video"
    const val AUDIO = "audio"
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val streamUrl: String,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val serviceId: Int,
    val kind: String,
    val qualityLabel: String,
    val mediaUrl: String,
    val mimeType: String,
    val fileName: String,
    val status: String = DownloadStatus.QUEUED,
    val progress: Int = 0,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = -1,
    val localUri: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
