package io.pawsomepals.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pawsomepals.app.data.model.Notification
import io.pawsomepals.app.data.repository.NotificationRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val repository: NotificationRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                loadNotifications()
                updateUnreadCount()
            } else {
                _notifications.value = emptyList()
                _unreadCount.value = 0
            }
        }
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _notifications.value = repository.getNotifications()
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun updateUnreadCount() {
        viewModelScope.launch {
            try {
                _unreadCount.value = repository.getUnreadNotificationsCount()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            try {
                repository.markAsRead(notificationId)
                loadNotifications()
                updateUnreadCount()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            try {
                repository.deleteNotification(notificationId)
                loadNotifications()
                updateUnreadCount()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}