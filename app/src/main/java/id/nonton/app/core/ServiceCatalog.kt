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
    )

    fun byId(id: Int): ServiceUi = all.firstOrNull { it.id == id } ?: all.first()

    fun nameFor(id: Int): String = byId(id).name
}
