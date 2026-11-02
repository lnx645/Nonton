package id.nonton.app.core

import androidx.compose.ui.graphics.Color

data class ServiceUi(
    val id: Int,
    val name: String,
    val shortName: String,
    val color: Color,
    val homeUrl: String,
)

object ServiceCatalog {

    val all: List<ServiceUi> = listOf(
        ServiceUi(0, "YouTube", "YouTube", Color(0xFFFF4E45), "https://www.youtube.com"),
        ServiceUi(1, "SoundCloud", "SoundCloud", Color(0xFFFF7700), "https://soundcloud.com"),
        ServiceUi(2, "media.ccc.de", "CCC", Color(0xFF2E7D32), "https://media.ccc.de"),
        ServiceUi(3, "PeerTube", "PeerTube", Color(0xFFF16823), "https://joinpeertube.org"),
        ServiceUi(4, "Bandcamp", "Bandcamp", Color(0xFF1DA0C3), "https://bandcamp.com"),
    )

    fun byId(id: Int): ServiceUi = all.firstOrNull { it.id == id } ?: all.first()

    fun nameFor(id: Int): String = byId(id).name
}
