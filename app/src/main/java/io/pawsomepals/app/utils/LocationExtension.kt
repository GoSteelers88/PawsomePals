package io.pawsomepals.app.utils

import android.location.Location
import com.google.android.gms.maps.model.LatLng

fun Location.toLatLng(): LatLng = LatLng(latitude, longitude)