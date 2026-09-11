package com.plagueid.app.api

import com.plagueid.app.Species
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface PlagueApi {

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<TokenResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>

    @POST("auth/google")
    suspend fun loginGoogle(@Body request: OAuthRequest): Response<TokenResponse>

    @POST("auth/facebook")
    suspend fun loginFacebook(@Body request: OAuthRequest): Response<TokenResponse>

    @GET("auth/me")
    suspend fun getMe(): Response<UserProfile>

    @GET("species/{slug}")
    suspend fun getSpecies(@Path("slug") slug: String): Response<Species>

    @GET("detections")
    suspend fun getDetections(): Response<List<DetectionRecord>>

    @Multipart
    @POST("predict")
    suspend fun predict(@Part image: MultipartBody.Part): Response<PredictResponse>
}
