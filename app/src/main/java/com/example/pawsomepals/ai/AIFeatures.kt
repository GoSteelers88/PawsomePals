package com.example.pawsomepals.ai

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.example.pawsomepals.data.model.DogProfile
import com.example.pawsomepals.data.repository.QuestionRepository
import com.example.pawsomepals.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aallam.openai.api.BetaOpenAI
import com.example.pawsomepals.data.model.Dog
import java.time.LocalDate

@OptIn(BetaOpenAI::class)
class AIFeatures(
    private val openAI: OpenAI,
    private val userRepository: UserRepository,
    private val questionRepository: QuestionRepository
) {
    companion object {
        private const val MAX_FREE_QUESTIONS = 5
        private const val GPT_4_MODEL = "gpt-4"
    }

    suspend fun getHealthAdvice(userId: String, question: String, dogProfile: Dog): String? {
        return withContext(Dispatchers.IO) {
            val user = userRepository.getUserById(userId) ?: return@withContext null

            if (!user.hasSubscription) {
                val dailyQuestionCount = questionRepository.getDailyQuestionCount(userId)
                if (dailyQuestionCount >= MAX_FREE_QUESTIONS) {
                    return@withContext "You've reached your daily limit of $MAX_FREE_QUESTIONS free questions. Please subscribe for unlimited access."
                }
            }

            val systemPrompt = """
                You are a knowledgeable and caring dog health advisor. Your role is to provide helpful, 
                safe, and tailored advice for dog owners. Always consider the specific characteristics 
                of the dog in question, and include a disclaimer that your advice is not a substitute 
                for professional veterinary care.
            """.trimIndent()

            val userPrompt = """
                I have a ${dogProfile.breed} dog, aged ${dogProfile.age}, with an energy level of ${dogProfile.energyLevel}.
                My question is: $question
            """.trimIndent()

            val messages = listOf(
                ChatMessage(role = ChatRole.System, content = systemPrompt),
                ChatMessage(role = ChatRole.User, content = userPrompt)
            )

            val response = openAI.chatCompletion(
                ChatCompletionRequest(
                    model = ModelId(GPT_4_MODEL),
                    messages = messages
                )
            )

            val answer = response.choices.first().message?.content ?: "No response received"

            // Save the question and answer
            questionRepository.saveQuestion(userId, question, answer)

            answer
        }
    }

    suspend fun getUserQuestions(userId: String): List<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            questionRepository.getQuestionsByUser(userId).map { it.question to it.answer }
        }
    }

    suspend fun getDailyTrainingTip(dogProfile: Dog): String {
        return withContext(Dispatchers.IO) {
            val systemPrompt = """
                You are an experienced dog trainer providing daily tips for dog owners. 
                Your goal is to offer concise, practical advice that can help improve a dog's 
                behavior, skills, or the overall relationship between the dog and its owner. 
                Keep your tips short, around 2-3 sentences.
            """.trimIndent()

            val userPrompt = """
                Please provide a daily training tip for a ${dogProfile.breed} dog, 
                aged ${dogProfile.age}, with an energy level of ${dogProfile.energyLevel}.
                Today's date is ${LocalDate.now()}.
            """.trimIndent()

            val messages = listOf(
                ChatMessage(role = ChatRole.System, content = systemPrompt),
                ChatMessage(role = ChatRole.User, content = userPrompt)
            )

            val response = openAI.chatCompletion(
                ChatCompletionRequest(
                    model = ModelId(GPT_4_MODEL),
                    messages = messages
                )
            )

            response.choices.first().message?.content ?: "No training tip available at the moment."
        }
    }

    suspend fun getTrainerAdvice(userId: String, question: String, dogProfile: Dog): String? {
        return withContext(Dispatchers.IO) {
            val user = userRepository.getUserById(userId) ?: return@withContext null

            if (!user.hasSubscription) {
                return@withContext "Trainer advice is available only for subscribed users. Please subscribe for access to this feature."
            }

            val systemPrompt = """
                You are an expert dog trainer providing advice to dog owners. Your role is to offer 
                practical, tailored training tips and strategies. Consider the specific characteristics 
                of the dog and the owner's question when formulating your response. Keep your advice 
                concise and actionable.
            """.trimIndent()

            val userPrompt = """
                I have a ${dogProfile.breed} dog, aged ${dogProfile.age}, with an energy level of ${dogProfile.energyLevel}.
                My training question is: $question
            """.trimIndent()

            val messages = listOf(
                ChatMessage(role = ChatRole.System, content = systemPrompt),
                ChatMessage(role = ChatRole.User, content = userPrompt)
            )

            val response = openAI.chatCompletion(
                ChatCompletionRequest(
                    model = ModelId(GPT_4_MODEL),
                    messages = messages
                )
            )

            val answer = response.choices.first().message?.content ?: "No advice available at the moment."

            // Save the question and answer
            questionRepository.saveQuestion(userId, question, answer)

            answer
        }
    }
}