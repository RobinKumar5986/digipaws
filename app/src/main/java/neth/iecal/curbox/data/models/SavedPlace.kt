package neth.iecal.curbox.data.models

data class SavedPlace(
    val id: String,
    val name: String?,
    val lat: String,
    val lng: String,
    val radius: Int
)