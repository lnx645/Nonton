package id.nonton.app.core

import org.jsoup.Jsoup
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.math.abs

data class MediaOption(
    val label: String,
    val url: String,
    val mimeType: String,
    val height: Int = 0,
    val bitrateKbps: Int = 0,
    val audioOnly: Boolean = false,
    val adaptive: Boolean = false,
)

data class PlaybackData(
    val info: StreamInfo,
    val videoOptions: List<MediaOption>,
    val audioOptions: List<MediaOption>,
    val isLive: Boolean,
) {
    val progressiveVideos: List<MediaOption> get() = videoOptions.filter { !it.adaptive }
    val preferredAudio: MediaOption? get() = audioOptions.firstOrNull()

    val bestThumbnail: String? get() = info.bestThumbnailUrl()
    val title: String get() = info.name.orEmpty()
    val uploader: String? get() = info.uploaderName
    val durationSeconds: Long get() = info.duration
    val description: String get() = info.plainDescription()

    fun defaultVideo(preference: String): MediaOption? {
        val progressive = progressiveVideos
        if (progressive.isEmpty()) return videoOptions.firstOrNull()
        val target = when (preference) {
            "720" -> 720
            "480" -> 480
            "360" -> 360
            else -> Int.MAX_VALUE
        }
        val atMost = progressive.filter { it.height in 1..target }
        return atMost.maxByOrNull { it.height }
            ?: progressive.minByOrNull { abs(it.height - target) }
            ?: progressive.first()
    }
}

fun StreamInfo.toPlaybackData(): PlaybackData {
    val videoOptions = buildList {
        videoStreams
            .filter { it.content.isNotBlank() }
            .sortedWith(
                compareByDescending<VideoStream> { it.height }
                    .thenBy { if (it.format == MediaFormat.MPEG_4) 0 else 1 }
                    .thenByDescending { it.bitrate }
            )
            .forEach { stream ->
                val format = stream.format
                val fps = stream.fps
                val label = buildString {
                    append(if (stream.height > 0) "${stream.height}p" else stream.resolution.ifBlank { "Video" })
                    if (fps > 30) append(" $fps fps")
                    if (format != null) append(" · ${format.name}")
                }
                add(
                    MediaOption(
                        label = label,
                        url = stream.content,
                        mimeType = format?.mimeType ?: "video/mp4",
                        height = stream.height,
                    )
                )
            }

        if (isLive() || none { !it.adaptive }) {
            val hls = hlsUrl
            if (!hls.isNullOrBlank()) {
                add(
                    MediaOption(
                        label = if (isLive()) "Live (HLS)" else "Adaptif (HLS)",
                        url = hls,
                        mimeType = "application/x-mpegURL",
                        adaptive = true,
                    )
                )
            }
            val dash = dashMpdUrl
            if (dash.isNotBlank()) {
                add(
                    MediaOption(
                        label = if (isLive()) "Live (DASH)" else "Adaptif (DASH)",
                        url = dash,
                        mimeType = "application/dash+xml",
                        adaptive = true,
                    )
                )
            }
        }
    }

    val audioOptions = audioStreams
        .filter { it.content.isNotBlank() }
        .sortedByDescending { it.averageBitrate }
        .map { stream ->
            MediaOption(
                label = buildString {
                    val kbps = stream.averageBitrate / 1000
                    if (kbps > 0) append("$kbps kbps") else append("Audio")
                    stream.format?.let { append(" · ${it.name}") }
                },
                url = stream.content,
                mimeType = stream.format?.mimeType ?: "audio/mp4",
                bitrateKbps = stream.averageBitrate / 1000,
                audioOnly = true,
            )
        }

    return PlaybackData(
        info = this,
        videoOptions = videoOptions,
        audioOptions = audioOptions,
        isLive = isLive(),
    )
}

fun StreamInfo.isLive(): Boolean =
    streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM

fun StreamInfo.plainDescription(): String {
    val description = description ?: return ""
    return when (description.type) {
        Description.Type.HTML -> Jsoup.parse(description.content).text()
        Description.Type.MARKDOWN -> description.content
        Description.Type.PLAIN_TEXT -> description.content
    }
}

fun StreamInfo.bestThumbnailUrl(): String? = bestImageUrl(thumbnails)
fun StreamInfoItem.bestThumbnailUrl(): String? = bestImageUrl(thumbnails)

private fun bestImageUrl(images: List<Image>): String? =
    images.maxByOrNull { it.width.coerceAtLeast(0) * it.height.coerceAtLeast(0) }?.url
        ?: images.firstOrNull()?.url

fun VideoStream.displayLabel(): String = buildString {
    append(resolution.ifBlank { if (height > 0) "${height}p" else "Video" })
    format?.let { append(" · ${it.name}") }
    if (bitrate > 0) append(" · ${bitrate / 1000} kbps")
}

fun AudioStream.displayLabel(): String = buildString {
    if (averageBitrate > 0) append("${averageBitrate / 1000} kbps") else append("Audio")
    format?.let { append(" · ${it.name}") }
}
