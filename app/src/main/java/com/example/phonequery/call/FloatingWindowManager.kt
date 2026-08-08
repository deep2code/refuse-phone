package com.example.phonequery.call

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.example.phonequery.R
import com.example.phonequery.model.PhoneInfo

/**
 * 来电悬浮窗管理器。
 * 使用系统 [WindowManager] 在来电时显示半透明卡片。
 */
class FloatingWindowManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: View? = null

    @Suppress("DEPRECATION")
    fun show(number: String, info: PhoneInfo, isWhitelist: Boolean = false) {
        if (!hasPermission()) {
            return
        }

        hide()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120
        }

        val view = LayoutInflater.from(context).inflate(R.layout.floating_call_window, null)
        bindView(view, number, info, isWhitelist)
        setupDrag(view, params)

        windowManager.addView(view, params)
        floatingView = view
    }

    private fun bindView(view: View, number: String, info: PhoneInfo, isWhitelist: Boolean) {
        view.findViewById<TextView>(R.id.tv_phone).text = number
        view.findViewById<TextView>(R.id.tv_location).text = buildString {
            append(info.province ?: "")
            append(info.city ?: "")
            if (isNotEmpty()) append(" ") else append("未知归属地 ")
            append(info.carrier ?: "")
        }.trim()

        view.findViewById<TextView>(R.id.tv_type).text = info.numberType.displayName

        val spamView = view.findViewById<TextView>(R.id.tv_spam)
        val spamText = buildSpamText(info)
        if (spamText.isNotBlank()) {
            spamView.text = spamText
            spamView.visibility = View.VISIBLE
        } else {
            spamView.visibility = View.GONE
        }

        val whitelistView = view.findViewById<TextView>(R.id.tv_whitelist_hint)
        whitelistView.visibility = if (isWhitelist) View.VISIBLE else View.GONE

        val enterpriseView = view.findViewById<TextView>(R.id.tv_enterprise)
        // 悬浮窗不展开企业列表，仅提示可进入 App 查看
        enterpriseView.visibility = View.GONE

        view.findViewById<Button>(R.id.btn_close).setOnClickListener {
            hide()
        }
    }

    private fun buildSpamText(info: PhoneInfo): String {
        val parts = mutableListOf<String>()
        info.spamType?.let { parts.add("标记：$it") }
        info.spamCount?.let { parts.add("次数：$it") }
        info.platformMarks.forEach { parts.add("${it.platform}: ${it.mark}") }
        return parts.joinToString(" | ")
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    if (floatingView != null) {
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun hide() {
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: IllegalArgumentException) {
                // 已经移除
            }
            floatingView = null
        }
    }

    private fun hasPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }
}