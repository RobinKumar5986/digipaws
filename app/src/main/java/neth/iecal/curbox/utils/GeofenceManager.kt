package neth.iecal.curbox.utils

import android.location.Location
import neth.iecal.curbox.data.models.SavedPlace
import neth.iecal.curbox.data.sharedpreferences.SharedPreferences

object GeofenceManager {

    fun isUserInsideAnyLocation(context: android.content.Context, userLat: Double, userLng: Double): Boolean {
        val sharedPrefs = SharedPreferences(context)
        val savedPlaces = sharedPrefs.getAllPlaces()

        for (place in savedPlaces) {
            val placeLat = place.lat.toDoubleOrNull() ?: continue
            val placeLng = place.lng.toDoubleOrNull() ?: continue
            
            val distance = FloatArray(1)
            Location.distanceBetween(
                userLat, userLng,
                placeLat, placeLng,
                distance
            )

            val distanceInFeet = metersToFeet(distance[0].toDouble())

            if (distanceInFeet <= place.radius) {
                return true
            }
        }
        return false
    }

    private fun metersToFeet(meters: Double): Double {
        return meters * 3.28084
    }
}