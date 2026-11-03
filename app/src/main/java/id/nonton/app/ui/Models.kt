package id.nonton.app.ui

import id.nonton.app.core.bestDateText
import id.nonton.app.core.bestThumbnailUrl
import id.nonton.app.core.isLive
import id.nonton.app.data.HistoryEntity
import id.nonton.app.data.PlaylistItemEntity
import id.nonton.app.data.PlaylistSummary
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

data class CommentUiModel(
    val id: String,
    val author: String,
    val avatarUrl: String?,
    val text: String,
    val likeCount: Int,
    val dateText: String,
    val pinned: Boolean,
    val hearted: Boolean,
    val verified: Boolean,
)

fun CommentsInfoItem.toCommentUiModel(): CommentUiModel {
    val desc = commentText
    val text = when (desc?.type) {
        Description.HTML -> android.text.Html.fromHtml(
            desc.content, android.text.Html.FROM_HTML_MODE_LEGACY,
        ).toString().trim()
        else -> desc?.content.orEmpty()
    }
    return CommentUiModel(
        id = commentId.orEmpty().ifBlank { "$uploaderName${desc?.content.orEmpty().hashCode()}" },
        author = uploaderName.orEmpty().ifBlank { "Anonim" },
        avatarUrl = uploaderAvatars.maxByOrNull { img ->
            img.width.coerceAtLeast(0) * img.height.coerceAtLeast(0)
        }?.url,
        text = text,
        likeCount = likeCount,
        dateText = bestDateText(
            textualUploadDate,
            runCatching { uploadDate?.offsetDateTime()?.toInstant() }.getOrNull(),
        ),
        pinned = isPinned,
        hearted = isHeartedByUploader,
        verified = isUploaderVerified,
    )
}

data class VideoUiModel(
    val url: String,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val serviceId: Int,
    val durationSeconds: Long,
    val views: Long = -1,
    val dateText: String? = null,
    val isLive: Boolean = false,
    val resumePositionMs: Long = 0,
    val progressFraction: Float = 0f,
)

data class PlaylistUiModel(
    val id: Long,
    val name: String,
    val itemCount: Int,
)

fun StreamInfoItem.toUiModel(): VideoUiModel = VideoUiModel(
    url = url,
    title = name.orEmpty(),
    uploader = uploaderName,
    thumbnailUrl = bestThumbnailUrl(),
    serviceId = serviceId,
    durationSeconds = duration,
    views = viewCount,
    dateText = bestDateText(textualUploadDate, runCatching { uploadDate?.offsetDateTime()?.toInstant() }.getOrNull()),
    isLive = streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM,
)

fun StreamInfo.toUiModel(): VideoUiModel = VideoUiModel(
    url = originalUrl.ifBlank { url },
    title = name.orEmpty(),
    uploader = uploaderName,
    thumbnailUrl = bestThumbnailUrl(),
    serviceId = serviceId,
    durationSeconds = duration,
    views = viewCount,
    dateText = bestDateText(textualUploadDate, runCatching { uploadDate?.offsetDateTime()?.toInstant() }.getOrNull()),
    isLive = isLive(),
)

fun HistoryEntity.toUiModel(): VideoUiModel = VideoUiModel(
    url = streamUrl,
    title = title,
    uploader = uploader,
    thumbnailUrl = thumbnailUrl,
    serviceId = serviceId,
    durationSeconds = durationSeconds,
    resumePositionMs = positionMs,
    progressFraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    },
)

fun PlaylistItemEntity.toUiModel(): VideoUiModel = VideoUiModel(
    url = streamUrl,
    title = title,
    uploader = uploader,
    thumbnailUrl = thumbnailUrl,
    serviceId = serviceId,
    durationSeconds = durationSeconds,
)

fun PlaylistSummary.toUiModel(): PlaylistUiModel = PlaylistUiModel(id, name, itemCount)
