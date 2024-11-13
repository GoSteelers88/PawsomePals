package io.pawsomepals.app.ui.theme

import io.pawsomepals.app.ai.AIFeatures
import io.pawsomepals.app.data.repository.UserRepository
import io.pawsomepals.app.subscription.SubscriptionManager
import io.pawsomepals.app.viewmodel.HealthAdvisorViewModel
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