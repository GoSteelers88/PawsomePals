package com.example.pawsomepals.data.repository

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAIRepository @Inject constructor(private val openAI: OpenAI) {

    @OptIn(BetaOpenAI::class)
    suspend fun getResponse(prompt: String): String {
        return withContext(Dispatchers.IO) {
            val chatCompletionRequest = ChatCompletionRequest(
                model = ModelId("gpt-3.5-turbo"),
                messages = listOf(
                    ChatMessage(
                        role = ChatRole.User,
                        content = prompt
                    )
                )
            )
            val completion: ChatCompletion = openAI.chatCompletion(chatCompletionRequest)
            completion.choices.first().message?.content ?: "No response received"
        }
    }
}