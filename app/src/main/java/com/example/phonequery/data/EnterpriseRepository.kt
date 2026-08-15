package com.example.phonequery.data

import android.content.Context
import com.example.phonequery.data.source.AiqichaSource
import com.example.phonequery.data.source.EnterpriseSource
import com.example.phonequery.data.source.EnterpriseSourceResult
import com.example.phonequery.data.source.QccSource
import com.example.phonequery.model.EnterpriseInfo
import com.example.phonequery.model.LandlineLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 固话企业反查仓库（本地缓存优先 + 可选工商源增强）
 *
 * - 默认零 key：先读本地标记缓存（断网可用），无缓存再走可选工商源。
 * - 可选增强（key 门控）：企查查 / 爱企查，按电话号码反查公司并补充「所属行业 / 法人 / 状态」。
 *
 * 注：原 tmini 聚合网关（电话邦数据源）因已停止注册/登录、无法获取 ckey，自 2026-08 起下线。
 */
class EnterpriseRepository(context: Context) {

    private val appContext: Context = context.applicationContext
    private val areaCodeHelper = AreaCodeHelper(context)
    private val markCacheRepository: MarkCacheRepository = MarkCacheRepository(context)

    /** 可选企业源；全部 key 缺失时为空列表，走纯零 key 流程。 */
    private val enterpriseSources: List<EnterpriseSource> = listOfNotNull(
        QccSource().takeIf { it.isEnabled },
        AiqichaSource().takeIf { it.isEnabled }
    )

    /**
     * 对固话号码做企业反查：
     * 1. 解析区号得到城市；
     * 2. 先读本地缓存（断网可用）；
     * 3. 受「在线查询开关」约束：开启时再走可选工商源（企查查/爱企查）按电话反查公司。
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

            // 隐私保护：受「在线查询开关」约束。用户关闭在线查询时，不把固话号码发给
            // 第三方网关（可选工商源），仅返回本地缓存结果。
            val settings = try {
                SettingsDataStore(appContext).settingsFlow.first()
            } catch (_: Exception) {
                null
            }
            val onlineEnabled = settings?.enableOnlineLookup ?: false
            if (!onlineEnabled) {
                return@withContext Pair(landline, emptyList())
            }

            // 用可选工商源（企查查/爱企查，需配置 key）按电话反查公司
            val enterprises = lookupFromSources(digits)?.let { listOf(it) } ?: emptyList()

            if (enterprises.isNotEmpty()) {
                runCatching { markCacheRepository.saveEnterprise(digits, enterprises.map { it.name }) }
            }
            Pair(landline, enterprises)
        }

    /** 用可选企业源直接按电话反查公司。 */
    private suspend fun lookupFromSources(digits: String): EnterpriseInfo? {
        for (src in enterpriseSources) {
            if (!src.isEnabled) continue
            val r: EnterpriseSourceResult = runCatching { src.lookup(digits) }.getOrNull() ?: continue
            if (!r.company.isNullOrBlank()) {
                return EnterpriseInfo(
                    name = r.company,
                    industry = r.industry,
                    legalPerson = r.legalPerson,
                    status = r.status,
                    source = r.sourceName
                )
            }
        }
        return null
    }
}
