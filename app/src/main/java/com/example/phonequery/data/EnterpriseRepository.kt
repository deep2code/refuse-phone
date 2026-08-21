package com.example.phonequery.data

import android.content.Context
import com.example.phonequery.model.EnterpriseInfo
import com.example.phonequery.model.LandlineLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 固话企业反查仓库（本地缓存优先）。
 *
 * - 默认零 key：先读本地标记缓存（断网可用），无缓存返回空结果。
 * - 原可选工商源（企查查 / 爱企查，key 门控）自 2026-08 起下线，
 *   企业反查统一走本地缓存（由外部网关写入的标记缓存兜底）。
 *
 * 注：原 tmini 聚合网关（电话邦数据源）因已停止注册/登录、无法获取 ckey，自 2026-08 起下线。
 */
class EnterpriseRepository(context: Context) {

    private val appContext: Context = context.applicationContext
    private val areaCodeHelper = AreaCodeHelper(context)
    private val markCacheRepository: MarkCacheRepository = MarkCacheRepository(context)

    /**
     * 对固话号码做企业反查：
     * 1. 解析区号得到城市；
     * 2. 读本地标记缓存（断网可用），无缓存返回空。
     */
    suspend fun querySimilarEnterprises(number: String): Pair<LandlineLocation?, List<EnterpriseInfo>> =
        withContext(Dispatchers.IO) {
            val landline = areaCodeHelper.parseLandline(number)
                ?: return@withContext Pair(null, emptyList())

            val digits = number.replace(Regex("\\D"), "")

            // 先查本地缓存（断网也能反查）
            val cachedNames = runCatching { markCacheRepository.getCachedEnterprise(digits) }.getOrNull()
            if (!cachedNames.isNullOrEmpty()) {
                return@withContext Pair(
                    landline,
                    cachedNames.map { EnterpriseInfo(name = it, source = "本地缓存") }
                )
            }
            Pair(landline, emptyList())
        }
}
