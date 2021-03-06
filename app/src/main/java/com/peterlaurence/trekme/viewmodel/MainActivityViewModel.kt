package com.peterlaurence.trekme.viewmodel

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peterlaurence.trekme.MainActivity
import com.peterlaurence.trekme.core.TrekMeContext
import com.peterlaurence.trekme.core.map.Map
import com.peterlaurence.trekme.core.map.maploader.MapLoader
import com.peterlaurence.trekme.core.settings.Settings
import com.peterlaurence.trekme.core.settings.StartOnPolicy
import com.peterlaurence.trekme.model.map.MapRepository
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus


/**
 * This view-model is attached to the [MainActivity].
 * It manages model specific considerations that are set here outside of the main activity which
 * should mainly used to manage fragments.
 *
 * Avoids excessive [MapLoader.updateMaps] calls by managing an internal [attemptedAtLeastOnce] flag.
 * It is also important because the activity might start after an Intent with a result code. In this
 * case, [attemptedAtLeastOnce] is true and we shall not trigger background processing.
 *
 * @author P.Laurence on 07/10/2019
 */
class MainActivityViewModel @ViewModelInject constructor(
        private val trekMeContext: TrekMeContext,
        private val settings: Settings,
        private val mapRepository: MapRepository
): ViewModel() {
    private var attemptedAtLeastOnce = false

    /**
     * When the [MainActivity] first starts, we either:
     * * show the last viewed map
     * * show the map list
     */
    fun onActivityStart() {
        viewModelScope.launch {
            if (attemptedAtLeastOnce) return@launch
            attemptedAtLeastOnce = true // remember that we tried once

            MapLoader.clearMaps()
            trekMeContext.mapsDirList?.also { dirList ->
                MapLoader.updateMaps(dirList)
            }

            when (settings.getStartOnPolicy()) {
                StartOnPolicy.MAP_LIST -> EventBus.getDefault().post(ShowMapListEvent())
                StartOnPolicy.LAST_MAP -> {
                    val id = settings.getLastMapId()
                    val found = id?.let {
                        val map = MapLoader.getMap(id)
                        map?.let {
                            mapRepository.setCurrentMap(map)
                            EventBus.getDefault().post(ShowMapViewEvent(map))
                            true
                        } ?: false
                    } ?: false

                    if (!found) {
                        /* Fall back to show the map list */
                        EventBus.getDefault().post(ShowMapListEvent())
                    }
                }
            }
        }
    }

    fun getMapIndex(mapId: Int): Int = MapLoader.maps.indexOfFirst { it.id == mapId }
}

/**
 * Events that this view-model fires, intended to the main activity.
 */
class ShowMapListEvent

data class ShowMapViewEvent(val map: Map)