package com.example.pawsomepals.di

import androidx.lifecycle.ViewModel
import dagger.MapKey
import kotlin.reflect.KClass

/**
 * A custom key annotation that can be used to associate ViewModel classes with provider methods in Dagger modules.
 * This annotation is specifically designed for use with ViewModels in a Hilt-enabled Android application.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
@MapKey
annotation class ViewModelKey(val value: KClass<out ViewModel>)

/**
 * Example usage:
 * @Binds
 * @IntoMap
 * @ViewModelKey(YourViewModel::class)
 * abstract fun bindYourViewModel(viewModel: YourViewModel): ViewModel
 */