package com.example.phonequery.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.phonequery.MainActivity
import com.example.phonequery.R
import com.example.phonequery.data.PhoneRepository
import com.example.phonequery.data.ContactChecker
import com.example.phonequery.data.NON_DIGIT_REGEX
import com.example.phonequery.data.SettingsDataStore
import com.example.phonequery.data.BlocklistRepository
import com.example.phonequery.data.PhoneAttributionRepository
import com.example.phonequery.data.RecentCallRepository
import com.example.phonequery.data.AppSettings
import com.example.phonequery.db.AppDatabase
import com.example.phonequery.model.PhoneInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 来电处理前台服务。
 * 负责：来电号码识别、自动挂断、启动悬浮窗展示。
 */
class CallHandlerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var phoneRepository: PhoneRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var blocklistRepository: BlocklistRepository
    private lateinit var phoneAttributionRepository: PhoneAttributionRepository
    private lateinit var recentCallRepository: RecentCallRepository
    private lateinit var db: AppDatabase
    private var floatingWindow: FloatingWindowManager? = null
    private var previousRingerMode: Int? = null

    /** 设置快照缓存：DataStore 变更自动刷新，避免每通来电阻塞读盘。 */
    @Volatile
    private var cachedSettings: AppSettings? = null

    override fun onCreate() {
        super.onCreate()
        phoneRepository = PhoneRepository(this)
        settingsDataStore = SettingsDataStore(this)
        blocklistRepository = BlocklistRepository(this)
        phoneAttributionRepository = PhoneAttributionRepository(this)
        recentCallRepository = RecentCallRepository(this)
        db = AppDatabase.getInstance(this)
        floatingWindow = FloatingWindowManager(this)

        // 预加载设置快照（DataStore 首次读盘后即内存缓存，此处只收更新）
        serviceScope.launch {
            settingsDataStore.settingsFlow.collect { cachedSettings = it }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val state = intent?.getIntExtra(CallStateReceiver.EXTRA_CALL_STATE, STATE_UNKNOWN) ?: STATE_UNKNOWN
        val number = intent?.getStringExtra(CallStateReceiver.EXTRA_PHONE_NUMBER)

        when (state) {
            STATE_RINGING -> handleRinging(number)
            STATE_ENDED -> handleCallEnded()
            else -> handleRinging(number)
        }

        return START_NOT_STICKY
    }

    private fun handleRinging(number: String?) {
        if (number.isNullOrBlank()) return

        serviceScope.launch {
            val settings = cachedSettings ?: settingsDataStore.settingsFlow.first()
            if (!settings.enableFloatingWindow && !settings.enableAutoHangup) {
                stopSelf()
                return@launch
            }

            val digits = number.replace(NON_DIGIT_REGEX, "")
            // 拦截动作：log=仅记录（不实际挂断）；block=默认拒接
            val allowBlock = settings.interceptAction == AppSettings.INTERCEPT_BLOCK
            // 本通来电是否被实际拦截（用于「最近来电」留痕）
            var wasBlocked = false

            // 1. 白名单优先（本地快速判断）
            if (db.blocklistDao().isWhitelisted(number) || (ContactChecker.hasPermission(this@CallHandlerService)
                        && ContactChecker.isInContacts(this@CallHandlerService, digits))
            ) {
                Log.d(TAG, "号码 $number 在白名单/通讯录中，跳过处理")
                if (settings.enableFloatingWindow) {
                    showFloatingWindow(number, PhoneInfo(number = number), isWhitelist = true, alpha = settings.floatingAlpha)
                }
                return@launch
            }

            // 1.5 非通讯录拦截：开启「仅放行通讯录」且来电不在通讯录中，直接挂断。
            //     未授予 READ_CONTACTS 时 isInContacts 恒为 false，会误拦通讯录号码，故需先校验权限。
            if (settings.enableBlockNonContacts && ContactChecker.hasPermission(this@CallHandlerService)) {
                val inContacts = ContactChecker.isInContacts(this@CallHandlerService, number)
                if (!inContacts) {
                    Log.d(TAG, "号码 $number 不在通讯录，按「仅放行通讯录」拦截")
                    val blocked = settings.enableAutoHangup
                    if (blocked) endCall()
                    if (settings.enableFloatingWindow) {
                        showFloatingWindow(
                            number,
                            PhoneInfo(number = number, errorMessage = "非通讯录号码，已拦截"),
                            isWhitelist = false,
                            alpha = settings.floatingAlpha
                        )
                    }
                    recordRecentCall(
                        number = number,
                        digits = digits,
                        label = "非通讯录号码，已拦截",
                        description = null,
                        blocked = blocked,
                        spamType = null
                    )
                    return@launch
                }
            }

            // 2. 黑名单本地快速判断：命中则立即挂断，不再等待在线查询
            //    求职模式下：黑名单仍会挂断，但骚扰标记不会自动挂断，避免错过面试电话
            if (settings.enableAutoHangup) {
                val isBlacklisted = db.blocklistDao().isBlacklisted(number)
                // 高级规则（正则 / 归属地）：命中黑名单规则同样立即挂断
                val attrCity = if (phoneAttributionRepository.isEnabled) {
                    phoneAttributionRepository.lookupAttribution(digits)?.let { (_, c, _) -> c }
                } else null
                val advancedHit = runCatching {
                    blocklistRepository.evaluateAdvanced(digits, attrCity)
                }.getOrNull()

                if (isBlacklisted || advancedHit != null) {
                    Log.d(TAG, "号码 $number 命中黑名单/规则，自动挂断")
                    val blocked = allowBlock
                    if (blocked) endCall()
                    if (settings.enableFloatingWindow) {
                        showFloatingWindow(
                            number,
                            PhoneInfo(
                                number = number,
                                errorMessage = if (blocked) "已命中黑名单并自动挂断"
                                else "命中黑名单（仅记录）"
                            ),
                            isWhitelist = false,
                            alpha = settings.floatingAlpha
                        )
                    }
                    recordRecentCall(
                        number = number,
                        digits = digits,
                        label = if (blocked) "已命中黑名单并自动挂断" else "命中黑名单（仅记录）",
                        description = null,
                        blocked = blocked,
                        spamType = null
                    )
                    return@launch
                }

                // 若只启用黑名单模式，则无需继续在线查询
                if (settings.enableBlacklistOnly) {
                    if (settings.enableFloatingWindow) {
                        showFloatingWindow(number, PhoneInfo(number = number), isWhitelist = false, alpha = settings.floatingAlpha)
                    }
                    return@launch
                }
            }

            // 3. 查询号码信息（可能涉及网络请求）
            val info = try {
                phoneRepository.query(number)
            } catch (e: Exception) {
                Log.e(TAG, "号码识别失败", e)
                PhoneInfo(number = number)
            }

            // 4. 根据在线标记决定是否挂断（求职模式下不根据骚扰标记挂断）
            if (settings.enableAutoHangup
                && settings.enableSpamAutoHangup
                && !settings.enableJobHuntMode
                && allowBlock
            ) {
                val isSpam = !info.spamType.isNullOrBlank() || info.platformMarks.isNotEmpty()
                if (isSpam) {
                    Log.d(TAG, "号码 $number 被标记为骚扰，自动挂断")
                    endCall()
                    wasBlocked = true
                }
            }

            // 5. 未知号码判定：不在黑白名单、也无任何骚扰标记
            val isUnknown = !db.blocklistDao().isWhitelisted(number)
                    && !db.blocklistDao().isBlacklisted(number)
                    && info.spamType.isNullOrBlank()
                    && info.platformMarks.isEmpty()

            // 6. 陌生号静默：非求职模式下，对未知/未标记号码临时静音，
            //    不响铃只弹窗+通知，避免被骚扰电话频繁打断；通话结束后恢复铃声。
            //    求职模式下不静音，确保面试/重要来电能正常响铃。
            val shouldSilence = settings.enableSilenceUnknown
                    && !settings.enableJobHuntMode
                    && isUnknown
                    && (settings.enableAutoHangup || settings.enableFloatingWindow)

            if (shouldSilence) {
                silenceRinger()
                notifySilencedCall(number, info)
            }

            // 7. 显示悬浮窗
            //    求职模式下，未知号码给出醒目提示，避免错过面试电话
            val shouldWarnUnknown = settings.enableJobHuntMode
                    && isUnknown

            if (settings.enableFloatingWindow) {
                val displayInfo = if (shouldWarnUnknown) {
                    info.copy(
                        errorMessage = "求职模式：陌生号码，请留意，避免错过面试/重要来电"
                    )
                } else {
                    info
                }
                showFloatingWindow(number, displayInfo, isWhitelist = false, alpha = settings.floatingAlpha)
            }

            if (shouldWarnUnknown) {
                Log.d(TAG, "求职模式：号码 $number 为未知号码，请留意不要错过来电")
            }

            // 写入「最近来电」留痕（ScreeningService 与本服务会按号码+3秒去重合并）
            val label = info.spamType
                ?: listOfNotNull(info.province, info.city).joinToString("").ifBlank { null }
            val desc = info.platformMarks.joinToString(" | ") { "${it.platform}: ${it.mark}" }
                .ifBlank { null }
            recordRecentCall(
                number = number,
                digits = digits,
                label = label,
                description = desc,
                blocked = wasBlocked,
                spamType = info.spamType
            )
        }
    }

    /**
     * 写入「最近来电」留痕（去重在 RecentCallRepository 内按 号码+3秒 合并），
     * 并在实际拦截 / 有骚扰标记时尽力回写系统通话记录。
     * 抽成独立方法以便各拦截分支在提前 return 前也能正确留痕，
     * 否则被拦截的来电不会出现在「最近来电」列表中。
     */
    private suspend fun recordRecentCall(
        number: String,
        digits: String,
        label: String?,
        description: String?,
        blocked: Boolean,
        spamType: String?
    ) {
        try {
            recentCallRepository.record(
                number = number,
                digits = digits,
                name = label,
                description = description,
                blocked = blocked,
                spamType = spamType
            )
            // 仅 Android 9- 且已授权时可回写；Android 10+ 受限跳过（best-effort）
            if (blocked || !spamType.isNullOrBlank()) {
                recentCallRepository.markSystemCallLog(digits, label ?: "骚扰")
            }
        } catch (_: Exception) {
            // 留痕失败不影响主流程
        }
    }

    private fun handleCallEnded() {
        restoreRinger()
        serviceScope.launch(Dispatchers.Main) {
            floatingWindow?.hide()
        }
        stopSelf()
    }

    /**
     * 临时将系统铃声调为静音（用于「陌生号静默」）。
     * 仅修改铃声音量模式，不影响媒体音量；通话结束后由 [restoreRinger] 还原。
     * MODIFY_AUDIO_SETTINGS 为普通权限，安装即授予，无需运行时申请。
     */
    private fun silenceRinger() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager == null) return
            val current = audioManager.ringerMode
            if (current != AudioManager.RINGER_MODE_SILENT) {
                previousRingerMode = current
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            }
        } catch (e: Exception) {
            Log.w(TAG, "设置静音失败", e)
        }
    }

    /** 恢复静音前的铃声音量模式 */
    private fun restoreRinger() {
        previousRingerMode?.let { mode ->
            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.ringerMode = mode
            } catch (e: Exception) {
                Log.w(TAG, "恢复铃声失败", e)
            }
            previousRingerMode = null
        }
    }

    /** 陌生号被静音后，用高优先级通知提醒用户，避免完全错过 */
    private fun notifySilencedCall(number: String, info: PhoneInfo) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    SILENT_CHANNEL_ID,
                    "静默来电提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "陌生来电已静音，仅在此提醒"
                }
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                1,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val text = if (info.spamType.isNullOrBlank()) {
                "陌生号码 $number"
            } else {
                "${info.spamType} $number"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(
                SILENT_NOTIFICATION_ID,
                NotificationCompat.Builder(this, SILENT_CHANNEL_ID)
                    .setContentTitle("陌生来电（已静音）")
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()
            )
        } catch (e: Exception) {
            Log.w(TAG, "发送静音来电通知失败", e)
        }
    }

    private fun showFloatingWindow(number: String, info: PhoneInfo, isWhitelist: Boolean, alpha: Float = 0.9f) {
        serviceScope.launch(Dispatchers.Main) {
            if (Settings.canDrawOverlays(this@CallHandlerService)) {
                floatingWindow?.show(number, info, isWhitelist, alpha)
            } else {
                Log.w(TAG, "缺少悬浮窗权限，无法显示悬浮窗")
            }
        }
    }

    /**
     * 挂断当前来电。
     * Android P+ 使用官方 [TelecomManager.endCall]，旧版本尝试反射。
     */
    private fun endCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ANSWER_PHONE_CALLS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                try {
                    telecomManager?.endCall()
                } catch (e: Exception) {
                    Log.e(TAG, "TelecomManager.endCall 失败", e)
                }
            }
        } else {
            endCallByReflection()
        }
    }

    @Suppress("DEPRECATION")
    private fun endCallByReflection() {
        try {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val clazz = Class.forName(telephonyManager.javaClass.name)
            val method = clazz.getDeclaredMethod("getITelephony")
            method.isAccessible = true
            val iTelephony = method.invoke(telephonyManager)
            val endCallMethod = iTelephony.javaClass.getDeclaredMethod("endCall")
            endCallMethod.invoke(iTelephony)
        } catch (e: Exception) {
            Log.e(TAG, "反射挂断电话失败", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "来电识别服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持来电识别和悬浮窗服务运行"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("正在监听来电…")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        restoreRinger()
        floatingWindow?.hide()
        floatingWindow = null
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "CallHandlerService"
        private const val CHANNEL_ID = "call_handler_channel"
        private const val NOTIFICATION_ID = 1001
        private const val SILENT_CHANNEL_ID = "silent_call_channel"
        private const val SILENT_NOTIFICATION_ID = 1002

        const val STATE_UNKNOWN = 0
        const val STATE_RINGING = 1
        const val STATE_ENDED = 2
    }
}