package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.appBlocker

import android.annotation.SuppressLint
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.SavedPlace
import neth.iecal.curbox.data.sharedpreferences.SharedPreferences
import neth.iecal.curbox.utils.GeoBlockerHelper
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import java.util.UUID
import kotlin.math.cos
import kotlin.math.ln

class GeoBlockerGroupsFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "geo_blocker_groups_editor"
        private val DEFAULT_GEO_POINT = GeoPoint(28.6139, 77.2090)
        private const val DEFAULT_FEET = 200f
        private const val CIRCLE_FILL_FRACTION = 0.7
    }

    private lateinit var locationHelper: GeoBlockerHelper
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var btnSave: MaterialButton
    private lateinit var fabFocus: FloatingActionButton
    private lateinit var etPlaceName: TextInputEditText
    private lateinit var sliderRadius: Slider
    private lateinit var tvRadiusValue: TextView
    private lateinit var mapContainer: FrameLayout
    private lateinit var mapView: MapView

    private var radiusCircle: Polygon? = null
    private var circleCenter: GeoPoint = DEFAULT_GEO_POINT
    private var currentRadiusMeters: Double = feetToMeters(DEFAULT_FEET)
    private var colorPrimary: Int = 0
    private var passedId: String? = null
    private var passedLocation: GeoPoint? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = requireContext()
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        Configuration.getInstance().userAgentValue = ctx.packageName

        locationHelper = GeoBlockerHelper(this)
        sharedPreferences = SharedPreferences.getInstance(ctx)

        val tv = TypedValue()
        ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
        colorPrimary = tv.data
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_geo_blocker_groups, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupMap()

        // Extract passed data
        val extras = activity?.intent?.extras
        passedId = extras?.getString("id")
        val pLat = extras?.getString("lat")
        val pLng = extras?.getString("lng")
        val pRad = extras?.getInt("radius", DEFAULT_FEET.toInt()) ?: DEFAULT_FEET.toInt()

        // UI Setup
        etPlaceName.setText(extras?.getString("name"))
        sliderRadius.value = pRad.toFloat()
        updateRadiusLabel(pRad.toFloat())
        currentRadiusMeters = feetToMeters(pRad.toFloat())

        if (!pLat.isNullOrEmpty() && !pLng.isNullOrEmpty()) {
            passedLocation = GeoPoint(pLat.toDouble(), pLng.toDouble())
            circleCenter = passedLocation!!
        }

        // Wait for layout to finish before focusing
        mapContainer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                mapContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                smartFocus(animate = true)
            }
        })

        setupSlider()
        setupClickListeners()
    }

    private fun bindViews(root: View) {
        btnSave = root.findViewById(R.id.btnSave)
        fabFocus = root.findViewById(R.id.fabFocusLocation)
        etPlaceName = root.findViewById(R.id.etPlaceName)
        sliderRadius = root.findViewById(R.id.sliderRadius)
        tvRadiusValue = root.findViewById(R.id.tvRadiusValue)
        mapContainer = root.findViewById(R.id.mapContainer)
        mapView = root.findViewById(R.id.mapView)
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                circleCenter = mapView.mapCenter as GeoPoint
                drawOrUpdateCircle()
                return true
            }
            override fun onZoom(event: ZoomEvent?): Boolean {
                drawOrUpdateCircle()
                return true
            }
        })
    }

    /**
     * Priority Hierarchy Focus:
     * 1. Passed Intent Location (If it exists)
     * 2. GPS Location (If helper finds it)
     * 3. Default Hardcoded Location
     */
    private fun smartFocus(animate: Boolean) {
        if( passedLocation != null ) {
            moveToPoint(passedLocation!!, animate = animate)
        }else {
            locationHelper.checkAndRequestLocation { latLng ->
                moveToPoint(GeoPoint(latLng.latitude, latLng.longitude), animate)
            }
        }
    }

    private fun moveToPoint(point: GeoPoint, animate: Boolean) {
        circleCenter = point
        if (animate) {
            mapView.controller.animateTo(circleCenter)
            fitCameraToRadius(true)
        } else {
            mapView.controller.setCenter(circleCenter)
            fitCameraToRadius(false)
        }
        drawOrUpdateCircle()
    }

    private fun fitCameraToRadius(animate: Boolean) {
        val w = mapContainer.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val latRad = Math.toRadians(circleCenter.latitude)

        val zoom = (ln((minOf(w, w) * CIRCLE_FILL_FRACTION) * 40075016.686 /
                (2.0 * currentRadiusMeters * 256.0 * cos(latRad))) / ln(2.0))
            .coerceIn(1.0, 20.0)

        if (animate) {
            mapView.controller.animateTo(circleCenter, zoom, 500L)
        } else {
            mapView.controller.setZoom(zoom)
            mapView.controller.setCenter(circleCenter)
        }
    }

    private fun drawOrUpdateCircle() {
        if (radiusCircle == null) {
            radiusCircle = Polygon().apply {
                val rgb = colorPrimary and 0x00FFFFFF
                fillPaint.color = (0x26 shl 24) or rgb
                outlinePaint.color = (0xFF shl 24) or rgb
                outlinePaint.strokeWidth = 4f
            }
            mapView.overlays.add(radiusCircle)
        }
        radiusCircle?.points = Polygon.pointsAsCircle(circleCenter, currentRadiusMeters)
        mapView.invalidate()
    }

    private fun setupSlider() {
        sliderRadius.addOnChangeListener { _, feet, fromUser ->
            updateRadiusLabel(feet)
            currentRadiusMeters = feetToMeters(feet)
            drawOrUpdateCircle()
            if (fromUser && mapContainer.width > 0) {
                fitCameraToRadius(true)
            }
        }
    }

    private fun updateRadiusLabel(f: Float) { tvRadiusValue.text = "${f.toInt()} ft" }
    private fun feetToMeters(f: Float): Double = f * 0.3048

    @SuppressLint("DefaultLocale")
    private fun setupClickListeners() {
        fabFocus.setOnClickListener {
            smartFocus(animate = true)
        }

        btnSave.setOnClickListener {
            val name = etPlaceName.text?.toString()?.trim()
            if (name.isNullOrEmpty()) {
                etPlaceName.error = "Name required"; return@setOnClickListener
            }

            val latStr = String.format("%.6f", circleCenter.latitude)
            val lngStr = String.format("%.6f", circleCenter.longitude)
            val rad = sliderRadius.value.toInt()

            val place = SavedPlace(passedId ?: UUID.randomUUID().toString(), name, latStr, lngStr, rad)
            if (passedId != null) sharedPreferences.updatePlace(place)
            else sharedPreferences.savePlace(place)

            activity?.finish()
            Toast.makeText(context, "Location Saved", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
}