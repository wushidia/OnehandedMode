package io.githuh.onehandedmode

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class ModuleApplication : Application(), XposedServiceHelper.OnServiceListener {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingScales = mutableListOf<() -> Unit>()
    var service by mutableStateOf<XposedService?>(null)
        private set

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        mainHandler.post {
            HandySettings.migrate(service.getRemotePreferences(HandySettings.GROUP))
            this.service = service
            pendingScales.forEach { it() }
            pendingScales.clear()
        }
    }

    override fun onServiceDied(service: XposedService) {
        mainHandler.post { this.service = null }
    }

    fun saveScale(percent: Int, onSaved: () -> Unit) {
        val save = {
            service!!.getRemotePreferences(HandySettings.GROUP).edit()
                .putInt(HandySettings.CUSTOM, percent)
                .putInt(HandySettings.MODE, HandySettings.CUSTOM_MODE)
                // Finish the remote write before releasing the broadcast's pending result.
                .commit()
            onSaved()
        }
        if (service != null) save() else pendingScales.add(save)
    }
}
