package com.skrypko.dicefamiliar.di

import com.skrypko.dicefamiliar.domain.remote.ActionCodec
import com.skrypko.dicefamiliar.domain.remote.IdGenerator
import com.skrypko.dicefamiliar.domain.remote.LocalActionExecutor
import com.skrypko.dicefamiliar.domain.remote.RemoteActionHandler
import com.skrypko.dicefamiliar.domain.remote.RemoteActionRegistry
import com.skrypko.dicefamiliar.domain.remote.UuidIdGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.serialization.json.Json

/**
 * Shared remote-action layer. Features contribute handlers with
 * `@Provides @IntoSet fun ...(): RemoteActionHandler<*, *>` (see DiceModule).
 */
@Module
@InstallIn(SingletonComponent::class)
object RemoteModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.System

    @Provides
    @Singleton
    fun provideIdGenerator(): IdGenerator = UuidIdGenerator()

    @Provides
    @Singleton
    fun provideActionCodec(json: Json, ids: IdGenerator, clock: Clock): ActionCodec = ActionCodec(json, ids, clock)

    @Provides
    @Singleton
    fun provideRegistry(
        handlers: Set<@JvmSuppressWildcards RemoteActionHandler<*, *>>,
        json: Json,
        clock: Clock,
    ): RemoteActionRegistry = RemoteActionRegistry(handlers, json, clock)

    @Provides
    @Singleton
    fun provideLocalExecutor(registry: RemoteActionRegistry, codec: ActionCodec): LocalActionExecutor =
        LocalActionExecutor(registry, codec)
}
