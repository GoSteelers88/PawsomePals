package com.example.pawsomepals.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.repository.OpenAIRepository
import com.example.pawsomepals.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AIViewModel @Inject constructor(
    private val openAIRepository: OpenAIRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _healthAdvice = MutableStateFlow<String?>(null)
    val healthAdvice: StateFlow<String?> = _healthAdvice

    private val _trainingTip = MutableStateFlow<String?>(null)
    val trainingTip: StateFlow<String?> = _trainingTip

    fun getHealthAdvice(question: String) {
        viewModelScope.launch {
            val dogProfile = userRepository.getCurrentUserDog()
            if (dogProfile != null) {
                val prompt = "As a dog health advisor, please answer this question for a ${dogProfile.breed} dog, aged ${dogProfile.age}: $question"
                _healthAdvice.value = openAIRepository.getResponse(prompt)
            } else {
                _healthAdvice.value = "Error: No dog profile found"
            }
        }
    }

    fun getDailyTrainingTip() {
        viewModelScope.launch {
            val dogProfile = userRepository.getCurrentUserDog()
            if (dogProfile != null) {
                val prompt = "Give a daily training tip for a ${dogProfile.breed} dog, aged ${dogProfile.age}, with energy level ${dogProfile.energyLevel}"
                _trainingTip.value = openAIRepository.getResponse(prompt)
            } else {
                _trainingTip.value = "Error: No dog profile found"
            }
        }
    }

    class Factory(
        private val openAIRepository: OpenAIRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AIViewModel::class.java)) {
                return AIViewModel(openAIRepository, userRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}