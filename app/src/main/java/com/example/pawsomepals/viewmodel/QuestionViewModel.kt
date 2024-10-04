package com.example.pawsomepals.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.model.QuestionnaireResponse
import com.example.pawsomepals.data.repository.QuestionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class QuestionnaireViewModel @Inject constructor(
    private val questionRepository: QuestionRepository
) : ViewModel() {

    fun saveQuestionnaireResponses(userId: String, responses: Map<String, String>) {
        viewModelScope.launch {
            val questionnaireResponse = QuestionnaireResponse(userId, responses)
            questionRepository.saveQuestionnaireResponse(questionnaireResponse)
        }
    }

    suspend fun getQuestionnaireResponses(userId: String): Map<String, String>? {
        return questionRepository.getQuestionnaireResponse(userId)?.responses
    }
}
