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

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<MessageResponse>

    @POST("auth/verify-reset-code")
    suspend fun verifyResetCode(@Body request: VerifyCodeRequest): Response<MessageResponse>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<MessageResponse>

    @GET("auth/me")
    suspend fun getMe(): Response<UserProfile>

    @GET("species/{slug}")
    suspend fun getSpecies(@Path("slug") slug: String): Response<Species>

    @GET("detections")
    suspend fun getDetections(): Response<List<DetectionRecord>>

    @Multipart
    @POST("predict")
    suspend fun predict(
        @Part image: MultipartBody.Part,
        @Query("latitude") latitude: Double? = null,
        @Query("longitude") longitude: Double? = null
    ): Response<PredictResponse>
}
