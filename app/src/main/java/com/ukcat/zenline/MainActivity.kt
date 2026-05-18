package com.ukcat.zenline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Check
import com.ukcat.zenline.ui.theme.ZenLineTheme
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import androidx.lifecycle.viewmodel.compose.viewModel
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

import androidx.compose.material3.SheetValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: BusStopViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val accentColorHex by viewModel.accentColor.collectAsState()
            
            CustomZenLineTheme(isDarkMode = isDarkMode, accentColorHex = accentColorHex) {
                MainContent(viewModel)
            }
        }
    }
}

@Composable
fun CustomZenLineTheme(
    isDarkMode: Boolean,
    accentColorHex: String,
    content: @Composable () -> Unit
) {
    val primaryColor = Color(android.graphics.Color.parseColor(accentColorHex))
    val colorScheme = if (isDarkMode) {
        darkColorScheme(
            primary = primaryColor,
            secondary = primaryColor,
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            secondary = primaryColor,
            background = Color.White,
            surface = Color(0xFFF5F5F5),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black
        )
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(viewModel: BusStopViewModel = viewModel()) {
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = false // Allows the user to swipe down to hide completely
        )
    )
    var selectedStop by remember { mutableStateOf<TflStopPoint?>(null) }
    var showMoreArrivals by remember { mutableStateOf(false) }
    var viewingRoute by remember { mutableStateOf(false) }
    var viewingSettings by remember { mutableStateOf(false) }
    var viewingInfo by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var hasAutoSearched by remember { mutableStateOf(false) }
    
    val uiState by viewModel.uiState.collectAsState()
    val arrivalsState by viewModel.arrivalsState.collectAsState()
    val routeState by viewModel.routeState.collectAsState()
    val distanceUnit by viewModel.distanceUnit.collectAsState()
    val autoRefreshIntervalSeconds by viewModel.autoRefreshIntervalSeconds.collectAsState()
    val defaultNotificationMinutes by viewModel.defaultNotificationMinutes.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    LaunchedEffect(selectedStop, autoRefreshIntervalSeconds) {
        if (selectedStop != null && autoRefreshIntervalSeconds > 0) {
            while (true) {
                kotlinx.coroutines.delay(autoRefreshIntervalSeconds * 1000L)
                viewModel.fetchArrivals(selectedStop!!.naptanId)
            }
        }
    }

    val isSheetHidden = scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden

    LaunchedEffect(isSheetHidden) {
        if (isSheetHidden) {
            viewingSettings = false
            selectedStop = null
            viewingRoute = false
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 350.dp,
        sheetContainerColor = MaterialTheme.colorScheme.background,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxSize() // Fills remaining space, preventing black boxes
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle visual
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(32.dp)
                        .height(4.dp)
                        .padding(bottom = 8.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.LightGray,
                        shape = RoundedCornerShape(2.dp)
                    ) {}
                }

                if (viewingSettings) {
                    val searchRadius by viewModel.searchRadius.collectAsState()
                    val isDarkMode by viewModel.isDarkMode.collectAsState()
                    val accentColor by viewModel.accentColor.collectAsState()
                    val defaultNotificationMinutes by viewModel.defaultNotificationMinutes.collectAsState()
                    val autoRefreshIntervalSeconds by viewModel.autoRefreshIntervalSeconds.collectAsState()
                    val distanceUnit by viewModel.distanceUnit.collectAsState()
                    val maxStops by viewModel.maxStops.collectAsState()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Dark Mode Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dark Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                                Text("Switch the application theme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            }
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { viewModel.updateDarkMode(it) }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 2. Custom Color Customization
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Accent Color", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                            Text("Personalize ZenLine highlights", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            
                            val colorPresets = listOf(
                                "#2196F3" to "Blue",
                                "#D32F2F" to "Red",
                                "#673AB7" to "Purple",
                                "#2E7D32" to "Green",
                                "#FF9800" to "Orange",
                                "#E91E63" to "Pink"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                colorPresets.forEach { (hex, name) ->
                                    val isSelected = accentColor.equals(hex, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                color = Color(android.graphics.Color.parseColor(hex)),
                                                shape = CircleShape
                                            )
                                            .clickable { viewModel.updateAccentColor(hex) }
                                            .border(
                                                width = if (isSelected) 3.dp else 0.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 3. Search Radius Slider
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Search Radius", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                                Text("${searchRadius}m", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Text("Distance range to look for nearby stops", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            Slider(
                                value = searchRadius.toFloat(),
                                onValueChange = { viewModel.updateSearchRadius(it.toInt()) },
                                valueRange = 100f..2000f,
                                steps = 18
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 4. Default Notification Time
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Default Reminder Time", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                                Text("${defaultNotificationMinutes} min before", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Text("Suggested offset when setting new reminders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            Slider(
                                value = defaultNotificationMinutes.toFloat(),
                                onValueChange = { viewModel.updateDefaultNotificationMinutes(it.toInt()) },
                                valueRange = 1f..10f,
                                steps = 8
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 5. Max Stops Slider
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Max Stops to Display", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                                Text("$maxStops stops", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Text("Limit results to declutter stops list", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            Slider(
                                value = maxStops.toFloat(),
                                onValueChange = { viewModel.updateMaxStops(it.toInt()) },
                                valueRange = 5f..30f,
                                steps = 5
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 6. Distance Units
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Distance Units", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("metric" to "Metric (m/km)", "imperial" to "Imperial (yd/mi)").forEach { (unitId, label) ->
                                    val isSelected = distanceUnit == unitId
                                    OutlinedButton(
                                        onClick = { viewModel.updateDistanceUnit(unitId) },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(label)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 7. Auto-Refresh Interval
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Arrivals Auto-Refresh", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(15 to "15s", 30 to "30s", 60 to "60s", 0 to "Manual").forEach { (seconds, label) ->
                                    val isSelected = autoRefreshIntervalSeconds == seconds
                                    OutlinedButton(
                                        onClick = { viewModel.updateAutoRefreshIntervalSeconds(seconds) },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        ),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Text(label, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedStop == null) {
                    // NEARBY STOPS UI
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nearby Bus Stops",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    when (val state = uiState) {
                        is BusStopUiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                        is BusStopUiState.Success -> {
                            val favorites by viewModel.favorites.collectAsState()
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(state.stops.size) { index ->
                                    val stop = state.stops[index]
                                    BusStopItem(
                                        stop = stop,
                                        isFavorite = favorites.contains(stop.naptanId),
                                        distanceUnit = distanceUnit,
                                        onFavoriteToggle = { viewModel.toggleFavorite(stop.naptanId) },
                                        onClick = {
                                            selectedStop = stop
                                            viewingRoute = false
                                            viewModel.fetchArrivals(stop.naptanId)
                                            scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                                        }
                                    )
                                }
                            }
                        }
                        is BusStopUiState.Error -> Text("Error: ${state.message}", color = Color.Red)
                        else -> {}
                    }
                } else if (viewingRoute && routeState is RouteUiState.Success) {
                    // ROUTE VIEW (List of Stops)
                    val route = (routeState as RouteUiState.Success).route
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewingRoute = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Route ${route.lineId}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Towards ${route.direction}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }

                    val routeStops = route.stopPointSequences.flatMap { it.stopPoint }
                    val favorites by viewModel.favorites.collectAsState()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(routeStops.size) { index ->
                            val stop = routeStops[index]
                            RouteStopItem(
                                stop = stop,
                                index = index,
                                totalStops = routeStops.size,
                                isFavorite = favorites.contains(stop.naptanId),
                                onFavoriteToggle = { viewModel.toggleFavorite(stop.naptanId) },
                                onClick = {
                                    selectedStop = stop
                                    viewingRoute = false
                                    viewModel.fetchArrivals(stop.naptanId)
                                }
                            )
                        }
                    }
                } else {
                    // ARRIVALS VIEW
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            selectedStop = null 
                            showMoreArrivals = false
                            viewModel.clearArrivals()
                            viewModel.clearRoute()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedStop!!.commonName,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (selectedStop!!.indicator != null) {
                                Text(
                                    text = selectedStop!!.indicator!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        val favorites by viewModel.favorites.collectAsState()
                        IconButton(onClick = { viewModel.toggleFavorite(selectedStop!!.naptanId) }) {
                            Icon(
                                imageVector = if (favorites.contains(selectedStop!!.naptanId)) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (favorites.contains(selectedStop!!.naptanId)) Color(0xFFFFD700) else Color.Gray
                            )
                        }
                    }

                    when (val state = arrivalsState) {
                        is ArrivalsUiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                        is ArrivalsUiState.Success -> {
                            val displayArrivals = if (showMoreArrivals) state.arrivals else state.arrivals.take(5)
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(displayArrivals.size) { index ->
                                    val arrival = displayArrivals[index]
                                    val activeReminders by viewModel.activeReminders.collectAsState()
                                    val context = LocalContext.current
                                    ArrivalItem(
                                        arrival = arrival,
                                        isReminderSet = activeReminders.contains(arrival.id),
                                        defaultMinutes = defaultNotificationMinutes,
                                        isDarkMode = isDarkMode,
                                        onSetReminder = { notifyMins ->
                                            scheduleReminder(context, notifyMins.toInt(), arrival)
                                            viewModel.addReminder(arrival.id)
                                        },
                                        onClick = {
                                            viewModel.fetchRouteSequence(arrival.lineId, arrival.direction)
                                            viewingRoute = true
                                        }
                                    )
                                }
                                
                                if (!showMoreArrivals && state.arrivals.size > 5) {
                                    item {
                                        TextButton(onClick = { showMoreArrivals = true }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Show more")
                                        }
                                    }
                                }
                            }
                        }
                        is ArrivalsUiState.Error -> Text("Error: ${state.message}", color = Color.Red)
                        else -> {}
                    }

                    if (routeState is RouteUiState.Loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }
        }
    ) { _ -> 
        // Map fills entire background, no insets padding to avoid black box
        Box(modifier = Modifier.fillMaxSize()) {
            OSMMapView(
                modifier = Modifier.fillMaxSize(),
                busStops = if (uiState is BusStopUiState.Success) (uiState as BusStopUiState.Success).stops else emptyList(),
                routeSequence = if (routeState is RouteUiState.Success) (routeState as RouteUiState.Success).route else null,
                selectedLocation = selectedStop?.let { GeoPoint(it.lat, it.lon) },
                isDarkMode = isDarkMode,
                onLocationChanged = { location ->
                    userLocation = location
                    if (!hasAutoSearched) {
                        viewModel.fetchNearbyBusStops(location.latitude, location.longitude)
                        hasAutoSearched = true
                    }
                },
                onStopSelected = { stop ->
                    selectedStop = stop
                    viewingRoute = false
                    viewModel.fetchArrivals(stop.naptanId)
                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                },
                onMapClicked = {
                    selectedStop = null
                    viewingRoute = false
                    viewingSettings = false
                    viewModel.clearArrivals()
                    viewModel.clearRoute()
                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                }
            )

            // Floating Action Button
            if (routeState !is RouteUiState.Success) {
                FloatingActionButton(
                    onClick = {
                        viewingSettings = false
                        selectedStop = null
                        showMoreArrivals = false
                        viewingRoute = false
                        viewModel.clearArrivals()
                        viewModel.clearRoute()
                        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                        userLocation?.let { viewModel.fetchNearbyBusStops(it.latitude, it.longitude) }
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomEnd),
                    containerColor = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFFFFFFF),
                    contentColor = if (isDarkMode) Color.White else Color.Black
                ) {
                    TflLogoIcon(modifier = Modifier.size(28.dp), color = if (isDarkMode) Color.White else Color.Black)
                }
            }

            // Close Route Button (Only appears when sheet is fully hidden)
            if (routeState is RouteUiState.Success && isSheetHidden) {
                Surface(
                    modifier = Modifier
                        .padding(16.dp)
                        .padding(top = 40.dp)
                        .align(Alignment.TopEnd),
                    shape = CircleShape,
                    color = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFFFFFFF),
                    shadowElevation = 8.dp
                ) {
                    IconButton(onClick = { 
                        viewModel.clearRoute()
                        viewingRoute = false
                        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close Route", tint = if (isDarkMode) Color.White else Color.Black)
                    }
                }
            }

            // Settings & Info Buttons (Only appears in starting section)
            if (selectedStop == null && routeState !is RouteUiState.Success && !viewingSettings) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .padding(top = 40.dp)
                        .align(Alignment.TopEnd),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFFFFFFF),
                        shadowElevation = 8.dp
                    ) {
                        IconButton(onClick = { 
                            viewingSettings = true
                            scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = if (isDarkMode) Color.White else Color.Black)
                        }
                    }
                    
                    Surface(
                        shape = CircleShape,
                        color = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFFFFFFF),
                        shadowElevation = 8.dp
                    ) {
                        IconButton(onClick = { 
                            viewingInfo = true
                        }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = if (isDarkMode) Color.White else Color.Black)
                        }
                    }
                }
            }

            if (viewingInfo) {
                AlertDialog(
                    onDismissRequest = { viewingInfo = false },
                    title = { Text("App Features", style = MaterialTheme.typography.titleLarge) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("• Tap any bus stop marker to view live arrivals.", color = if (isDarkMode) Color.White else Color.Black)
                            Text("• Swipe left on an arrival to set a custom reminder.", color = if (isDarkMode) Color.White else Color.Black)
                            Text("• Tap on an arrival to see the full route sequence.", color = if (isDarkMode) Color.White else Color.Black)
                            Text("• Customize theme, colors, and alerts in Settings.", color = if (isDarkMode) Color.White else Color.Black)
                            Text("• Click the floating TfL button to reset to nearby stops.", color = if (isDarkMode) Color.White else Color.Black)
                            Text("• Tap empty map space to dismiss active selections.", color = if (isDarkMode) Color.White else Color.Black)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewingInfo = false }) {
                            Text("Got it")
                        }
                    },
                    containerColor = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFFFFFFF),
                    titleContentColor = if (isDarkMode) Color.White else Color.Black,
                    textContentColor = if (isDarkMode) Color.White else Color.Black
                )
            }
        }
    }
}

