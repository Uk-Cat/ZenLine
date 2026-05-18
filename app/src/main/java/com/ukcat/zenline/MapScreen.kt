package com.ukcat.zenline

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.Typeface
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.Overlay
import android.graphics.drawable.Drawable

@Composable
fun OSMMapView(
    modifier: Modifier = Modifier,
    busStops: List<TflStopPoint> = emptyList(),
    routeSequence: TflRouteSequence? = null,
    selectedLocation: GeoPoint? = null,
    isDarkMode: Boolean = false,
    onLocationChanged: (GeoPoint) -> Unit = {},
    onStopSelected: (TflStopPoint) -> Unit = {},
    onMapClicked: () -> Unit = {},
    onMapLoaded: (MapView) -> Unit = {}
) {
    val context = LocalContext.current

    remember {
        Configuration.getInstance().userAgentValue = context.packageName
        true
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(51.5074, -0.1278))
        }
    }

    val bubbleOverlay = remember { BusStopBubbleOverlay(mapView) }

    val mapEventsReceiver = remember {
        object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                bubbleOverlay.hideBubble()
                onMapClicked()
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        }
    }

    val mapEventsOverlay = remember {
        org.osmdroid.views.overlay.MapEventsOverlay(mapEventsReceiver)
    }

    LaunchedEffect(isDarkMode) {
        val filter = if (isDarkMode) {
            org.osmdroid.views.overlay.TilesOverlay.INVERT_COLORS
        } else {
            null
        }
        mapView.overlayManager.tilesOverlay.setColorFilter(filter)
        mapView.overlayManager.tilesOverlay.setLoadingBackgroundColor(
            if (isDarkMode) Color.BLACK else Color.WHITE
        )
        mapView.invalidate()
    }

    fun createStopIcon(text: String): Drawable {
        val size = 80
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.apply {
            color = Color.WHITE
            textSize = 32f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val displayLetter = if (text.startsWith("Stop ")) text.removePrefix("Stop ") else text

        val textBounds = Rect()
        paint.getTextBounds(displayLetter, 0, displayLetter.length, textBounds)
        val textY = (size / 2f) - textBounds.centerY()
        canvas.drawText(displayLetter, size / 2f, textY, paint)

        return BitmapDrawable(context.resources, bitmap)
    }

    val locationIcon = remember {
        val size = 100
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = size / 2f
        val centerY = size / 2f
        val paint = Paint().apply { isAntiAlias = true }

        paint.color = Color.parseColor("#334285F4")
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, size / 2f, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, size / 3.5f, paint)

        paint.color = Color.parseColor("#4285F4")
        canvas.drawCircle(centerX, centerY, size / 4.5f, paint)
        bitmap
    }

    val directionIcon = remember {
        val size = 250
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = size / 2f
        val centerY = size / 2f
        val paint = Paint().apply { isAntiAlias = true }

        val path = Path()
        path.moveTo(centerX, centerY)
        val radius = size / 2f - 10f
        val rectF = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        path.arcTo(rectF, 235f, 70f)
        path.close()

        paint.shader = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(Color.parseColor("#AA4285F4"), Color.parseColor("#004285F4")),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null

        paint.color = Color.parseColor("#334285F4")
        canvas.drawCircle(centerX, centerY, 50f, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, 28f, paint)

        paint.color = Color.parseColor("#4285F4")
        canvas.drawCircle(centerX, centerY, 22f, paint)
        bitmap
    }

    val myLocationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            setDirectionArrow(locationIcon, directionIcon)
            setPersonHotspot(locationIcon.width / 2f, locationIcon.height / 2f)

            runOnFirstFix {
                val location = myLocation
                if (location != null) {
                    onLocationChanged(GeoPoint(location.latitude, location.longitude))
                }
            }
        }
    }

    LaunchedEffect(routeSequence) {
        val polylinesToRemove = mapView.overlays.filterIsInstance<org.osmdroid.views.overlay.Polyline>()
        mapView.overlays.removeAll(polylinesToRemove)

        routeSequence?.let { sequence ->
            val polyline = org.osmdroid.views.overlay.Polyline(mapView).apply {
                outlinePaint.color = Color.RED
                outlinePaint.strokeWidth = 10f
            }
            val points = mutableListOf<GeoPoint>()

            if (sequence.lineStrings.isNotEmpty()) {
                fun extractPoints(array: com.google.gson.JsonArray) {
                    if (array.size() == 2 && array.get(0).isJsonPrimitive && array.get(1).isJsonPrimitive) {
                        points.add(GeoPoint(array.get(1).asDouble, array.get(0).asDouble))
                    } else {
                        for (i in 0 until array.size()) {
                            val element = array.get(i)
                            if (element.isJsonArray) {
                                extractPoints(element.asJsonArray)
                            }
                        }
                    }
                }

                sequence.lineStrings.forEach { json ->
                    try {
                        val parsed = com.google.gson.JsonParser.parseString(json)
                        if (parsed.isJsonArray) {
                            extractPoints(parsed.asJsonArray)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (points.isEmpty()) {
                points.addAll(sequence.stopPointSequences.flatMap { it.stopPoint }.map { GeoPoint(it.lat, it.lon) })
            }

            polyline.setPoints(points)
            mapView.overlays.add(polyline)

            if (points.isNotEmpty()) {
                mapView.controller.animateTo(points[points.size / 2])
                mapView.controller.setZoom(14.0)
            }
        }

        if (mapView.overlays.contains(myLocationOverlay)) {
            mapView.overlays.remove(myLocationOverlay)
            mapView.overlays.add(myLocationOverlay)
        }

        if (mapView.overlays.contains(bubbleOverlay)) {
            mapView.overlays.remove(bubbleOverlay)
        }
        mapView.overlays.add(bubbleOverlay)

        mapView.invalidate()
    }

    var selectedStopName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(busStops, routeSequence, selectedLocation) {
        val overlaysToRemove = mapView.overlays.filterIsInstance<Marker>()
        mapView.overlays.removeAll(overlaysToRemove)

        val stopsToShow = if (routeSequence != null) {
            routeSequence.stopPointSequences.flatMap { it.stopPoint }
        } else {
            busStops
        }

        stopsToShow.forEach { stop ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(stop.lat, stop.lon)
                title = if (!stop.indicator.isNullOrBlank()) {
                    val letter = stop.indicator.removePrefix("Stop ").trim()
                    if (letter.isNotEmpty()) "${stop.commonName} ($letter)" else stop.commonName
                } else {
                    stop.commonName
                }
                snippet = null
                icon = createStopIcon(stop.stopLetter ?: stop.indicator ?: "")
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { m, _ ->
                    onStopSelected(stop)
                    bubbleOverlay.showBubble(m.position, m.title ?: "", mapView)
                    true
                }
            }
            mapView.overlays.add(marker)

            if (selectedLocation != null && selectedLocation.latitude == stop.lat && selectedLocation.longitude == stop.lon) {
                bubbleOverlay.showBubble(marker.position, marker.title ?: "", mapView)
            }
        }

        if (selectedLocation == null) {
            bubbleOverlay.hideBubble()
        }

        if (mapView.overlays.contains(myLocationOverlay)) {
            mapView.overlays.remove(myLocationOverlay)
            mapView.overlays.add(myLocationOverlay)
        }

        mapView.invalidate()
    }

    LaunchedEffect(selectedLocation) {
        selectedLocation?.let {
            myLocationOverlay.disableFollowLocation()
            mapView.controller.animateTo(it)
            mapView.controller.setZoom(18.5)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            myLocationOverlay.enableMyLocation()
            myLocationOverlay.enableFollowLocation()
            if (!mapView.overlays.contains(myLocationOverlay)) {
                mapView.overlays.add(myLocationOverlay)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!mapView.overlays.contains(mapEventsOverlay)) {
            mapView.overlays.add(0, mapEventsOverlay)
        }
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation || hasCoarseLocation) {
            myLocationOverlay.enableMyLocation()
            myLocationOverlay.enableFollowLocation()
            if (!mapView.overlays.contains(myLocationOverlay)) {
                mapView.overlays.add(myLocationOverlay)
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        myLocationOverlay.enableMyLocation()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    myLocationOverlay.disableMyLocation()
                    mapView.onPause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            myLocationOverlay.disableMyLocation()
            myLocationOverlay.disableFollowLocation()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            onMapLoaded(view)
        }
    )
}

class BusStopBubbleOverlay(private val mapView: MapView) : Overlay() {
    private var bubblePosition: GeoPoint? = null
    private var bubbleText: String = ""
    private var textPaint = TextPaint().apply {
        color = Color.parseColor("#1A1A1A")
        textSize = 36f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    private var shadowPaint = Paint().apply {
        color = Color.argb(50, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun showBubble(position: GeoPoint, text: String, mapView: MapView) {
        bubblePosition = position
        bubbleText = text
        mapView.invalidate()
    }

    fun hideBubble() {
        bubblePosition = null
        bubbleText = ""
        mapView.invalidate()
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || bubblePosition == null || bubbleText.isEmpty()) return

        val projection = mapView.projection
        val point = projection.toPixels(bubblePosition, null)

        val textBounds = Rect()
        textPaint.getTextBounds(bubbleText, 0, bubbleText.length, textBounds)

        val paddingH = 48
        val paddingV = 32
        val cornerRadius = 24f

        val textWidth = textBounds.width() + paddingH * 2
        val textHeight = textBounds.height() + paddingV * 2

        val bubbleRect = RectF(
            (point.x - textWidth / 2).toFloat(),
            (point.y - textHeight - 60).toFloat(),
            (point.x + textWidth / 2).toFloat(),
            (point.y - 60).toFloat()
        )

        val shadowOffset = 12f
        canvas.drawRoundRect(
            bubbleRect.left + shadowOffset,
            bubbleRect.top + shadowOffset,
            bubbleRect.right + shadowOffset,
            bubbleRect.bottom + shadowOffset,
            cornerRadius,
            cornerRadius,
            shadowPaint
        )

        val bgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
            setShadowLayer(8f, 0f, 4f, Color.argb(80, 0, 0, 0))
        }
        canvas.drawRoundRect(bubbleRect, cornerRadius, cornerRadius, bgPaint)

        val strokePaint = Paint().apply {
            color = Color.parseColor("#D0D0D0")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(bubbleRect, cornerRadius, cornerRadius, strokePaint)

        val pointerPath = Path()
        val pointerWidth = 30f
        val pointerHeight = 20f
        val pointerCenterX = point.x.toFloat()
        val pointerTop = bubbleRect.bottom
        val pointerBottom = point.y - 60f

        pointerPath.moveTo(pointerCenterX - pointerWidth / 2, pointerTop)
        pointerPath.lineTo(pointerCenterX, pointerBottom)
        pointerPath.lineTo(pointerCenterX + pointerWidth / 2, pointerTop)
        pointerPath.close()

        canvas.drawPath(pointerPath, bgPaint)
        canvas.drawPath(pointerPath, strokePaint)

        val textX = point.x - textBounds.width() / 2f
        val textY = bubbleRect.centerY() + textBounds.height() / 2f
        canvas.drawText(bubbleText, textX, textY, textPaint)
    }
}