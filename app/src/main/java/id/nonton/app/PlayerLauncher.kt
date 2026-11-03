package id.nonton.app

import android.content.Context
import android.content.Intent
import id.nonton.app.ui.VideoUiModel

object PlayerIntents {
    const val EXTRA_URL = "extra_url"
    const val EXTRA_SERVICE_ID = "extra_service_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_THUMBNAIL = "extra_thumbnail"
    const val EXTRA_UPLOADER = "extra_uploader"
    const val EXTRA_START_POSITION = "extra_start_position"

    fun createIntent(
        context: Context,
        video: VideoUiModel,
        startPositionMs: Long = 0L,
    ): Intent = Intent(context, PlayerActivity::class.java).apply {
        putExtra(EXTRA_URL, video.url)
        putExtra(EXTRA_SERVICE_ID, video.serviceId)
        putExtra(EXTRA_TITLE, video.title)
        putExtra(EXTRA_THUMBNAIL, video.thumbnailUrl)
        putExtra(EXTRA_UPLOADER, video.uploader)
        putExtra(EXTRA_START_POSITION, startPositionMs)
    }
}

object ChannelIntents {
    const val EXTRA_URL = "extra_channel_url"
    const val EXTRA_SERVICE_ID = "extra_channel_service_id"

    fun createIntent(context: Context, url: String, serviceId: Int): Intent =
        Intent(context, ChannelActivity::class.java).apply {
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_SERVICE_ID, serviceId)
        }
}

fun Context.openPlayer(video: VideoUiModel, startPositionMs: Long = 0L) {
    startActivity(PlayerIntents.createIntent(this, video, startPositionMs))
}

fun Context.openChannel(url: String, serviceId: Int) {
    startActivity(ChannelIntents.createIntent(this, url, serviceId))
}
