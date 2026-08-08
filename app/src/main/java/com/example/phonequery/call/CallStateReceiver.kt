package com.example.phonequery.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log

/**
 * 监听来电状态广播，启动 [CallHandlerService] 进行号码识别、悬浮窗展示和自动挂断。
 */
class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d(TAG, "Phone state: $state, number: $incomingNumber")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!incomingNumber.isNullOrBlank()) {
                    startService(context) {
                        putExtra(EXTRA_PHONE_NUMBER, incomingNumber)
                        putExtra(EXTRA_CALL_STATE, CallHandlerService.STATE_RINGING)
                    }
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK,
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // 通话建立或结束：通知服务关闭悬浮窗
                startService(context) {
                    putExtra(EXTRA_CALL_STATE, CallHandlerService.STATE_ENDED)
                }
            }
        }
    }

    private fun startService(context: Context, block: Intent.() -> Unit) {
        val serviceIntent = Intent(context, CallHandlerService::class.java).apply(block)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动来电处理服务失败", e)
        }
    }

    companion object {
        private const val TAG = "CallStateReceiver"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CALL_STATE = "extra_call_state"
    }
}