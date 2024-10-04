package com.example.pawsomepals.subscription

import com.example.pawsomepals.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class SubscriptionManager(private val userRepository: UserRepository) {

    suspend fun isUserSubscribed(userId: String): Boolean {
        return userRepository.getUserById(userId)?.subscriptionEndDate?.isAfter(LocalDate.now()) == true
    }

    fun getSubscriptionStatus(userId: String): Flow<SubscriptionStatus> {
        return userRepository.getUserFlow(userId).map { user ->
            when {
                user == null -> SubscriptionStatus.NOT_SUBSCRIBED
                user.subscriptionEndDate == null -> SubscriptionStatus.NOT_SUBSCRIBED
                user.subscriptionEndDate?.isBefore(LocalDate.now()) == true -> SubscriptionStatus.EXPIRED
                else -> SubscriptionStatus.ACTIVE
            }
        }
    }

    suspend fun decrementRemainingQuestions(userId: String): Int {
        val currentCount = getRemainingQuestions(userId)
        if (currentCount > 0) {
            incrementQuestionCount(userId)
        }
        return currentCount - 1
    }

    suspend fun subscribeUser(userId: String, durationInMonths: Int) {
        val endDate = LocalDate.now().plusMonths(durationInMonths.toLong())
        userRepository.updateSubscription(userId, endDate)
    }

    suspend fun cancelSubscription(userId: String) {
        userRepository.updateSubscription(userId, null)
    }

    suspend fun getRemainingQuestions(userId: String): Int {
        val user = userRepository.getUserById(userId) ?: return 0
        return if (isUserSubscribed(userId)) {
            Int.MAX_VALUE // Subscribed users have unlimited questions
        } else {
            MAX_FREE_QUESTIONS - user.dailyQuestionCount
        }
    }

    suspend fun incrementQuestionCount(userId: String) {
        userRepository.incrementDailyQuestionCount(userId)
    }

    suspend fun resetDailyQuestionCount() {
        userRepository.resetAllDailyQuestionCounts()
    }

    companion object {
        const val MAX_FREE_QUESTIONS = 5
    }
}