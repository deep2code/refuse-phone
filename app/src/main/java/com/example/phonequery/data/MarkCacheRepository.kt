package com.example.phonequery.data

import android.content.Context
import com.example.phonequery.db.AppDatabase
import com.example.phonequery.db.MarkCacheEntity
import com.example.phonequery.model.EnterpriseInfo
import com.example.phonequery.model.NumberType
import com.example.phonequery.model.PhoneInfo
import com.example.phonequery.model.PlatformMark
import com.example.phonequery.model.ResultSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 本地标记缓存仓库（零 key）。
 *
 * 把每次 tmini 免费网关查到的号码标记 / 固话企业反查结果落库，
 * 形成「越用越准的个人内置标记库」——断网或接口失效时仍可标记。
 */
class MarkCacheRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).markCacheDao()
    private val gson = Gson()

    /** 缓存有效期：90 天。过期后视为未命中，重新在线查询。 */
    private val ttlMs = 90L * 24 * 60 * 60 * 1000

    /** 读取缓存的号码标记；过期或不存在返回 null */
    suspend fun getCachedMark(digits: String): PhoneInfo? {
        val e = dao.getMark(digits) ?: return null
        if (System.currentTimeMillis() - e.updatedAt > ttlMs) return null
        val marks: List<PlatformMark> = if (!e.marksJson.isNullOrBlank()) {
            runCatching {
                gson.fromJson<List<PlatformMark>>(e.marksJson, object : TypeToken<List<PlatformMark>>() {}.type)
            }.getOrElse { emptyList() }
        } else emptyList()
        return PhoneInfo(
            number = digits,
            numberType = NumberType.UNKNOWN,
            province = e.province,
            city = e.city,
            carrier = e.carrier,
            spamType = e.spamType,
            spamCount = e.spamCount,
            platformMarks = marks,
            source = ResultSource.CACHED,
            fromCache = true
        )
    }

    /** 保存号码标记到缓存（仅当确有标记数据时才写，避免用空结果覆盖旧缓存） */
    suspend fun saveMark(digits: String, info: PhoneInfo) {
        val hasData = !info.spamType.isNullOrBlank() || info.platformMarks.isNotEmpty()
        if (!hasData) return
        val toCache = info.copy(
            platformMarks = info.platformMarks.filter { it.platform != "本地号段库" }
        )
        if (toCache.spamType.isNullOrBlank() && toCache.platformMarks.isEmpty()) return
        runCatching {
            dao.upsert(
                MarkCacheEntity(
                    id = "${digits}_MARK",
                    number = digits,
                    cacheType = "MARK",
                    province = info.province,
                    city = info.city,
                    carrier = info.carrier,
                    spamType = info.spamType,
                    spamCount = info.spamCount,
                    marksJson = gson.toJson(toCache.platformMarks)
                )
            )
        }
    }

    /** 读取缓存的固话企业名称列表；过期或不存在返回 null */
    suspend fun getCachedEnterprise(digits: String): List<String>? {
        val e = dao.getEnterprise(digits) ?: return null
        if (System.currentTimeMillis() - e.updatedAt > ttlMs) return null
        return if (!e.enterpriseJson.isNullOrBlank()) {
            runCatching {
                gson.fromJson<List<String>>(e.enterpriseJson, object : TypeToken<List<String>>() {}.type)
            }.getOrNull()
        } else null
    }

    /** 保存固话企业名称列表到缓存 */
    suspend fun saveEnterprise(digits: String, names: List<String>) {
        if (names.isEmpty()) return
        runCatching {
            dao.upsert(
                MarkCacheEntity(
                    id = "${digits}_ENTERPRISE",
                    number = digits,
                    cacheType = "ENTERPRISE",
                    enterpriseJson = gson.toJson(names)
                )
            )
        }
    }

    /** 缓存条目数 */
    suspend fun count(): Int = runCatching { dao.count() }.getOrDefault(0)

    /**
     * 主动标记某个号码（用户手动标记）。
     * 使用独立的 USERMARK 缓存类型，不与在线标记（MARK）互相覆盖。
     * spamType 为标记内容，如「骚扰 / 诈骗 / 广告营销 / 正常 / 其他」。
     */
    suspend fun markNumber(digits: String, spamType: String) {
        val clean = digits.replace(Regex("[^0-9]"), "")
        if (clean.isBlank() || spamType.isBlank()) return
        runCatching {
            dao.upsert(
                MarkCacheEntity(
                    id = "${clean}_USERMARK",
                    number = clean,
                    cacheType = "USERMARK",
                    spamType = spamType
                )
            )
        }
    }

    /** 读取用户对某号码的主动标记（无则返回 null） */
    suspend fun getUserMark(digits: String): String? {
        val clean = digits.replace(Regex("[^0-9]"), "")
        if (clean.isBlank()) return null
        return runCatching { dao.getById("${clean}_USERMARK")?.spamType }.getOrNull()
    }

    /** 清除用户对某号码的主动标记 */
    suspend fun clearUserMark(digits: String) {
        val clean = digits.replace(Regex("[^0-9]"), "")
        if (clean.isBlank()) return
        runCatching { dao.deleteById("${clean}_USERMARK") }
    }

    /** 清空全部标记缓存 */
    suspend fun clearAll() = runCatching { dao.clearAll() }
}
