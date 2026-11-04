package com.skrypko.dicefamiliar.data.lan

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.skrypko.dicefamiliar.MainActivity
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.data.settings.AppSettingsRepository
import com.skrypko.dicefamiliar.domain.dice.DiceRollAction
import com.skrypko.dicefamiliar.domain.remote.ActionCodec
import com.skrypko.dicefamiliar.domain.remote.ActionRequest
import com.skrypko.dicefamiliar.domain.settings.LanNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Whether any activity of the app is on screen (requests are announced by notification only when it isn't). */
@Singleton
class AppVisibility @Inject constructor() : Application.ActivityLifecycleCallbacks {
    private var started = 0
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    override fun onActivityStarted(activity: Activity) {
        started++
        _visible.value = true
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        _visible.value = started > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/** "Open the pending roll request": set by a notification tap, consumed by the navigation (Dice tab). */
@Singleton
class LanNavigation @Inject constructor() {
    private val _openRequest = MutableStateFlow(false)
    val openRequest: StateFlow<Boolean> = _openRequest.asStateFlow()

    fun requestOpened() {
        _openRequest.value = true
    }

    fun consumed() {
        _openRequest.value = false
    }

    companion object {
        const val EXTRA_OPEN_REQUEST = "com.skrypko.dicefamiliar.OPEN_LAN_REQUEST"
    }
}

/** LAN notifications: the ongoing session one (for the foreground service) and roll requests. */
@Singleton
class LanNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_SESSION, context.getString(R.string.lan_channel_session), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.lan_channel_session_description)
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_ALERT, context.getString(R.string.lan_channel_requests), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.lan_channel_requests_description)
                    enableVibration(true)
                },
                NotificationChannel(CHANNEL_VIBRATE, context.getString(R.string.lan_channel_requests_vibrate), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.lan_channel_requests_vibrate_description)
                    setSound(null, null)
                    enableVibration(true)
                },
            ),
        )
    }

    fun sessionNotification(text: String): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_stat_dice)
            .setContentTitle(context.getString(R.string.lan_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(open = false))
            .addAction(0, context.getString(R.string.lan_leave), leaveIntent())
            .build()

    fun updateSession(text: String) = post(SESSION_ID, sessionNotification(text))

    fun postRequest(text: String, mode: LanNotifications) {
        if (mode == LanNotifications.OFF) return
        val channel = if (mode == LanNotifications.VIBRATION_ONLY) CHANNEL_VIBRATE else CHANNEL_ALERT
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_dice)
            .setContentTitle(context.getString(R.string.lan_request_title))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(if (mode == LanNotifications.VIBRATION_ONLY) NotificationCompat.DEFAULT_VIBRATE else NotificationCompat.DEFAULT_ALL)
            .setSilent(false)
            .setAutoCancel(true)
            .setContentIntent(openApp(open = true))
            .build()
        post(REQUEST_ID, notification)
    }

    fun cancelRequest() = manager.cancel(REQUEST_ID)

    fun cancelAll() {
        manager.cancel(REQUEST_ID)
        manager.cancel(SESSION_ID)
    }

    private fun post(id: Int, notification: android.app.Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) {
            @Suppress("MissingPermission") // Checked just above.
            manager.notify(id, notification)
        }
    }

    private fun openApp(open: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(LanNavigation.EXTRA_OPEN_REQUEST, open)
        return PendingIntent.getActivity(context, if (open) 1 else 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun leaveIntent(): PendingIntent =
        PendingIntent.getService(
            context,
            2,
            Intent(context, LanService::class.java).setAction(LanService.ACTION_LEAVE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** The system screen for the request notifications ("like any other app"). */
    fun systemSettingsIntent(mode: LanNotifications): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, if (mode == LanNotifications.VIBRATION_ONLY) CHANNEL_VIBRATE else CHANNEL_ALERT)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.fromParts("package", context.packageName, null))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        const val CHANNEL_SESSION = "lan_session"
        const val CHANNEL_ALERT = "lan_requests_alert"
        const val CHANNEL_VIBRATE = "lan_requests_vibrate"
        const val SESSION_ID = 4101
        const val REQUEST_ID = 4102
    }
}

/**
 * Glue between the session and Android: starts/stops [LanService] with the session, keeps its notification
 * text current and announces roll requests by notification while the app isn't on screen.
 */
@Singleton
class LanCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: LanSessionManager,
    private val notifier: LanNotifier,
    private val visibility: AppVisibility,
    private val settings: AppSettingsRepository,
    private val codec: ActionCodec,
) {
    fun start(scope: CoroutineScope) {
        notifier.createChannels()
        scope.launch {
            session.state.map { it.role }.distinctUntilChanged().collect { role ->
                if (role == LanRole.NONE) {
                    context.stopService(Intent(context, LanService::class.java))
                    notifier.cancelAll()
                } else {
                    ContextCompat.startForegroundService(context, Intent(context, LanService::class.java))
                }
            }
        }
        scope.launch {
            session.state.map { sessionText(it) }.distinctUntilChanged().collect { text ->
                if (text != null) notifier.updateSession(text)
            }
        }
        scope.launch {
            combine(session.state.map { it.incoming }.distinctUntilChanged { a, b -> a?.id == b?.id }, visibility.visible) { request, visible -> request to visible }
                .collect { (request, visible) ->
                    when {
                        request == null -> notifier.cancelRequest()
                        visible -> notifier.cancelRequest()
                        else -> notifier.postRequest(requestText(request), settings.settings.first().lanNotifications)
                    }
                }
        }
    }

    fun sessionText(state: LanState): String? = when (state.role) {
        LanRole.NONE -> null
        LanRole.HOST -> {
            val count = state.players.count { it.connected }
            context.resources.getQuantityString(R.plurals.lan_session_hosting, count, count)
        }
        LanRole.PLAYER -> context.getString(R.string.lan_session_playing, state.host?.name.orEmpty())
    }

    private fun requestText(request: ActionRequest): String {
        val payload = runCatching { codec.decodePayload(DiceRollAction, request) }.getOrNull()
        val dice = payload?.let { if (it.pool) context.getString(R.string.lan_request_pool, it.notation) else it.notation }.orEmpty()
        return context.getString(R.string.lan_request_text, request.from.name, dice)
    }
}
