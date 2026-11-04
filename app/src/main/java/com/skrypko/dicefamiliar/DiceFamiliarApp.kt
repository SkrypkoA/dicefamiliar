package com.skrypko.dicefamiliar

import android.app.Application
import com.skrypko.dicefamiliar.data.lan.AppVisibility
import com.skrypko.dicefamiliar.data.lan.LanCoordinator
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DiceFamiliarApp : Application() {
    @Inject lateinit var visibility: AppVisibility
    @Inject lateinit var lanCoordinator: LanCoordinator

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(visibility)
        lanCoordinator.start(appScope)
    }
}
