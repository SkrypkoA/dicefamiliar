package com.skrypko.dicefamiliar.di

import com.skrypko.dicefamiliar.domain.random.RandomSource
import com.skrypko.dicefamiliar.domain.random.SecureRandomSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RandomModule {
    @Provides
    @Singleton
    fun provideRandomSource(): RandomSource = SecureRandomSource()
}
