package com.skrypko.dicefamiliar.di

import com.skrypko.dicefamiliar.domain.pool.PoolPicker
import com.skrypko.dicefamiliar.domain.random.RandomSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PoolModule {
    @Provides
    @Singleton
    fun providePoolPicker(random: RandomSource): PoolPicker = PoolPicker(random)
}
