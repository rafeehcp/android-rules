package com.rafeeh.rules.ui

import android.annotation.SuppressLint
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/** OpenStreetMap view: tap to pick a point; shows the pin and its geofence circle. */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun MapPicker(
    point: GeoPoint?,
    radiusM: Float,
    focus: GeoPoint?,
    onPick: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(if (point != null) 17.0 else 4.0)
                controller.setCenter(point ?: GeoPoint(20.0, 78.0))
                setOnTouchListener { v, _ -> v.parent.requestDisallowInterceptTouchEvent(true); false }
            }
        },
        onRelease = { it.onDetach() },
        update = { map ->
            map.overlays.clear()
            map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { onPick(p); return true }
                override fun longPressHelper(p: GeoPoint): Boolean { onPick(p); return true }
            }))
            if (point != null) {
                map.overlays.add(Polygon(map).apply {
                    points = Polygon.pointsAsCircle(point, radiusM.toDouble())
                    fillPaint.color = Color.argb(50, 66, 133, 244)
                    outlinePaint.color = Color.rgb(66, 133, 244)
                    outlinePaint.strokeWidth = 4f
                    setOnClickListener { _, _, _ -> false }
                })
                map.overlays.add(Marker(map).apply {
                    position = point
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ -> true }
                })
            }
            if (focus != null && map.tag != focus) {
                map.tag = focus
                map.controller.animateTo(focus, 17.0, 600L)
            }
            map.invalidate()
        },
    )
}
