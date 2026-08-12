package com.example.phonequery.data

import android.content.Context
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.example.phonequery.db.AppDatabase
import com.example.phonequery.db.RecentCallEntity
import kotlinx.coroutines.flow.Flow
import android.Manifest
import android.content.ContentValues
import android.os.Build
import android.provider.CallLog.Calls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 最近来电本地仓库。
 * 由来电识别链路（ScreeningService / CallHandlerService）写入，
 * 在「最近来电」页展示。两条识别链路可能各触发一次，
 * 故写入时按「同号码 + 3 秒内」去重并合并更丰富的信息，避免重复行。
 */
class RecentCallRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).recentCallDao()
    private val appContext = context.applicationContext

    val all: Flow<List<RecentCallEntity>> = dao.getAll()

    /**
     * 记录一通来电。若 3 秒内已有同号码记录（多为另一识别链路写入），
     * 则用更丰富的信息原地更新，不新增行。
     */
    suspend fun record(
        number: String,
        digits: String,
        name: String?,
        description: String?,
        blocked: Boolean,
        spamType: String?
    ) {
        val now = System.currentTimeMillis()
        val last = dao.getLatest()
        if (last != null && last.digits == digits && (now - last.timestamp) < 3000) {
            // 同一通来电的二次写入：合并更丰富的信息
            dao.insert(
                last.copy(
                    number = number.ifBlank { last.number },
                    name = name ?: last.name,
                    description = description ?: last.description,
                    blocked = blocked || last.blocked,
                    spamType = spamType ?: last.spamType,
                    timestamp = now
                )
            )
            return
        }
        dao.insert(
            RecentCallEntity(
                number = number,
                digits = digits,
                name = name,
                description = description,
                blocked = blocked,
                spamType = spamType,
                timestamp = now
            )
        )
    }

    suspend fun clear() = dao.clear()

    /**
     * 尽力把骚扰标记写回系统通话记录（仅 Android 9 及以下的已授权设备；
     * Android 10+ 第三方应用写入通话记录被系统限制，直接跳过）。
     * 该操作不可靠且为可选增强，任何异常都被吞掉，绝不影响主流程。
     */
    suspend fun markSystemCallLog(digits: String, label: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_CALL_LOG)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        withContext(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply { put(Calls.CACHED_NAME, label) }
                val selection =
                    "${Calls._ID} = (SELECT MAX(${Calls._ID}) FROM ${CallLog.Calls._ID} WHERE ${Calls.NUMBER} = ?)"
                appContext.contentResolver.update(CallLog.Calls.CONTENT_URI, values, selection, arrayOf(digits))
            }
        }
    }
}
