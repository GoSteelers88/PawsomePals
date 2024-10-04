package com.example.pawsomepals.data.remote

import android.net.Uri
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface PhotoApi {
    @Multipart
    @POST("photos/upload")
    suspend fun uploadPhoto(
        @Part photo: Uri,
        @Part("isUserPhoto") isUserPhoto: Boolean
    ): String // Returns the URL of the uploaded photo

    @DELETE("photos/{photoUrl}")
    suspend fun deletePhoto(@Path("photoUrl") photoUrl: String)

    @GET("photos")
    suspend fun getPhotos(@Query("isUserPhoto") isUserPhoto: Boolean): List<String>

    @GET("photos/{userId}")
    suspend fun getUserPhotos(@Path("userId") userId: String): List<String>

    @GET("photos/dogs/{dogId}")
    suspend fun getDogPhotos(@Path("dogId") dogId: String): List<String>
}