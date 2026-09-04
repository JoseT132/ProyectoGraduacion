package com.plagueid.app

import com.google.gson.annotations.SerializedName

data class Species(
    val id: Int,
    val slug: String,
    @SerializedName("scientific_name") val scientificName: String,
    @SerializedName("common_name") val commonName: String?,
    val family: String?,
    val description: String?,
    @SerializedName("life_cycle") val lifeCycle: String?,
    val hosts: String?,
    val damage: String?,
    @SerializedName("biological_control") val biologicalControl: String?,
    @SerializedName("cultural_control") val culturalControl: String?,
    @SerializedName("chemical_control") val chemicalControl: String?,
    val threshold: String?,
    val references: String?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("created_at") val createdAt: String?
)
