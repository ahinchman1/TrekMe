package com.peterlaurence.trekme.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.peterlaurence.trekme.R
import com.peterlaurence.trekme.core.map.Map
import com.peterlaurence.trekme.core.map.maploader.MapLoader
import org.greenrobot.eventbus.EventBus


/**
 * A dialog that displays the list of maps. The user can only select one map, and confirm or not the
 * selection. Upon selection of a map, a [MapSelectedEvent] is fired.
 *
 * @author P.Laurence on 01/09/2018
 */
class MapChoiceDialog : DialogFragment(), MapChoiceSelectionListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var mapChoiceAdapter: MapChoiceAdapter
    private var selectedIndex: Int = -1

    private var mapSelected: Map? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        recyclerView = RecyclerView(requireActivity().baseContext)
        val llm = LinearLayoutManager(activity)
        recyclerView.layoutManager = llm

        /* Fetch the list of maps */
        val mapList = MapLoader.maps

        /* Restore the selection after device rotation */
        selectedIndex = savedInstanceState?.getInt(KEY_BUNDLE_MAP_INDEX) ?: -1
        if (selectedIndex != -1) {
            mapSelected = mapList.getOrNull(selectedIndex)
        }

        mapChoiceAdapter = MapChoiceAdapter(mapList, this, selectedIndex)
        recyclerView.adapter = mapChoiceAdapter

        val builder = AlertDialog.Builder(requireActivity())
        builder.setTitle(getString(R.string.choose_a_map))
        builder.setView(recyclerView)
        builder.setPositiveButton(getString(R.string.ok_dialog)) { _, _ ->
            if (mapSelected != null) {
                EventBus.getDefault().post(MapSelectedEvent(mapSelected?.id!!))
            }
        }
        builder.setNegativeButton(getString(R.string.cancel_dialog_string)) { _, _ ->
            dismiss()
        }

        return builder.create()
    }

    override fun onMapSelected(map: Map, position: Int) {
        selectedIndex = position
        mapSelected = map
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putInt(KEY_BUNDLE_MAP_INDEX, selectedIndex)
    }
}

class MapSelectedEvent(val mapId: Int)

private class MapChoiceAdapter(private val mapList: List<Map>, val listener: MapChoiceSelectionListener,
                               selectedIndex: Int) : RecyclerView.Adapter<MapChoiceViewHolder>(), MapChoiceItemClickListener {
    private var index = selectedIndex
    private var oldIndex = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MapChoiceViewHolder {
        val ctx = parent.context
        val v = LayoutInflater.from(ctx).inflate(R.layout.map_choice_row, parent, false)
        return MapChoiceViewHolder(v)
    }

    override fun getItemCount(): Int {
        return mapList.size
    }

    override fun onBindViewHolder(holder: MapChoiceViewHolder, position: Int) {
        val map = mapList[position]
        holder.textView.text = map.name
        holder.index = position

        holder.setItemClickListener(this)

        if (position == index) {
            holder.layout.setBackgroundColor(0x882196F3.toInt())
        } else {
            if (position % 2 == 0) {
                holder.layout.setBackgroundColor(0xFFEDEDED.toInt())
            } else {
                holder.layout.setBackgroundColor(0xFFFFFFFF.toInt())
            }
        }
    }

    override fun onItemClick(position: Int) {
        oldIndex = index
        index = position
        notifyItemChanged(oldIndex)
        notifyItemChanged(index)

        listener.onMapSelected(mapList[position], position)
    }
}

private class MapChoiceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val layout: ConstraintLayout = itemView.findViewById(R.id.map_choice_layout)
    val textView: TextView = itemView.findViewById(R.id.map_choice_textview)
    var index: Int = -1

    fun setItemClickListener(listener: MapChoiceItemClickListener) {
        itemView.setOnClickListener { listener.onItemClick(index) }
    }
}

private const val KEY_BUNDLE_MAP_INDEX = "mapIndex"

private interface MapChoiceItemClickListener {
    fun onItemClick(position: Int)
}

private interface MapChoiceSelectionListener {
    fun onMapSelected(map: Map, position: Int)
}