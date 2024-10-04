package com.example.pawsomepals.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.ai.AIFeatures
import com.example.pawsomepals.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrainerTipsViewModel @Inject constructor(
    private val aiFeatures: AIFeatures,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _dailyTip = MutableStateFlow("")
    val dailyTip: StateFlow<String> = _dailyTip

    private val _trainerAdvice = MutableStateFlow("")
    val trainerAdvice: StateFlow<String> = _trainerAdvice

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadDailyTip() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val dogProfile = userRepository.getCurrentUserDog()
                    ?: throw IllegalStateException("No dog profile found for current user")

                val tip = aiFeatures.getDailyTrainingTip(dogProfile)
                _dailyTip.value = tip
            } catch (e: Exception) {
                _dailyTip.value = "Error loading daily tip: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }




    fun askTrainerQuestion(question: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val userId = userRepository.getCurrentUserId() ?: throw IllegalStateException("No user logged in")
                val dogProfile = userRepository.getCurrentUserDog()
                    ?: throw IllegalStateException("No dog profile found for current user")

                val advice = aiFeatures.getTrainerAdvice(userId, question, dogProfile)
                _trainerAdvice.value = advice ?: "Unable to get trainer advice at this time."
            } catch (e: Exception) {
                _trainerAdvice.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }}