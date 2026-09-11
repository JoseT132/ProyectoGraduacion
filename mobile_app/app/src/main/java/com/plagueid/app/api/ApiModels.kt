package com.plagueid.app.api

import com.google.gson.annotations.SerializedName
import com.plagueid.app.Species

data class RegisterRequest(
    val first_name: String,
    val last_name: String,
    val email: String,
    val password: String,
    val birth_date: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String
)

data class UserProfile(
    val id: Int,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    val email: String,
    val age: Int,
    @SerializedName("created_at") val createdAt: String? = null
)

data class OAuthRequest(val token: String)

data class DetectionRecord(
    val id: Int,
    val species: Species?,
    val confidence: Float?,
    @SerializedName("top_predictions") val topPredictions: List<PredictedItem>?,
    @SerializedName("image_path") val imagePath: String?,
    @SerializedName("created_at") val createdAt: String?
)

data class PredictResponse(
    val species: String,
    val slug: String,
    val confidence: Float,
    @SerializedName("top_predictions") val topPredictions: List<PredictedItem>,
    val ficha: Species?
)

data class PredictedItem(
    val rank: Int,
    val species: String,
    val slug: String,
    val confidence: Float
)
