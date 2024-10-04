package com.example.pawsomepals.data.remote

import com.example.pawsomepals.data.model.Settings
import retrofit2.http.*

interface SettingsApi {
    @GET("settings")
    suspend fun fetchSettings(): Settings

    @PUT("settings")
    suspend fun updateSettings(@Body settings: Settings): Settings

    @PATCH("settings")
    suspend fun patchSettings(@Body partialSettings: Map<String, Any>): Settings

    @DELETE("settings")
    suspend fun resetSettings()
}