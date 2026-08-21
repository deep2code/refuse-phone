package com.example.phonequery.data

import android.content.Context
import com.example.phonequery.model.LandlineLocation
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 中国固话区号解析器
 */
class AreaCodeHelper(context: Context) {

    private val areaCodeList: List<AreaCodeEntry> by lazy {
        loadAreaCodes(context)
    }

    private val areaCodeMap: Map<String, AreaCodeEntry> by lazy {
        areaCodeList.associateBy { it.code }
    }

    /**
     * 从固话号码中解析区号和本地号。
     * 支持带 0 前缀的区号，如 057156264805。
     */
    fun parseLandline(number: String): LandlineLocation? {
        val digits = number.replace(NON_DIGIT_REGEX, "")
        if (digits.length < 8) return null

        // 尝试 4 位、3 位、2 位区号匹配
        val prefixes = listOf(
            digits.take(4),
            digits.take(3),
            digits.take(2)
        )

        for (prefix in prefixes) {
            val entry = areaCodeMap[prefix]
            if (entry != null) {
                return LandlineLocation(
                    areaCode = prefix,
                    city = entry.city,
                    province = entry.province,
                    localNumber = digits.substring(prefix.length)
                )
            }
        }
        return null
    }

    /**
     * 生成与目标固话「相似」的号码列表。
     * 相似规则：同一区号下，本地号前 3~4 位相同的相邻号码。
     */
    fun generateSimilarNumbers(
        landline: LandlineLocation,
        prefixLength: Int = 4,
        neighborCount: Int = 4
    ): List<String> {
        val local = landline.localNumber
        if (local.length <= prefixLength) return emptyList()

        val prefix = local.take(prefixLength)
        val base = prefix.padEnd(local.length, '0').toLongOrNull() ?: return emptyList()
        val result = mutableListOf<String>()

        for (offset in -neighborCount..neighborCount) {
            if (offset == 0) continue
            val similarLocal = (base + offset).toString().padStart(local.length, '0')
            result.add(landline.areaCode + similarLocal)
        }
        return result
    }

    private fun loadAreaCodes(context: Context): List<AreaCodeEntry> {
        return try {
            val json = context.assets.open("area_code.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<AreaCodeEntry>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private data class AreaCodeEntry(
        val code: String,
        val city: String,
        val pinyin: String,
        val province: String? = null
    )
}
