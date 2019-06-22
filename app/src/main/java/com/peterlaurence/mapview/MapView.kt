package com.peterlaurence.mapview

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.peterlaurence.mapview.core.*
import com.peterlaurence.mapview.layout.ZoomPanLayout
import com.peterlaurence.mapview.markers.MarkerLayout
import com.peterlaurence.mapview.view.TileCanvasView
import com.peterlaurence.mapview.viewmodel.TileCanvasViewModel
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlin.coroutines.CoroutineContext

/**
 * The [MapView] is a subclass of [ZoomPanLayout], specialized for displaying
 * [deepzoom](https://geoservices.ign.fr/documentation/geoservices/wmts.html) maps.
 *
 * @author peterLaurence on 31/05/2019
 */
class MapView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        ZoomPanLayout(context, attrs, defStyleAttr), CoroutineScope {

    private lateinit var visibleTilesResolver: VisibleTilesResolver
    private var job = Job()

    private var tileSize: Int = 256
    private lateinit var tileCanvasView: TileCanvasView
    private lateinit var tileCanvasViewModel: TileCanvasViewModel
    lateinit var markerLayout: MarkerLayout
        private set
    lateinit var coordinateTranslater: CoordinateTranslater
        private set

    private lateinit var configuration: MapViewConfiguration

    private lateinit var throttledTask: SendChannel<Unit>
    private var shouldRelayoutChildren = false
    private val scaleChangeListeners = mutableListOf<ScaleChangeListener>()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    /**
     * Configure the [MapView], with a [MapViewConfiguration]. Other settings can be set using dedicated
     * public methods of the [MapView].
     *
     * There are two conventions when using [MapView].
     * 1. The provided [MapViewConfiguration.levelCount] will define the zoomLevels index that the provided
     * [MapViewConfiguration.tileStreamProvider] will be given for its [TileStreamProvider#zoomLevels].
     * The zoomLevels will be [0 ; [MapViewConfiguration.levelCount]-1].
     *
     * 2. A map is made of levels with level p+1 being twice bigger than level p.
     * The last level will be at scale 1. So all levels have scales between 0 and 1.
     *
     * So it is assumed that the scale of level 1 is twice the scale at level 0, and so on until
     * last level [MapViewConfiguration.levelCount] - 1 (which has scale 1).
     *
     * @param config the [MapViewConfiguration] which defines a set af mandatory parameters
     */
    fun configure(config: MapViewConfiguration) {
        /* Save the configuration */
        configuration = config

        super.setSize(config.fullWidth, config.fullHeight)
        visibleTilesResolver = VisibleTilesResolver(config.levelCount, config.fullWidth, config.fullHeight)
        tileCanvasViewModel = TileCanvasViewModel(this, config.tileSize, visibleTilesResolver,
                config.tileStreamProvider, config.workerCount)
        this.tileSize = config.tileSize

        initChildViews()

        setStartScale(config.startScale)
        setMaxScale(config.maxScale)

        startInternals()
    }

    /**
     * Set the relative coordinates of the edges. It usually are projected values obtained from:
     * lat/lon --> projection --> X/Y (or Northing/Easting)
     * It can also be just latitude and longitude values, but it's the responsibility of the parent
     * hierarchy to provide lat/lon values in other public methods where relative coordinates are
     * expected (like when markers are added).
     *
     * @param left   The left edge of the rectangle used when calculating position
     * @param top    The top edge of the rectangle used when calculating position
     * @param right  The right edge of the rectangle used when calculating position
     * @param bottom The bottom edge of the rectangle used when calculating position
     */
    fun defineBounds(left: Double, top: Double, right: Double, bottom: Double) {
        val width = configuration.fullWidth
        val height = configuration.fullHeight
        coordinateTranslater = CoordinateTranslater(width, height, left, top, right, bottom)
    }

    fun addScaleChangeListener(listener: ScaleChangeListener) {
        scaleChangeListeners.add(listener)
    }

    fun removeScaleChangeListener(listener: ScaleChangeListener) {
        scaleChangeListeners.remove(listener)
    }

    /**
     * Stop everything.
     * [MapView] then does necessary housekeeping. After this call, the [MapView] should be removed
     * from all view trees.
     */
    fun destroy() {
        scaleChangeListeners.clear()
        job.cancel()
    }

    private fun initChildViews() {
        /* Remove the TileCanvasView if it was already added */
        if (this::tileCanvasView.isInitialized) {
            removeView(tileCanvasView)
        }
        tileCanvasView = TileCanvasView(context, tileCanvasViewModel, tileSize, visibleTilesResolver)
        addView(tileCanvasView, 0)

        if (!this::markerLayout.isInitialized) {
            markerLayout = MarkerLayout(context)
            addView(markerLayout)
        }
    }

