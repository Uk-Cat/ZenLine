package com.ukcat.zenline

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

sealed class BusStopUiState {
    object Idle : BusStopUiState()
    object Loading : BusStopUiState()
    data class Success(val stops: List<TflStopPoint>) : BusStopUiState()
    data class Error(val message: String) : BusStopUiState()
}

sealed class ArrivalsUiState {
    object Idle : ArrivalsUiState()
    object Loading : ArrivalsUiState()
    data class Success(val arrivals: List<TflArrival>) : ArrivalsUiState()
    data class Error(val message: String) : ArrivalsUiState()
}

sealed class RouteUiState {
    object Idle : RouteUiState()
    object Loading : RouteUiState()
    data class Success(val route: TflRouteSequence) : RouteUiState()
    data class Error(val message: String) : RouteUiState()
}

class BusStopViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("zenline_prefs", Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow<BusStopUiState>(BusStopUiState.Idle)
    val uiState: StateFlow<BusStopUiState> = _uiState

    private val _arrivalsState = MutableStateFlow<ArrivalsUiState>(ArrivalsUiState.Idle)
    val arrivalsState: StateFlow<ArrivalsUiState> = _arrivalsState

    private val _routeState = MutableStateFlow<RouteUiState>(RouteUiState.Idle)
    val routeState: StateFlow<RouteUiState> = _routeState

    private val _favorites = MutableStateFlow<Set<String>>(
        prefs.getStringSet("favorites", emptySet()) ?: emptySet()
    )
    val favorites: StateFlow<Set<String>> = _favorites

    private val _activeReminders = MutableStateFlow<Set<String>>(emptySet())
    val activeReminders: StateFlow<Set<String>> = _activeReminders

    fun addReminder(vehicleId: String) {
        _activeReminders.value = _activeReminders.value + vehicleId
    }

    fun removeReminder(vehicleId: String) {
        _activeReminders.value = _activeReminders.value - vehicleId
    }

    // Settings States
    private val _searchRadius = MutableStateFlow(prefs.getInt("search_radius", 1000))
    val searchRadius: StateFlow<Int> = _searchRadius

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val _accentColor = MutableStateFlow(prefs.getString("accent_color", "#2196F3") ?: "#2196F3")
    val accentColor: StateFlow<String> = _accentColor

    private val _defaultNotificationMinutes = MutableStateFlow(prefs.getInt("default_notification_minutes", 1))
    val defaultNotificationMinutes: StateFlow<Int> = _defaultNotificationMinutes

    private val _autoRefreshIntervalSeconds = MutableStateFlow(prefs.getInt("auto_refresh_interval_seconds", 30))
    val autoRefreshIntervalSeconds: StateFlow<Int> = _autoRefreshIntervalSeconds

    private val _distanceUnit = MutableStateFlow(prefs.getString("distance_unit", "metric") ?: "metric")
    val distanceUnit: StateFlow<String> = _distanceUnit

    private val _maxStops = MutableStateFlow(prefs.getInt("max_stops", 10))
    val maxStops: StateFlow<Int> = _maxStops

    fun updateSearchRadius(radius: Int) {
        _searchRadius.value = radius
        prefs.edit().putInt("search_radius", radius).apply()
        if (lastLat != 0.0 && lastLon != 0.0) {
            fetchNearbyBusStops(lastLat, lastLon)
        }
    }

    fun updateDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        prefs.edit().putBoolean("dark_mode", enabled).apply()
    }

    fun updateAccentColor(colorHex: String) {
        _accentColor.value = colorHex
        prefs.edit().putString("accent_color", colorHex).apply()
    }

    fun updateDefaultNotificationMinutes(minutes: Int) {
        _defaultNotificationMinutes.value = minutes
        prefs.edit().putInt("default_notification_minutes", minutes).apply()
    }

    fun updateAutoRefreshIntervalSeconds(seconds: Int) {
        _autoRefreshIntervalSeconds.value = seconds
        prefs.edit().putInt("auto_refresh_interval_seconds", seconds).apply()
    }

    fun updateDistanceUnit(unit: String) {
        _distanceUnit.value = unit
        prefs.edit().putString("distance_unit", unit).apply()
    }

    fun updateMaxStops(max: Int) {
        _maxStops.value = max
        prefs.edit().putInt("max_stops", max).apply()
        val currentState = _uiState.value
        if (currentState is BusStopUiState.Success) {
            _uiState.value = BusStopUiState.Success(sortStops(currentState.stops))
        }
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.tfl.gov.uk/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(TflApiService::class.java)

    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0

    fun fetchNearbyBusStops(lat: Double, lon: Double) {
        lastLat = lat
        lastLon = lon
        viewModelScope.launch {
            _uiState.value = BusStopUiState.Loading
            try {
                val response = apiService.getNearbyStopPoints(lat, lon, radius = _searchRadius.value)
                val sortedStops = sortStops(response.stopPoints)
                _uiState.value = BusStopUiState.Success(sortedStops)
            } catch (e: Exception) {
                _uiState.value = BusStopUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun sortStops(stops: List<TflStopPoint>): List<TflStopPoint> {
        val favs = _favorites.value
        val sorted = stops.sortedWith(
            compareByDescending<TflStopPoint> { favs.contains(it.naptanId) }
                .thenBy { it.distance }
        )
        return if (_maxStops.value > 0) {
            sorted.take(_maxStops.value)
        } else {
            sorted
        }
    }

    fun toggleFavorite(naptanId: String) {
        val currentFavs = _favorites.value.toMutableSet()
        if (currentFavs.contains(naptanId)) {
            currentFavs.remove(naptanId)
        } else {
            currentFavs.add(naptanId)
        }
        _favorites.value = currentFavs
        prefs.edit().putStringSet("favorites", currentFavs).apply()
        
        // Re-sort current results if we are in Success state
        val currentState = _uiState.value
        if (currentState is BusStopUiState.Success) {
            _uiState.value = BusStopUiState.Success(sortStops(currentState.stops))
        }
    }

    fun fetchArrivals(naptanId: String) {
        viewModelScope.launch {
            _arrivalsState.value = ArrivalsUiState.Loading
            try {
                val response = apiService.getArrivals(naptanId)
                val sortedArrivals = response.sortedBy { it.timeToStation }
                _arrivalsState.value = ArrivalsUiState.Success(sortedArrivals)
            } catch (e: Exception) {
                _arrivalsState.value = ArrivalsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun clearArrivals() {
        _arrivalsState.value = ArrivalsUiState.Idle
    }

    fun fetchRouteSequence(lineId: String, direction: String) {
        viewModelScope.launch {
            _routeState.value = RouteUiState.Loading
            try {
                val response = apiService.getRouteSequence(lineId, direction)
                _routeState.value = RouteUiState.Success(response)
            } catch (e: Exception) {
                _routeState.value = RouteUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun clearRoute() {
        _routeState.value = RouteUiState.Idle
    }
}
