package io.pawsomepals.app.data.remote

import io.pawsomepals.app.data.model.Rating
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface RatingApi {
    @POST("ratings")
    suspend fun submitRating(@Body rating: Rating): Rating

    @GET("ratings/user/{userId}")
    suspend fun getUserRatings(@Path("userId") userId: String): List<Rating>

    @GET("ratings/average/{userId}")
    suspend fun getUserAverageRating(@Path("userId") userId: String): Float

    @PUT("ratings/{ratingId}")
    suspend fun updateRating(@Path("ratingId") ratingId: String, @Body rating: Rating): Rating

    @DELETE("ratings/{ratingId}")
    suspend fun deleteRating(@Path("ratingId") ratingId: String)
}