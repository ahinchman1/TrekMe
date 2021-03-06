package com.peterlaurence.trekme.viewmodel.record

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peterlaurence.trekme.R
import com.peterlaurence.trekme.core.events.GenericMessage
import com.peterlaurence.trekme.core.map.BoundingBox
import com.peterlaurence.trekme.core.map.intersects
import com.peterlaurence.trekme.core.map.maploader.MapLoader
import com.peterlaurence.trekme.core.settings.Settings
import com.peterlaurence.trekme.core.track.TrackImporter
import com.peterlaurence.trekme.service.GpxRecordService
import com.peterlaurence.trekme.service.event.GpxFileWriteEvent
import com.peterlaurence.trekme.ui.dialogs.MapSelectedEvent
import com.peterlaurence.trekme.ui.record.components.events.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File

/**
 * The business logic for importing a recording (gpx file) into a map.
 *
 * @author P.Laurence on 16/04/20
 */
class RecordViewModel @ViewModelInject constructor(
        private val trackImporter: TrackImporter,
        private val app: Application,
        private val settings: Settings
) : ViewModel() {
    private var recordingsSelected = listOf<File>()

    init {
        EventBus.getDefault().register(this)
    }

    /**
     * Whenever a [GpxFileWriteEvent] is emitted, import the gpx track in all maps which intersects
     * the [BoundingBox] of the gpx track.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onGpxFileWriteEvent(event: GpxFileWriteEvent) = viewModelScope.launch {
        val gpx = event.gpx

        val boundingBox = gpx.metadata?.bounds?.let {
            BoundingBox(it.minLat, it.maxLat, it.minLon, it.maxLon)
        } ?: return@launch

        var importCount = 0
        supervisorScope {
            MapLoader.maps.forEach { map ->
                launch {
                    if (map.intersects(boundingBox) == true) {
                        /* Import the new route */
                        val result = trackImporter.applyGpxToMap(gpx, map)
                        if (result is TrackImporter.GpxImportResult.GpxImportOk && result.newRouteCount >= 1) {
                            importCount++
                        }
                    }
                }
            }
        }
        if (importCount > 0) {
            val msg = app.applicationContext.getString(R.string.automatic_import_feedback, importCount)
            EventBus.getDefault().post(GenericMessage(msg))
        }
    }

    fun setSelectedRecordings(recordings: List<File>) {
        recordingsSelected = recordings
    }

    /**
     * The business logic of parsing a GPX file.
     */
    @Subscribe
    fun onMapSelectedForRecord(event: MapSelectedEvent) {
        val map = MapLoader.getMap(event.mapId) ?: return

        val recording = recordingsSelected.firstOrNull() ?: return

        viewModelScope.launch {
            trackImporter.applyGpxFileToMap(recording, map).let {
                /* Once done, all we want is to post an event */
                EventBus.getDefault().post(it)
            }
        }
    }

    @Subscribe
    fun onRequestStartEvent(event: RequestStartEvent) {
        /* Check battery optimization, and inform the user if needed */
        if (isBatteryOptimized()) {
            EventBus.getDefault().post(RequestDisableBatteryOpt())
        }

        /* Start the service */
        val intent = Intent(app, GpxRecordService::class.java)
        app.startService(intent)

        /* The background location permission is asked after the dialog is closed. But it doesn't
         * matter that the recording is already started - it works even when the permission is
         * granted during the recording. */
        if (settings.isShowingLocationDisclaimer()) {
            EventBus.getDefault().post(ShowLocationDisclaimerEvent())
        } else {
            /* If the disclaimer is discarded, ask for the permission anyway */
            requestBackgroundLocationPerm()
        }
    }

    /**
     * Check the battery optimization.
     */
    private fun isBatteryOptimized(): Boolean {
        val pm = app.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val name = app.applicationContext.packageName
        return !pm.isIgnoringBatteryOptimizations(name)
    }

    @Subscribe
    fun onLocationDisclaimerClosed(event: LocationDisclaimerClosedEvent) {
        requestBackgroundLocationPerm()
    }

    private fun requestBackgroundLocationPerm() {
        EventBus.getDefault().post(RequestBackgroundLocationPermission())
    }

    @Subscribe
    fun onDiscardLocationDisclaimer(event: DiscardLocationDisclaimerEvent) {
        settings.discardLocationDisclaimer()
    }

    override fun onCleared() {
        super.onCleared()

        EventBus.getDefault().unregister(this)
    }
}