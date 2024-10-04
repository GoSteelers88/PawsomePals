package com.example.pawsomepals.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.model.Chat
import com.example.pawsomepals.data.model.Message
import com.example.pawsomepals.data.repository.ChatRepository
import com.example.pawsomepals.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel

class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _isUserLoggedIn = MutableStateFlow(false)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn

    init {
        checkUserLoginStatus()
    }

    private fun checkUserLoginStatus() {
        viewModelScope.launch {
            _isUserLoggedIn.value = userRepository.getCurrentUser() != null
            if (_isUserLoggedIn.value) {
                loadChats()
            }
        }
    }

    private fun loadChats() {
        viewModelScope.launch {
            val userId = userRepository.getCurrentUserId() ?: return@launch
            chatRepository.getChats(userId).collect {
                _chats.value = it
            }
        }
    }

    fun loadMessages(chatId: String) {
        viewModelScope.launch {
            chatRepository.getMessages(chatId).collect {
                _messages.value = it
            }
        }
    }

    fun sendMessage(chatId: String, content: String) {
        viewModelScope.launch {
            val senderId = userRepository.getCurrentUserId() ?: return@launch
            chatRepository.sendMessage(chatId, senderId, content)
        }
    }

    fun getCurrentUserId(): String? {
        return try {
            userRepository.getCurrentUserId()
        } catch (e: IllegalStateException) {
            null
        }}}


