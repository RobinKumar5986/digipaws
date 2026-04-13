package neth.iecal.curbox.data.sharedpreferences

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import neth.iecal.curbox.data.models.SavedPlace
import androidx.core.content.edit
import com.google.android.gms.maps.model.LatLng

class SharedPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("curbox_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_PLACES_LIST = "saved_places_list"
        private const val KEY_LAST_LAT = "last_known_lat"
        private const val KEY_LAST_LNG = "last_known_lng"
        private const val KEY_LAST_TIME = "last_location_timestamp"
    }

    fun saveLastLocation(latLng: LatLng) {
        prefs.edit {
            putString(KEY_LAST_LAT, latLng.latitude.toString())
            putString(KEY_LAST_LNG, latLng.longitude.toString())
            putLong(KEY_LAST_TIME, System.currentTimeMillis())
        }
    }

    fun getLastLocation(): LatLng? {
        val lat = prefs.getString(KEY_LAST_LAT, null)?.toDoubleOrNull() ?: return null
        val lng = prefs.getString(KEY_LAST_LNG, null)?.toDoubleOrNull() ?: return null
        return LatLng(lat, lng)
    }

    fun getLastLocationTime(): Long {
        return prefs.getLong(KEY_LAST_TIME, 0L)
    }

    fun getAllPlaces(): List<SavedPlace> {
        val json = prefs.getString(KEY_PLACES_LIST, null) ?: return emptyList()
        val type = object : TypeToken<List<SavedPlace>>() {}.type
        return gson.fromJson(json, type)
    }

    fun savePlace(newPlace: SavedPlace) {
        val existingPlaces = getAllPlaces().toMutableList()
        existingPlaces.add(newPlace)
        prefs.edit { putString(KEY_PLACES_LIST, gson.toJson(existingPlaces)) }
    }

    fun updatePlace(updatedPlace: SavedPlace) {
        val existingPlaces = getAllPlaces().toMutableList()
        val index = existingPlaces.indexOfFirst { it.id == updatedPlace.id }
        if (index != -1) {
            existingPlaces[index] = updatedPlace
            prefs.edit { putString(KEY_PLACES_LIST, gson.toJson(existingPlaces)) }
        }
    }

    fun removePlace(id: String) {
        val existingPlaces = getAllPlaces().toMutableList()
        existingPlaces.removeAll { it.id == id }
        prefs.edit { putString(KEY_PLACES_LIST, gson.toJson(existingPlaces)) }
    }

    fun clearAll() {
        prefs.edit {
            remove(KEY_PLACES_LIST)
            remove(KEY_LAST_LAT)
            remove(KEY_LAST_LNG)
            remove(KEY_LAST_TIME)
        }
    }
}