package com.thewalkersoft.linkedin_job_tracker.client

import android.util.Log
import com.google.gson.GsonBuilder
import com.thewalkersoft.linkedin_job_tracker.service.GoogleSheetApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val DEPLOYMENT_ID =
    "AKfycbzUE5aItxZ6LAgb9KaEp7EAxpHqsKMucs2CLWVp7eM6u9Imz8s_0PVns6BlD8_jf1PE-A"

object RetrofitClient {
    private const val BASE_URL = "https://script.google.com/macros/s/$DEPLOYMENT_ID/"

    val instance: GoogleSheetApiService by lazy {
        // Create Gson with lenient mode to handle edge cases
        val gson = GsonBuilder()
            .setLenient()
            .create()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson)) // Use lenient Gson
            .build()
            .create(GoogleSheetApiService::class.java)
    }

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("HTTP", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

}