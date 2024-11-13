package io.pawsomepals.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _permissionState = MutableStateFlow<PermissionState>(PermissionState.Initial)
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    sealed class PermissionState {
        object Initial : PermissionState()
        object Granted : PermissionState()
        object Denied : PermissionState()
        object RequestPermission : PermissionState()  // New state
        object PermanentlyDenied : PermissionState()
        data class Error(val message: String) : PermissionState()
    }

    init {
        checkInitialPermission()
    }

    private fun checkInitialPermission() {
        when {
            checkPermission() -> {
                _permissionState.value = PermissionState.Granted
            }
            shouldShowRationale() -> {
                _permissionState.value = PermissionState.RequestPermission
            }
            else -> {
                _permissionState.value = PermissionState.Initial
            }
        }
    }

    fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun shouldShowRationale(): Boolean {
        return PermissionHelper.shouldShowRequestPermissionRationale(
            context,
            Manifest.permission.CAMERA
        )
    }

    fun handlePermissionResult(isGranted: Boolean) {
        viewModelScope.launch {
            _permissionState.value = when {
                isGranted -> PermissionState.Granted
                shouldShowRationale() -> PermissionState.RequestPermission
                else -> PermissionState.PermanentlyDenied
            }
        }
    }

    fun requestPermission() {
        _permissionState.value = PermissionState.RequestPermission
    }

    fun onPermissionDenied() {
        _permissionState.value = PermissionState.Denied
    }

    fun resetPermissionState() {
        _permissionState.value = PermissionState.Initial
    }
}