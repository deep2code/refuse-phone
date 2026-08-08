package com.example.phonequery.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.phonequery.data.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机完成后，根据用户设置自动启用来电监听服务。
 */
class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        scope.launch {
            val settings = SettingsDataStore(context).settingsFlow.first()
            if (!settings.enableBootStart) return@launch

            val serviceIntent = Intent(context, CallHandlerService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "开机启动来电识别服务")
            } catch (e: Exception) {
                Log.e(TAG, "开机启动服务失败", e)
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}