@Composable
fun BusStopItem(
    stop: TflStopPoint, 
    isFavorite: Boolean,
    distanceUnit: String,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (stop.indicator != null) "${stop.commonName} (${stop.indicator})" else stop.commonName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stop.naptanId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                
                val distanceText = if (distanceUnit == "imperial") {
                    val yards = (stop.distance * 1.09361).toInt()
                    if (yards >= 1760) {
                        val miles = yards / 1760.0
                        String.format("%.1f mi", miles)
                    } else {
                        "$yards yd"
                    }
                } else {
                    if (stop.distance >= 1000) {
                        val km = stop.distance / 1000.0
                        String.format("%.1f km", km)
                    } else {
                        "${stop.distance.toInt()} m"
                    }
                }
                
                Text(
                    text = distanceText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = if (isFavorite) 
                        Icons.Filled.Star 
                    else 
                        Icons.Filled.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun RouteStopItem(
    stop: TflStopPoint,
    index: Int,
    totalStops: Int,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit
) {
    val isFirst = index == 0
    val isLast = index == totalStops - 1
    val primaryColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!isFirst) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp)
                            .align(Alignment.TopCenter)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                }
                if (!isLast) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp)
                            .align(Alignment.BottomCenter)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                }
                Surface(
                    modifier = Modifier.size(24.dp),
                    shape = CircleShape,
                    color = primaryColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = if (stop.indicator != null) "${stop.commonName} (${stop.indicator})" else stop.commonName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        IconButton(onClick = onFavoriteToggle) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) Color(0xFFFFD700) else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrivalItem(
    arrival: TflArrival, 
    isReminderSet: Boolean, 
    defaultMinutes: Int,
    isDarkMode: Boolean,
    onSetReminder: (Float) -> Unit, 
    onClick: () -> Unit
) {
    var isConfiguringNotification by remember { mutableStateOf(false) }
    val maxMinutes = (arrival.timeToStation / 60).toFloat()
    var notifyMinutes by remember(defaultMinutes) { 
        mutableFloatStateOf(defaultMinutes.toFloat().coerceAtMost(maxMinutes).coerceAtLeast(1f)) 
    }
    
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onSetReminder(notifyMinutes)
            isConfiguringNotification = false
        }
    }

    if (isConfiguringNotification) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notify ${notifyMinutes.toInt()} min before",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    IconButton(onClick = { isConfiguringNotification = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                
                Text(
                    text = "Bus arrives in ${if (maxMinutes < 1f) "less than 1" else maxMinutes.toInt()} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                val stepsCount = if (maxMinutes > 2f) (maxMinutes - 2).toInt() else 0
                if (maxMinutes > 1f) {
                    Slider(
                        value = notifyMinutes,
                        onValueChange = { notifyMinutes = it },
                        valueRange = 1f..maxMinutes.coerceAtLeast(2f),
                        steps = stepsCount,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("Bus is due soon, notifying immediately.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f))
                }
                
                Button(
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            
                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onSetReminder(notifyMinutes)
                                isConfiguringNotification = false
                            }
                        } else {
                            onSetReminder(notifyMinutes)
                            isConfiguringNotification = false
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Set Reminder")
                }
            }
        }
    } else {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    isConfiguringNotification = true
                }
                false
            }
        )

        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                val targetColor = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> if (isDarkMode) Color(0xFF2E7D32) else Color(0xFF4CAF50)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val color by animateColorAsState(targetColor, label = "swipeColor")
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp)
                        .background(color, RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = "Set Reminder",
                        tint = Color.White
                    )
                }
            },
            content = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClick() },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isReminderSet) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (isReminderSet) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color.Red,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = arrival.lineName,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = arrival.destinationName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isReminderSet) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (arrival.timeToStation < 60) "Due" else "${arrival.timeToStation / 60} min",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (arrival.timeToStation < 60) Color.Red else if (isReminderSet) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isReminderSet) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = "Reminder Set",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 4.dp).size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

