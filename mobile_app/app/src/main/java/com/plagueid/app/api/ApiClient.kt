package com.plagueid.app.api

import android.content.Context
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private const val BASE_URL = "http://10.0.2.2:8000/"

    private val gson by lazy {
        GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            .setLenient()
            .create()
    }

    private fun createClient(context: Context): OkHttpClient {
        val appContext = context.applicationContext
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(AuthInterceptor(appContext))
            .build()
    }

    private fun createRetrofit(context: Context): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(createClient(context))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    fun getApi(context: Context): PlagueApi {
        return createRetrofit(context).create(PlagueApi::class.java)
    }
}
