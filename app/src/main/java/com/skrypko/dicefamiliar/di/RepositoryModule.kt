package com.skrypko.dicefamiliar.di

import com.skrypko.dicefamiliar.data.dice.AssetDiceSetRepository
import com.skrypko.dicefamiliar.data.dice.DataStoreDiceStateRepository
import com.skrypko.dicefamiliar.data.dice.DiceSetRepository
import com.skrypko.dicefamiliar.data.dice.DiceStateRepository
import com.skrypko.dicefamiliar.data.navigation.DataStoreLastTabRepository
import com.skrypko.dicefamiliar.data.navigation.LastTabRepository
import com.skrypko.dicefamiliar.data.remote.DataStoreParticipantRepository
import com.skrypko.dicefamiliar.data.remote.ParticipantRepository
import com.skrypko.dicefamiliar.data.settings.AppSettingsRepository
import com.skrypko.dicefamiliar.data.settings.DataStoreAppSettingsRepository
import com.skrypko.dicefamiliar.ui.common.SoundPlayer
import com.skrypko.dicefamiliar.ui.common.SoundPoolSoundPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindAppSettings(impl: DataStoreAppSettingsRepository): AppSettingsRepository

    @Binds
    abstract fun bindDiceState(impl: DataStoreDiceStateRepository): DiceStateRepository

    @Binds
    abstract fun bindParticipant(impl: DataStoreParticipantRepository): ParticipantRepository

    @Binds
    abstract fun bindDiceSets(impl: AssetDiceSetRepository): DiceSetRepository

    @Binds
    abstract fun bindLastTab(impl: DataStoreLastTabRepository): LastTabRepository

    @Binds
    abstract fun bindSoundPlayer(impl: SoundPoolSoundPlayer): SoundPlayer
}
