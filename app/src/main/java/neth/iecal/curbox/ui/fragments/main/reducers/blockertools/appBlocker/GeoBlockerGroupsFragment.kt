package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.appBlocker

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.SavedPlace
import neth.iecal.curbox.data.sharedpreferences.SharedPreferences
import neth.iecal.curbox.utils.GeoBlockerHelper
import java.util.UUID
import kotlin.math.cos
import kotlin.math.ln

class GeoBlockerGroupsFragment : Fragment(), OnMapReadyCallback {

    companion object {
        const val FRAGMENT_ID = "geo_blocker_groups_editor"
        private const val TAG = "GeoBlockerMap"
        private val DEFAULT_LAT_LNG = LatLng(28.6139, 77.2090)
        private const val DEFAULT_FEET = 200f
        private const val CIRCLE_FILL_FRACTION = 0.2
        private const val REALISTIC_MAP_THRESHOLD_FT = 300f
    }

    private lateinit var locationHelper: GeoBlockerHelper
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var btnSave: MaterialButton
    private lateinit var etPlaceName: TextInputEditText
    private lateinit var sliderRadius: Slider
    private lateinit var tvRadiusValue: TextView
    private lateinit var mapContainer: FrameLayout

    private var googleMap: GoogleMap? = null
    private var radiusCircle: Circle? = null
    private var circleCenter: LatLng = DEFAULT_LAT_LNG
    private var currentRadiusMeters: Double = feetToMeters(DEFAULT_FEET)
    private var colorPrimary: Int = 0
    private var passedId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationHelper = GeoBlockerHelper(this)
        sharedPreferences = SharedPreferences(requireContext())
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
        colorPrimary = tv.data
        MapsInitializer.initialize(requireContext(), MapsInitializer.Renderer.LATEST) { }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_geo_blocker_groups, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)

        val extras = activity?.intent?.extras
        passedId = extras?.getString("id")
        val pName = extras?.getString("name")
        val pLat = extras?.getString("lat")
        val pLng = extras?.getString("lng")
        val pRad = extras?.getInt("radius", DEFAULT_FEET.toInt()) ?: DEFAULT_FEET.toInt()

        etPlaceName.setText(pName)
        sliderRadius.value = pRad.toFloat()
        updateRadiusLabel(pRad.toFloat())

        if (!pLat.isNullOrEmpty() && !pLng.isNullOrEmpty()) {
            circleCenter = LatLng(pLat.toDouble(), pLng.toDouble())
            currentRadiusMeters = feetToMeters(pRad.toFloat())
        }

        setupMap()
        setupSlider()
        setupClickListeners()
    }

    private fun bindViews(root: View) {
        btnSave = root.findViewById(R.id.btnSave)
        etPlaceName = root.findViewById(R.id.etPlaceName)
        sliderRadius = root.findViewById(R.id.sliderRadius)
        tvRadiusValue = root.findViewById(R.id.tvRadiusValue)
        mapContainer = root.findViewById(R.id.mapContainer)
    }

    private fun setupMap() {
        val mapFrag = childFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFrag?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        with(map.uiSettings) {
            isZoomControlsEnabled = false
            isMyLocationButtonEnabled = true
            isScrollGesturesEnabled = true
            isZoomGesturesEnabled = true
        }

        applyMapType(sliderRadius.value)

        if (activity?.intent?.extras?.getString("lat") == null) {
            locationHelper.checkAndRequestLocation {
                circleCenter = it
                fitCameraToRadius(true)
                drawOrUpdateCircle(circleCenter, currentRadiusMeters)
            }
        } else {
            drawOrUpdateCircle(circleCenter, currentRadiusMeters)
            fitCameraToRadius(false)
        }

        map.setOnCameraIdleListener {
            circleCenter = map.cameraPosition.target
            radiusCircle?.center = circleCenter
        }
    }

    private fun drawOrUpdateCircle(centre: LatLng, radiusMeters: Double) {
        val map = googleMap ?: return
        val rgb = colorPrimary and 0x00FFFFFF
        val fill = (0x26 shl 24) or rgb
        val stroke = (0xFF shl 24) or rgb

        if (radiusCircle == null) {
            radiusCircle = map.addCircle(CircleOptions().center(centre).radius(radiusMeters).fillColor(fill).strokeColor(stroke).strokeWidth(4f).zIndex(1f))
        } else {
            radiusCircle?.center = centre
            radiusCircle?.radius = radiusMeters
        }
    }

    private fun fitCameraToRadius(animate: Boolean) {
        val map = googleMap ?: return
        val w = mapContainer.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val latRad = Math.toRadians(circleCenter.latitude)
        val zoom = (ln((minOf(w, w) * CIRCLE_FILL_FRACTION) * 40075016.686 / (2.0 * currentRadiusMeters * 256.0 * cos(latRad))) / ln(2.0)).toFloat().coerceIn(1f, 21f)
        if (animate) map.animateCamera(CameraUpdateFactory.newLatLngZoom(circleCenter, zoom))
        else map.moveCamera(CameraUpdateFactory.newLatLngZoom(circleCenter, zoom))
    }

    private fun applyMapType(feet: Float) {
        googleMap?.mapType = if (feet <= REALISTIC_MAP_THRESHOLD_FT) GoogleMap.MAP_TYPE_SATELLITE else GoogleMap.MAP_TYPE_NORMAL
    }

    private fun setupSlider() {
        sliderRadius.addOnChangeListener { _, feet, _ ->
            updateRadiusLabel(feet)
            currentRadiusMeters = feetToMeters(feet)
            applyMapType(feet)
            radiusCircle?.radius = currentRadiusMeters
            if (mapContainer.width > 0) fitCameraToRadius(true)
        }
    }

    private fun updateRadiusLabel(f: Float) { tvRadiusValue.text = "${f.toInt()} ft" }
    private fun feetToMeters(f: Float): Double = f * 0.3048

    @SuppressLint("DefaultLocale")
    private fun setupClickListeners() {
        btnSave.setOnClickListener {
            val name = etPlaceName.text?.toString()?.trim()
            val latStr = String.format("%.6f", circleCenter.latitude)
            val lngStr = String.format("%.6f", circleCenter.longitude)
            val rad = sliderRadius.value.toInt()

            if (name.isNullOrEmpty()) {
                etPlaceName.error = "Name required"
                return@setOnClickListener
            }

            if (passedId != null) {
                sharedPreferences.updatePlace(SavedPlace(passedId!!, name, latStr, lngStr, rad))
            } else {
                sharedPreferences.savePlace(SavedPlace(UUID.randomUUID().toString(), name, latStr, lngStr, rad))
            }
            activity?.finish()
            Toast.makeText(context,"Location Saved Successfully",Toast.LENGTH_SHORT).show()
        }
    }
}