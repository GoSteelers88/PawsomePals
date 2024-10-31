package com.example.pawsomepals.viewmodel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import androidx.fragment.app.FragmentActivity

@HiltViewModel
class LocationPermissionViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _locationPermissionState = MutableStateFlow<LocationPermissionState>(LocationPermissionState.Initial)
    val locationPermissionState: StateFlow<LocationPermissionState> = _locationPermissionState.asStateFlow()

    fun updatePermissionState(state: LocationPermissionState) {
        _locationPermissionState.value = state
    }

    sealed class LocationPermissionState {
        object Initial : LocationPermissionState()
        object Granted : LocationPermissionState()
        object Denied : LocationPermissionState()
        object PermanentlyDenied : LocationPermissionState()
        object RequiresRationale : LocationPermissionState()
    }

    fun checkLocationPermission(activity: FragmentActivity) {
        when {
            checkPermissionGranted() -> {
                _locationPermissionState.value = LocationPermissionState.Granted
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                _locationPermissionState.value = LocationPermissionState.RequiresRationale
            }
            else -> {
                _locationPermissionState.value = LocationPermissionState.Denied
            }
        }
    }

    private fun checkPermissionGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun shouldShowRationale(activity: FragmentActivity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun isPermissionPermanentlyDenied(activity: FragmentActivity): Boolean {
        return !checkPermissionGranted() && !shouldShowRationale(activity)
    }
}

object PermissionHelper {
    fun shouldShowRequestPermissionRationale(context: Context, permission: String): Boolean {
        return if (context is FragmentActivity) {
            ActivityCompat.shouldShowRequestPermissionRationale(context, permission)
        } else {
            false
        }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }
}

@Composable
fun LocationPermissionHandler(
    onPermissionGranted: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        } else {
            if (!PermissionHelper.shouldShowRequestPermissionRationale(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                showSettingsDialog = true
            } else {
                showPermissionDialog = true
            }
        }
    }

    LaunchedEffect(Unit) {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                onPermissionGranted()
            }
            PermissionHelper.shouldShowRequestPermissionRationale(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                showPermissionDialog = true
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Location Permission Required") },
            text = {
                Text(
                    "PawsomePals needs access to your location to find nearby playdate matches for your dog. " +
                            "This helps ensure you're matched with dogs in your area."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPermissionDialog = false
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Not Now")
                }
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Location Required") },
            text = {
                Text(
                    "Location access has been permanently denied. Please enable it in settings to find nearby matches."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsDialog = false
                        PermissionHelper.openAppSettings(context)
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

    content()
}