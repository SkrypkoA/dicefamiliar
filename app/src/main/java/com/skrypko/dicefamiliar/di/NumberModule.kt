package com.skrypko.dicefamiliar.di

import com.skrypko.dicefamiliar.domain.number.NumberGenerator
import com.skrypko.dicefamiliar.domain.random.RandomSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NumberModule {
    @Provides
    @Singleton
    fun provideNumberGenerator(random: RandomSource): NumberGenerator = NumberGenerator(random)
}
