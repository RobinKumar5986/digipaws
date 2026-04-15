package neth.iecal.curbox.data.sharedpreferences

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import neth.iecal.curbox.data.models.SavedPlace
import org.osmdroid.util.GeoPoint

class SharedPreferences private constructor(private val context: Context) {

    @Suppress("DEPRECATION")
    private fun prefs() = context.applicationContext
        .getSharedPreferences("curbox_prefs", Context.MODE_MULTI_PROCESS)

    private val gson = Gson()

    companion object {
        private const val KEY_PLACES_LIST = "saved_places_list"
        private const val KEY_LAST_LAT = "last_known_lat"
        private const val KEY_LAST_LNG = "last_known_lng"
        private const val KEY_LAST_TIME = "last_location_timestamp"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: SharedPreferences? = null

        fun getInstance(context: Context): SharedPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharedPreferences(context).also { INSTANCE = it }
            }
        }
    }

    fun saveLastLocation(latLng: GeoPoint) {
        prefs().edit(commit = true) {
            putString(KEY_LAST_LAT, latLng.latitude.toString())
            putString(KEY_LAST_LNG, latLng.longitude.toString())
            putLong(KEY_LAST_TIME, System.currentTimeMillis())
        }
    }

    fun getLastLocation(): GeoPoint? {
        val lat = prefs().getString(KEY_LAST_LAT, null)?.toDoubleOrNull() ?: return null
        val lng = prefs().getString(KEY_LAST_LNG, null)?.toDoubleOrNull() ?: return null
        return GeoPoint(lat, lng)
    }

    fun getLastLocationTime(): Long {
        return prefs().getLong(KEY_LAST_TIME, 0L)
    }

    fun getAllPlaces(): List<SavedPlace> {
        val json = prefs().getString(KEY_PLACES_LIST, null) ?: return emptyList()
        val type = object : TypeToken<List<SavedPlace>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePlace(newPlace: SavedPlace) {
        val existingPlaces = getAllPlaces().toMutableList()
        existingPlaces.add(newPlace)
        prefs().edit(commit = true) { putString(KEY_PLACES_LIST, gson.toJson(existingPlaces)) }
    }

    fun updatePlace(updatedPlace: SavedPlace) {
        val existingPlaces = getAllPlaces().toMutableList()
        val index = existingPlaces.indexOfFirst { it.id == updatedPlace.id }
        if (index != -1) {
            existingPlaces[index] = updatedPlace
            prefs().edit(commit = true) { putString(KEY_PLACES_LIST, gson.toJson(existingPlaces)) }
        }
    }

    fun removePlace(id: String) {
        val existingPlaces = getAllPlaces().toMutableList()
        existingPlaces.removeAll { it.id == id }
        prefs().edit(commit = true) { putString(KEY_PLACES_LIST, gson.toJson(existingPlaces)) }
    }

    fun clearAll() {
        prefs().edit(commit = true) {
            remove(KEY_PLACES_LIST)
            remove(KEY_LAST_LAT)
            remove(KEY_LAST_LNG)
            remove(KEY_LAST_TIME)
        }
    }
}