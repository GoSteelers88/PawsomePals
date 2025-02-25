package io.pawsomepals.app.ui.screens.location

import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import io.pawsomepals.app.R
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.data.model.LocationState
import io.pawsomepals.app.viewmodel.LocationPickerViewModel


private fun Place.toDogFriendlyLocation(): DogFriendlyLocation {
    return DogFriendlyLocation(
        placeId = id ?: "",
        name = name ?: "",
        address = address ?: "",
        latitude = latLng?.latitude ?: 0.0,
        longitude = latLng?.longitude ?: 0.0,
        placeTypes = types?.map { it.name } ?: emptyList(),
        rating = rating,
        userRatingsTotal = userRatingsTotal,
        phoneNumber = phoneNumber,
        websiteUri = websiteUri?.toString()
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    onLocationSelected: (DogFriendlyLocation) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocationPickerViewModel = hiltViewModel()
) {
    val locationState by viewModel.locationState.collectAsState()
    val mapViewState = rememberMapViewWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        // Search Bar with Autocomplete
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .zIndex(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchLocations(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search for dog parks, cafes, etc.") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                singleLine = true
            )
        }

        // Map and Results Container
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { mapViewState },
                modifier = Modifier.fillMaxSize()
            ) { mapView ->
                mapView.getMapAsync { googleMap ->
                    setupGoogleMap(googleMap, locationState)
                }
            }

            when (locationState) {
                is LocationState.Initial -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Initializing map...")
                    }
                }
                is LocationState.Loading -> {
                    LoadingIndicator()
                }
                is LocationState.Error -> {
                    ErrorView(
                        message = (locationState as LocationState.Error).message,
                        onRetry = viewModel::retry
                    )
                }
                is LocationState.SearchResults -> {
                    val searchState = locationState as LocationState.SearchResults
                    if (searchState.predictions.isNotEmpty() && searchQuery.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 200.dp)
                            ) {
                                items(searchState.predictions) { prediction ->
                                    AutocompleteSuggestionItem(
                                        prediction = prediction,
                                        onClick = {
                                            viewModel.getPlaceDetails(prediction.placeId)
                                            searchQuery = ""
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (searchState.places.isNotEmpty()) {
                        PlacesBottomSheet(
                            places = searchState.places,
                            onPlaceSelected = { place ->
                                viewModel.selectPlace(place)
                                onLocationSelected(place.toDogFriendlyLocation())
                            }
                        )
                    }
                }
                is LocationState.Success -> {
                    val successState = locationState as LocationState.Success
                    LocationsBottomSheet(
                        locations = successState.nearbyLocations + successState.recommendedLocations,
                        optimalLocation = successState.optimalLocation,
                        onLocationSelected = onLocationSelected
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationsBottomSheet(
    locations: List<DogFriendlyLocation>,
    optimalLocation: DogFriendlyLocation?,
    onLocationSelected: (DogFriendlyLocation) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = { },
        sheetState = rememberModalBottomSheetState(),
        modifier = Modifier.fillMaxHeight(0.5f)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            optimalLocation?.let { location ->
                item {
                    Text(
                        "Optimal Location",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    LocationItem(
                        location = location,
                        onClick = { onLocationSelected(location) },
                        isOptimal = true
                    )
                }
            }

            items(locations) { location ->
                LocationItem(
                    location = location,
                    onClick = { onLocationSelected(location) }
                )
            }
        }
    }
}
@Composable
private fun LocationItem(
    location: DogFriendlyLocation,
    onClick: () -> Unit,
    isOptimal: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isOptimal) {
                Text(
                    text = "✨ Optimal Location",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = location.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = location.address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationResultsBottomSheet(
    places: List<Place>,
    onPlaceSelected: (Place) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = { },
        sheetState = rememberModalBottomSheetState(),
        modifier = Modifier.fillMaxHeight(0.5f)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(places) { place ->
                PlaceItem(place = place, onClick = { onPlaceSelected(place) })
            }
        }
    }
}
@Composable
private fun AutocompleteSuggestionItem(
    prediction: AutocompletePrediction,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = prediction.getPrimaryText(null).toString(),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = prediction.getSecondaryText(null).toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}


@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = message)
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun PlaceItem(place: Place, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = place.name ?: "",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = place.address ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            place.rating?.let { rating ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "★ $rating",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "(${place.userRatingsTotal ?: 0})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Replace the existing setupGoogleMap function
private fun setupGoogleMap(
    googleMap: GoogleMap,
    locationState: LocationState
) {
    googleMap.apply {
        uiSettings.apply {
            isZoomControlsEnabled = true
            isMyLocationButtonEnabled = true
            isMapToolbarEnabled = true
        }

        clear()

        when (locationState) {
            is LocationState.SearchResults -> {
                locationState.places.forEach { place ->
                    place.latLng?.let { latLng ->
                        addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .title(place.name)
                                .snippet(place.address)
                        )
                    }
                }

                if (locationState.places.isNotEmpty()) {
                    val bounds = LatLngBounds.builder().apply {
                        locationState.places.forEach { place ->
                            place.latLng?.let { include(it) }
                        }
                    }.build()
                    animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                }
            }
            is LocationState.Success -> {
                val allLocations = locationState.nearbyLocations +
                        locationState.recommendedLocations +
                        listOfNotNull(locationState.optimalLocation)

                allLocations.forEach { location ->
                    val latLng = com.google.android.gms.maps.model.LatLng(
                        location.latitude,
                        location.longitude
                    )
                    addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title(location.name)
                            .snippet(location.address)
                    )
                }

                if (allLocations.isNotEmpty()) {
                    val bounds = LatLngBounds.builder().apply {
                        allLocations.forEach { location ->
                            include(com.google.android.gms.maps.model.LatLng(
                                location.latitude,
                                location.longitude
                            ))
                        }
                    }.build()
                    animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                }
            }
            else -> Unit // Handle Initial, Loading, and Error states
        }

        setOnMarkerClickListener { marker ->
            when (locationState) {
                is LocationState.SearchResults -> {
                    locationState.places.find { it.latLng == marker.position }?.let { place ->
                        // Handle place selection
                        true
                    } ?: false
                }
                is LocationState.Success -> {
                    val allLocations = locationState.nearbyLocations +
                            locationState.recommendedLocations +
                            listOfNotNull(locationState.optimalLocation)
                    allLocations.find {
                        com.google.android.gms.maps.model.LatLng(
                            it.latitude,
                            it.longitude
                        ) == marker.position
                    }?.let {
                        // Handle location selection
                        true
                    } ?: false
                }
                else -> false
            }
        }
    }
}
@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            id = R.id.map
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = getMapLifecycleObserver(mapView)
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    return mapView
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacesBottomSheet(
    places: List<Place>,
    onPlaceSelected: (Place) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = { },
        sheetState = rememberModalBottomSheetState(),
        modifier = Modifier.fillMaxHeight(0.5f)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(places) { place ->
                PlaceItem(place = place, onClick = { onPlaceSelected(place) })
            }
        }
    }
}

private fun getMapLifecycleObserver(mapView: MapView) = object : DefaultLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {
        mapView.onCreate(Bundle())
    }

    override fun onStart(owner: LifecycleOwner) {
        mapView.onStart()
    }

    override fun onResume(owner: LifecycleOwner) {
        mapView.onResume()
    }

    override fun onPause(owner: LifecycleOwner) {
        mapView.onPause()
    }

    override fun onStop(owner: LifecycleOwner) {
        mapView.onStop()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        mapView.onDestroy()
    }
}