package com.example.pawsomepals.ui.screens

import android.Manifest
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pawsomepals.data.model.Dog
import com.example.pawsomepals.ui.components.common.LoadingScreen
import com.example.pawsomepals.ui.components.swiping.*
import com.example.pawsomepals.ui.components.location.*
import com.example.pawsomepals.viewmodel.*
import com.example.pawsomepals.utils.PermissionHelper

@Composable
fun SwipingScreen(
    viewModel: SwipingViewModel,
    dogProfileViewModel: DogProfileViewModel,
    locationPermissionViewModel: LocationPermissionViewModel,
    onSchedulePlaydate: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) {
        context as? FragmentActivity
            ?: throw IllegalStateException("SwipingScreen must be used within a FragmentActivity")
    }

    // State collection
    val currentProfile by viewModel.currentProfile.collectAsStateWithLifecycle()
    val matches by viewModel.matches.collectAsStateWithLifecycle()
    val compatibilityState by dogProfileViewModel.compatibilityState.collectAsStateWithLifecycle()
    val currentMatchDetail by viewModel.currentMatchDetail.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionState by locationPermissionViewModel.locationPermissionState.collectAsStateWithLifecycle()

    // Permission handling
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        when {
            isGranted -> locationPermissionViewModel.checkLocationPermission(activity)
            !PermissionHelper.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> locationPermissionViewModel.updatePermissionState(
                LocationPermissionViewModel.LocationPermissionState.PermanentlyDenied
            )
            else -> locationPermissionViewModel.updatePermissionState(
                LocationPermissionViewModel.LocationPermissionState.Denied
            )
        }
    }

    // Effect to check location permission on launch
    LaunchedEffect(Unit) {
        locationPermissionViewModel.checkLocationPermission(activity)
    }

    SwipingScreenContent(
        permissionState = permissionState,
        currentProfile = currentProfile,
        matches = matches,
        compatibilityState = compatibilityState,
        currentMatchDetail = currentMatchDetail,
        uiState = uiState,
        onRequestPermission = {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        onOpenSettings = { PermissionHelper.openAppSettings(activity) },
        onSwipeWithCompatibility = viewModel::onSwipeWithCompatibility,
        onDismissMatch = viewModel::dismissMatch,
        onSchedulePlaydate = onSchedulePlaydate
    )
}

@Composable
private fun SwipingScreenContent(
    permissionState: LocationPermissionViewModel.LocationPermissionState,
    currentProfile: Dog?,
    matches: List<Dog>,
    compatibilityState: DogProfileViewModel.CompatibilityState,
    currentMatchDetail: SwipingViewModel.MatchDetail?,
    uiState: SwipingViewModel.SwipingUIState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwipeWithCompatibility: (String, Boolean) -> Unit,
    onDismissMatch: () -> Unit,
    onSchedulePlaydate: (String) -> Unit
) {
    when (permissionState) {
        LocationPermissionViewModel.LocationPermissionState.Initial -> {
            LoadingScreen()
        }
        LocationPermissionViewModel.LocationPermissionState.Denied,
        LocationPermissionViewModel.LocationPermissionState.RequiresRationale -> {
            LocationPermissionRequest(onRequestPermission = onRequestPermission)
        }
        LocationPermissionViewModel.LocationPermissionState.PermanentlyDenied -> {
            LocationSettingsRequest(onOpenSettings = onOpenSettings)
        }
        LocationPermissionViewModel.LocationPermissionState.Granted -> {
            MainSwipingContent(
                currentProfile = currentProfile,
                matches = matches,
                compatibilityState = compatibilityState,
                currentMatchDetail = currentMatchDetail,
                uiState = uiState,
                onSwipeWithCompatibility = onSwipeWithCompatibility,
                onDismissMatch = onDismissMatch,
                onSchedulePlaydate = onSchedulePlaydate
            )
        }
    }
}