    private fun setStartScale(startScale: Float?) {
        scale = startScale ?: (visibleTilesResolver.getScaleForLevel(0) ?: 1f)
    }

    private fun renderVisibleTilesThrottled() {
        if (this::throttledTask.isInitialized) {
            throttledTask.offer(Unit)
        }
    }

    private fun startInternals() {
        throttledTask = throttle {
            updateViewport()
        }
    }

    private fun updateViewport() {
        val viewport = getCurrentViewport()
        tileCanvasViewModel.setViewport(viewport)

        checkChildrenRelayout(viewport.right - viewport.left, viewport.bottom - viewport.top)
    }


    private fun getCurrentViewport(): Viewport {
        val left = scrollX
        val top = scrollY
        val right = left + width
        val bottom = top + height

        return Viewport(left, top, right, bottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        renderVisibleTilesThrottled()
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        renderVisibleTilesThrottled()
    }

    override fun onScaleChanged(currentScale: Float, previousScale: Float) {
        super.onScaleChanged(currentScale, previousScale)

        visibleTilesResolver.setScale(currentScale)
        tileCanvasView.setScale(currentScale)
        markerLayout.setScale(currentScale)

        scaleChangeListeners.forEach {
            it.onScaleChanged(currentScale)
        }

        if (shouldRelayoutChildren) {
            tileCanvasView.shouldRequestLayout()
        }
        renderVisibleTilesThrottled()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        markerLayout.removeAllCallout()
        return super.onTouchEvent(event)
    }

    override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
        val x = scrollX + event.x.toInt() - offsetX
        val y = scrollY + event.y.toInt() - offsetY
        markerLayout.processHit(x, y)
        return super.onSingleTapConfirmed(event)
    }

    /**
     * We only need a child view calling a [requestLayout] when either:
     * * the height of the viewport becomes greater than the scaled [baseHeight] of the MapView
     * * the width of the viewport becomes greater than the scaled [baseWidth] of the MapView
     * The [requestLayout] has to be called from child view. If it's done from the MapView
     * itself, it impacts performance as it triggers too much computations.
     */
    private fun checkChildrenRelayout(viewportWidth: Int, viewportHeight: Int) {
        shouldRelayoutChildren =
                ((viewportHeight > baseHeight * scale) || (viewportWidth > baseWidth * scale))
    }

    override fun onSaveInstanceState(): Parcelable? {
        val parentState = super.onSaveInstanceState() ?: Bundle()
        return SavedState(parentState, scale, centerX = scrollX + halfWidth,
                centerY = scrollY + halfHeight)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(state)

        val savedState = state as SavedState

        setScaleForced(savedState.scale, savedState.scale)
        post {
            scrollToAndCenter(savedState.centerX, savedState.centerY)
        }
    }
}

/**
 * The set of parameters of the [MapView]. Some of them are mandatory:
 * [levelCount], [fullWidth], [fullHeight], [tileSize], [tileStreamProvider].
 *
 * @param levelCount the number of levels
 * @param fullWidth the width of the map in pixels at scale 1
 * @param fullHeight the height of the map in pixels at scale 1
 * @param tileSize the size of tiles (must be squares)
 * @param tileStreamProvider the tiles provider
 */
data class MapViewConfiguration(val levelCount: Int, val fullWidth: Int, val fullHeight: Int,
                                val tileSize: Int, val tileStreamProvider: TileStreamProvider) {
    var workerCount = Runtime.getRuntime().availableProcessors() - 1
    private set

    var maxScale = 1f
    private set

    var startScale: Float? = null
    private set


    /**
     * Define the size of the thread pool that will handle tile decoding. In some situations, a pool
     * of several times the numbers of cores is suitable. Whereas sometimes we want to limit to just
     * 1, as some tile providers forbid multi threading to limit the load of the remote servers.
     * By default, we set this to the number of cores minus one.
     */
    fun setWorkerCount(n: Int): MapViewConfiguration {
        if (n > 0) workerCount = n
        return this
    }

    fun setMaxScale(scale: Float): MapViewConfiguration {
        maxScale = scale
        return this
    }

    /**
     * Set the start scale of the [MapView]. Note that it will be constrained by the
     * [ZoomPanLayout.MinimumScaleMode]. By default, the minimum scale mode is
     * [ZoomPanLayout.MinimumScaleMode.FIT] and on startup, the [MapView] will try to set the scale
     * which corresponds to the level 0, but constrained with the minimum scale mode. So by default,
     * the startup scale can be also the minimum scale.
     */
    fun setStartScale(scale: Float): MapViewConfiguration {
        startScale = scale
        return this
    }
}

@Parcelize
data class SavedState(val parcelable: Parcelable, val scale: Float, val centerX: Int, val centerY: Int) : View.BaseSavedState(parcelable)

interface ScaleChangeListener {
    fun onScaleChanged(scale: Float)
}
