package io.pawsomepals.app.utils

import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse
import com.google.android.libraries.places.api.net.PlacesClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun PlacesClient.findCurrentPlace(request: FindCurrentPlaceRequest): FindCurrentPlaceResponse =
    suspendCoroutine { continuation ->
        findCurrentPlace(request)
            .addOnSuccessListener { response ->
                continuation.resume(response)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }