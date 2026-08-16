package com.simats.growise.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// 1. Expanded Data Models for Open-Meteo Detailed Response
data class WeatherResponse(
    val current_weather: CurrentWeather?,
    val hourly: HourlyData?,
    val daily: DailyData?
)

data class CurrentWeather(val temperature: Double, val weathercode: Int, val windspeed: Double? = 0.0)
// FIX: Added precipitation and windspeed_10m to dynamically catch the array payloads
data class HourlyData(val time: List<String>, val temperature_2m: List<Double>, val relativehumidity_2m: List<Int>?, val precipitation: List<Double>?, val windspeed_10m: List<Double>?)
data class DailyData(val time: List<String>, val weathercode: List<Int>, val temperature_2m_max: List<Double>, val temperature_2m_min: List<Double>)

// 2. API Interface
interface WeatherApiService {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current_weather") currentWeather: Boolean = true
    ): WeatherResponse

    // New Endpoint for the Detailed Screen
    // New Endpoint for the Detailed Screen
    @GET("v1/forecast")
    suspend fun getFullWeather(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current_weather") currentWeather: Boolean = true,
        // FIX: Added precipitation and windspeed_10m to the API fetch query
        @Query("hourly") hourly: String = "temperature_2m,relativehumidity_2m,precipitation,windspeed_10m",
        @Query("daily") daily: String = "weathercode,temperature_2m_max,temperature_2m_min",
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponse
}

// 3. Independent Retrofit Client for Open-Meteo
object WeatherRetrofitClient {
    private const val WEATHER_BASE_URL = "https://api.open-meteo.com/"

    val apiService: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl(WEATHER_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }
}