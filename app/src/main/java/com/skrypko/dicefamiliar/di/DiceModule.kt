package com.skrypko.dicefamiliar.di

import com.skrypko.dicefamiliar.domain.dice.DiceRollHandler
import com.skrypko.dicefamiliar.domain.dice.DiceRoller
import com.skrypko.dicefamiliar.domain.random.RandomSource
import com.skrypko.dicefamiliar.domain.remote.RemoteActionHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DiceModule {
    @Provides
    @Singleton
    fun provideDiceRoller(random: RandomSource): DiceRoller = DiceRoller(random)

    @Provides
    @IntoSet
    fun provideDiceRollHandler(roller: DiceRoller): RemoteActionHandler<*, *> = DiceRollHandler(roller)
}
