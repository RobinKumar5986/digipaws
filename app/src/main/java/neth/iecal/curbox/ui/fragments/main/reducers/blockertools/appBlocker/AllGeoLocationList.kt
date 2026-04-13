package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.appBlocker

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import neth.iecal.curbox.R
import neth.iecal.curbox.data.sharedpreferences.SharedPreferences
import neth.iecal.curbox.ui.activity.FragmentActivity

class AllGeoLocationList : Fragment() {

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var adapter: GeoPlaceAdapter

    companion object {
        const val FRAGMENT_ID = "geo_blocker_groups"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_all_geo_location_list, container, false)
        sharedPrefs = SharedPreferences(requireContext())

        val rvPlaces = view.findViewById<RecyclerView>(R.id.rvPlaces)
        adapter = GeoPlaceAdapter(prepareList().toMutableList())
        rvPlaces.adapter = adapter

        return view
    }

    override fun onResume() {
        super.onResume()
        adapter.updateData(prepareList().toMutableList())
    }

    private fun prepareList(): List<GeoItem> {
        val list = mutableListOf<GeoItem>()
        list.add(GeoItem(null, "Add a new Place", R.drawable.ic_add, false))

        val saved = sharedPrefs.getAllPlaces()
        saved.forEach {
            list.add(GeoItem(it.id, it.name ?: "Unknown", R.drawable.ic_location_geo, true))
        }

        list.add(GeoItem(null, "Home", R.drawable.ic_home_geo, false))
        list.add(GeoItem(null, "School", R.drawable.ic_school_geo, false))
        list.add(GeoItem(null, "Office", R.drawable.ic_office_geo, false))
        list.add(GeoItem(null, "Gym", R.drawable.ic_gym_geo, false))
        list.add(GeoItem(null, "Grocery Store", R.drawable.ic_shop_geo, false))

        return list
    }

    private data class GeoItem(
        val id: String?,
        val name: String,
        val iconRes: Int,
        val isSaved: Boolean
    )

    private inner class GeoPlaceAdapter(private var items: MutableList<GeoItem>) :
        RecyclerView.Adapter<GeoPlaceAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivIcon)
            val name: TextView = view.findViewById(R.id.tvPlaceName)
            val action: ImageView = view.findViewById(R.id.ivAction)
            val container: View = view.findViewById(R.id.rowContainer)
        }

        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newItems: MutableList<GeoItem>) {
            this.items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_geo_location, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            if (item.isSaved || position == 0) {
                holder.name.text = item.name
            } else {
                holder.name.text = "Add your " + item.name
            }

            holder.icon.setImageResource(item.iconRes)

            if (item.isSaved) {
                holder.action.visibility = View.VISIBLE
                holder.action.setImageResource(R.drawable.ic_close)
            } else {
                holder.action.visibility = View.GONE
            }

            holder.container.setOnClickListener {
                val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                    putExtra("fragment", GeoBlockerGroupsFragment.FRAGMENT_ID)

                    if (item.isSaved) {
                        val place = sharedPrefs.getAllPlaces().find { it.id == item.id }
                        place?.let {
                            putExtra("id", it.id)
                            putExtra("name", it.name)
                            putExtra("lat", it.lat)
                            putExtra("lng", it.lng)
                            putExtra("radius", it.radius)
                        }
                    } else if (position != 0) {
                        putExtra("name", item.name)
                    }
                }
                startActivity(intent)
            }

            holder.action.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Remove Place")
                    .setMessage("Are you sure you want to remove the geo location from the saved place?")
                    .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
                    .setPositiveButton("Proceed") { _, _ ->
                        item.id?.let { id ->
                            sharedPrefs.removePlace(id)
                            items.removeAt(position)
                            notifyItemRemoved(position)
                            notifyItemRangeChanged(position, items.size)
                        }
                    }.show()
            }
        }

        override fun getItemCount() = items.size
    }
}