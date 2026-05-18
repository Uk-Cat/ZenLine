package com.ukcat.zenline

import retrofit2.http.GET
import retrofit2.http.Query
import com.google.gson.annotations.SerializedName

data class TflStopPointResponse(
    val stopPoints: List<TflStopPoint>
)

data class TflStopPoint(
    @SerializedName(value = "naptanId", alternate = ["id"])
    val naptanId: String,
    
    @SerializedName(value = "commonName", alternate = ["name"])
    val commonName: String,
    
    val indicator: String? = null,
    val distance: Double = 0.0,
    val lat: Double,
    val lon: Double,
    val stopLetter: String? = null
)

data class TflArrival(
    val id: String,
    val lineId: String,
    val lineName: String,
    val direction: String,
    val destinationName: String,
    val timeToStation: Int,
    val expectedArrival: String
)

data class TflRouteSequence(
    val lineId: String,
    val lineName: String,
    val direction: String,
    val stopPointSequences: List<TflStopPointSequence>,
    val lineStrings: List<String>
)

data class TflStopPointSequence(
    val stopPoint: List<TflStopPoint>
)

interface TflApiService {
    @GET("StopPoint")
    suspend fun getNearbyStopPoints(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("stopTypes") stopTypes: String = "NaptanPublicBusCoachTram",
        @Query("radius") radius: Int,
        @Query("app_key") appKey: String = "aa063bac459d4c25b154f84c55ada07d"
    ): TflStopPointResponse

    @GET("StopPoint/{id}/Arrivals")
    suspend fun getArrivals(
        @retrofit2.http.Path("id") id: String,
        @Query("app_key") appKey: String = "aa063bac459d4c25b154f84c55ada07d"
    ): List<TflArrival>

    @GET("Line/{id}/Route/Sequence/{direction}")
    suspend fun getRouteSequence(
        @retrofit2.http.Path("id") id: String,
        @retrofit2.http.Path("direction") direction: String,
        @Query("app_key") appKey: String = "aa063bac459d4c25b154f84c55ada07d"
    ): TflRouteSequence
}
