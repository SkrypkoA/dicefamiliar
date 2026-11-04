package com.skrypko.dicefamiliar.di

import android.os.SystemClock
import android.util.Log
import com.skrypko.dicefamiliar.data.lan.LanSessionManager
import com.skrypko.dicefamiliar.data.lan.transport.LanTransport
import com.skrypko.dicefamiliar.data.lan.transport.LanTransports
import com.skrypko.dicefamiliar.data.lan.transport.WifiNsdTransport
import com.skrypko.dicefamiliar.data.remote.ParticipantRepository
import com.skrypko.dicefamiliar.data.settings.AppSettingsRepository
import com.skrypko.dicefamiliar.domain.remote.ActionCodec
import com.skrypko.dicefamiliar.domain.remote.RemoteActionRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/**
 * LAN play. Every transport this build ships is contributed with `@Provides @IntoSet fun ...(): LanTransport`
 * (a flavor can add Nearby Connections or a relay in its own module); the active one comes from
 * `AppSettings.lanTransport`.
 */
@Module
@InstallIn(SingletonComponent::class)
object LanModule {
    @Provides
    fun provideDiceGateway(manager: LanSessionManager): com.skrypko.dicefamiliar.data.lan.LanDiceGateway = manager

    @Provides
    @IntoSet
    fun provideWifiTransport(transport: WifiNsdTransport): LanTransport = transport

    @Provides
    @Singleton
    fun provideTransports(transports: Set<@JvmSuppressWildcards LanTransport>): LanTransports = LanTransports(transports)

    @Provides
    @Singleton
    fun provideSessionManager(
        transports: LanTransports,
        settings: AppSettingsRepository,
        participants: ParticipantRepository,
        registry: RemoteActionRegistry,
        codec: ActionCodec,
        clock: Clock,
    ): LanSessionManager = LanSessionManager(
        transportProvider = { transports.get(settings.settings.first().lanTransport) },
        selfProvider = { participants.self() },
        registry = registry,
        codec = codec,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        now = { SystemClock.elapsedRealtime() },
        wallTime = { clock.now().toEpochMilliseconds() },
        log = { message, error -> Log.d("Lan", message, error) },
    )
}
