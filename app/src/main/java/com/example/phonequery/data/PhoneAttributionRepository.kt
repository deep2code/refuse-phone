package com.example.phonequery.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

/**
 * 可刷新的「手机号归属地」离线仓库（号段 → 省/市/运营商）。
 *
 * 与 [com.example.phonequery.data.PhoneRepository] 的 libphonenumber 离线解析互补：
 * - libphonenumber（geocoder + carrier）是零配置保底源，给到省/市级归属地与运营商，但粒度较粗；
 * - 本仓库使用由 scripts/fetch_phonedata.py 生成的 phonedata.db（来自 xluohome/phonedata 开源数据），
 *   按号段前 7 位精确匹配，且更新灵活——重新跑脚本、重新打包即可获得最新号段。
 *
 * 行为：
 * - 仅当 assets/phonedata.db 存在时启用（isEnabled=true），否则自动跳过，回落 libphonenumber 的结果；
 * - 首启动把 asset 拷贝到 databases/phonedata.db，之后只读查询；
 * - 查询按手机号前 7 位号段匹配（前缀 <= 目标 的最大记录），命中返回 省/市/运营商。
 */
class PhoneAttributionRepository(context: Context) {

    private val assetName = "phonedata.db"
    private val dbFile: File =
        File(context.getDatabasePath("phonedata.db").parent ?: context.filesDir.path, "phonedata.db")

    val isEnabled: Boolean
        get() = dbFile.exists()

    init {
        copyIfNeeded(context)
    }

    private fun copyIfNeeded(context: Context) {
        if (dbFile.exists()) return
        runCatching {
            context.assets.open(assetName).use { input ->
                FileOutputStream(dbFile).use { out -> input.copyTo(out) }
            }
        }
    }

    /**
     * 按手机号前 7 位查询归属地。
     * @return Triple(省份, 城市, 运营商)，未启用或未命中返回 null。
     */
    fun lookupAttribution(digitsRaw: String): Triple<String, String, String>? {
        if (!isEnabled) return null
        val digits = digitsRaw.replace(Regex("\\D"), "")
        // 仅对 11 位、1 开头的手机号做归属地查询
        if (digits.length != 11 || !digits.startsWith("1")) return null
        val prefix = digits.substring(0, 7).toIntOrNull() ?: return null

        return runCatching {
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                db.query(
                    "segments",
                    arrayOf("province", "city", "isp"),
                    "prefix <= ?",
                    arrayOf(prefix.toString()),
                    null,
                    null,
                    "prefix DESC",
                    "1"
                ).use { cur ->
                    if (cur.moveToFirst()) {
                        Triple(
                            cur.getString(0) ?: "",
                            cur.getString(1) ?: "",
                            cur.getString(2) ?: ""
                        )
                    } else null
                }
            } finally {
                db.close()
            }
        }.getOrNull()
    }

    /** 条数（用于设置页展示；未启用返回 0）。 */
    fun count(): Int {
        if (!isEnabled) return 0
        return runCatching {
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                db.rawQuery("SELECT COUNT(*) FROM segments", null).use { cur ->
                    if (cur.moveToFirst()) cur.getInt(0) else 0
                }
            } finally {
                db.close()
            }
        }.getOrDefault(0)
    }
}