fun scheduleReminder(context: android.content.Context, notifyMinutes: Int, arrival: TflArrival) {
    val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
    val intent = android.content.Intent(context, ReminderReceiver::class.java).apply {
        putExtra("busLine", arrival.lineName)
        putExtra("destination", arrival.destinationName)
    }
    
    val pendingIntent = android.app.PendingIntent.getBroadcast(
        context,
        arrival.id.hashCode(),
        intent,
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
    )
    
    val timeToStationMillis = arrival.timeToStation * 1000L
    val notifyMillis = notifyMinutes * 60 * 1000L
    val delayMillis = maxOf(0L, timeToStationMillis - notifyMillis)
    val triggerTime = System.currentTimeMillis() + delayMillis
    
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.setWindow(android.app.AlarmManager.RTC_WAKEUP, triggerTime, 60000L, pendingIntent)
        }
    } else {
        alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
    }
    
    android.widget.Toast.makeText(context, "Reminder set for ${arrival.lineName}", android.widget.Toast.LENGTH_SHORT).show()
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ZenLineTheme {
        MainContent()
    }
}

@Composable
fun TflLogoIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        val ringRadius = width * 0.35f
        val strokeWidth = width * 0.12f
        drawCircle(
            color = color,
            radius = ringRadius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
        
        val barWidth = width * 0.95f
        val barHeight = height * 0.15f
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(
                x = (width - barWidth) / 2f,
                y = (height - barHeight) / 2f
            ),
            size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
        )
    }
}