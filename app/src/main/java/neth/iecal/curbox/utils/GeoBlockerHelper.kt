package neth.iecal.curbox.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.LocationServices
import org.osmdroid.util.GeoPoint

class GeoBlockerHelper(private val fragment: Fragment) {

    private var requestPermissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var backgroundPermissionLauncher: ActivityResultLauncher<String>? = null
    private var onLocationReceived: ((GeoPoint) -> Unit)? = null

    init {
        requestPermissionLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    checkAndRequestBackgroundLocation()
                } else {
                    getLastKnownLocation()
                }
            }
        }

        backgroundPermissionLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                getLastKnownLocation()
            } else {
                // User denied background location, but we have foreground.
                // We proceed, but background geofencing might not work reliably.
                getLastKnownLocation()
            }
        }
    }

    fun checkAndRequestLocation(callback: (GeoPoint) -> Unit) {
        this.onLocationReceived = callback
        val context = fragment.requireContext()

        if (hasForegroundLocationPermission(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationPermission(context)) {
                checkAndRequestBackgroundLocation()
            } else {
                getLastKnownLocation()
            }
        } else {
            requestPermissionLauncher?.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasForegroundLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkAndRequestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundPermissionLauncher?.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation() {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(fragment.requireActivity())
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    onLocationReceived?.invoke(GeoPoint(location.latitude, location.longitude))
                }
            }
        } catch (e: Exception) {
            Log.e("GeoBlockerHelper", "Error getting location: ${e.message}")
        }
    }
}