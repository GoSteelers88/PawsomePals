package com.example.pawsomepals.ui.theme

import com.example.pawsomepals.ai.AIFeatures
import com.example.pawsomepals.data.repository.UserRepository
import com.example.pawsomepals.subscription.SubscriptionManager
import com.example.pawsomepals.viewmodel.HealthAdvisorViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object HealthAdvisorModule {

    @Provides
    fun provideHealthAdvisorViewModel(
        aiFeatures: AIFeatures,
        userRepository: UserRepository,
        subscriptionManager: SubscriptionManager
    ): HealthAdvisorViewModel {
        return HealthAdvisorViewModel(aiFeatures, userRepository, subscriptionManager)
    }}