package neth.iecal.curbox.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.util.Log
import neth.iecal.curbox.data.sharedpreferences.SharedPreferences
import neth.iecal.curbox.ui.activity.WarningActivity

object GeofenceManager {

    private const val TAG = "GeofenceManager"

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = locationManager.getProviders(true)
        var bestLocation: Location? = null

        for (provider in providers) {
            val l = locationManager.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                bestLocation = l
            }
        }

        if (bestLocation == null) {
            Log.e(TAG, "No location fix available from system providers.")
        }

        return bestLocation
    }

    fun isUserInsideAnyLocation(context: Context, userLat: Double, userLng: Double): Boolean {
        val sharedPrefs = SharedPreferences.getInstance(context)
        val savedPlaces = sharedPrefs.getAllPlaces()



        Log.d(TAG, "User Location: $userLat, $userLng | Saved Places Count: ${savedPlaces.size}")

        if (savedPlaces.isEmpty()) {
            return false
        }

        for (place in savedPlaces) {
            val placeLat = place.lat.toDoubleOrNull()
            val placeLng = place.lng.toDoubleOrNull()

            if (placeLat == null || placeLng == null) {
                continue
            }

            val distance = FloatArray(1)
            Location.distanceBetween(
                userLat, userLng,
                placeLat, placeLng,
                distance
            )

            val distanceInFeet = distance[0] * 3.28084
            val isInside = distanceInFeet <= place.radius

            if (isInside) {
                Log.i(TAG, "Geofence Match: User is inside ${place.name}")
                return true
            }
        }

        return false
    }

    fun showGeoBlockWarning(context: Context, packageName: String) {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(homeIntent)

        // Using your existing helpers for Shizuku
        if (AppSuspendHelper.isShizukuAvailable()) {
           ShizukuRunner.executeCommand(
                "am force-stop $packageName",
                object :ShizukuRunner.CommandResultListener {}
            )
        }

        val intent = Intent(context, WarningActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("mode", "GEO_BLOCK")
            putExtra("result_id", packageName)
        }
        context.startActivity(intent)
    }
}