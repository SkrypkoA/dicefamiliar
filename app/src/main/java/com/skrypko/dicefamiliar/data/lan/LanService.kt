package com.skrypko.dicefamiliar.data.lan

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps the LAN session alive while hosting or playing (so requests arrive with the app in the background). */
@AndroidEntryPoint
class LanService : Service() {
    @Inject lateinit var session: LanSessionManager
    @Inject lateinit var notifier: LanNotifier
    @Inject lateinit var coordinator: LanCoordinator

    private val scope: CoroutineScope = MainScope()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LEAVE) {
            scope.launch { session.leave() }
            return START_NOT_STICKY
        }
        val text = coordinator.sessionText(session.state.value)
        if (text == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, LanNotifier.SESSION_ID, notifier.sessionNotification(text), type)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_LEAVE = "com.skrypko.dicefamiliar.LAN_LEAVE"
    }
}
