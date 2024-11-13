package io.pawsomepals.app.ui.screens.location

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import io.pawsomepals.app.viewmodel.EnhancedPlaydateViewModel
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
    val locationState: EnhancedPlaydateViewModel.LocationState by viewModel.locationState.collectAsState()
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
            Column {
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

                if (locationState is EnhancedPlaydateViewModel.LocationState.AutocompleteResults && searchQuery.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        items((locationState as? EnhancedPlaydateViewModel.LocationState.AutocompleteResults)?.predictions ?: emptyList()) { prediction ->
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
        }

        // Map and Results Container
        Box(modifier = Modifier.weight(1f)) {
            // Google Maps
            AndroidView(
                factory = { mapViewState },
                modifier = Modifier.fillMaxSize()
            ) { mapView ->
                mapView.getMapAsync { googleMap ->
                    setupGoogleMap(googleMap, locationState, viewModel)
                }
            }

            // Results Sheet
            if (locationState is EnhancedPlaydateViewModel.LocationState.PlacesFound) {
                LocationResultsBottomSheet(
                    places = (locationState as? EnhancedPlaydateViewModel.LocationState.PlacesFound)?.places ?: emptyList(),
                    onPlaceSelected = { place ->
                        viewModel.selectPlace(place)
                        onLocationSelected(place.toDogFriendlyLocation())
                    }
                )
            }

            // Loading and Error States
            when (locationState) {
                is EnhancedPlaydateViewModel.LocationState.Loading -> LoadingIndicator()
                is EnhancedPlaydateViewModel.LocationState.Error -> {
                    ErrorView(
                        message = (locationState as EnhancedPlaydateViewModel.LocationState.Error).message,
                        onRetry = viewModel::retry
                    )
                }
                else -> Unit
            }
        }
    }
}

// Location Results Components
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

// Helper Functions and Extensions
private fun setupGoogleMap(
    googleMap: GoogleMap,
    locationState: EnhancedPlaydateViewModel.LocationState,
    viewModel: LocationPickerViewModel
) {
    googleMap.apply {
        uiSettings.apply {
            isZoomControlsEnabled = true
            isMyLocationButtonEnabled = true
            isMapToolbarEnabled = true
        }

        clear()

        when (locationState) {
            is EnhancedPlaydateViewModel.LocationState.PlacesFound -> {
                locationState.places.forEach { place ->
                    addMarker(
                        MarkerOptions()
                            .position(place.latLng)
                            .title(place.name)
                            .snippet(place.address)
                    )
                }

                val bounds = LatLngBounds.builder().apply {
                    locationState.places.forEach { include(it.latLng) }
                }.build()
                animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
            }
            else -> Unit
        }

        setOnMarkerClickListener { marker ->
            (locationState as? EnhancedPlaydateViewModel.LocationState.PlacesFound)?.places
                ?.find { it.latLng == marker.position }
                ?.let { place ->
                    viewModel.selectPlace(place)
                    true
                } ?: false
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