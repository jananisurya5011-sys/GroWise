package com.simats.growise.data.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // Default dynamic target IP binding for local deployment environment
    // FIX: Removed 'private' so it can be accessed dynamically by FProfile.kt
    const val BASE_URL = "http://10.162.43.73:5000/"

    // FIX: Added OkHttpClient to bypass the default 10-second timeout restriction
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val apiService: GroWiseApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) // FIX: Attached custom client
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroWiseApiService::class.java)
    }
}