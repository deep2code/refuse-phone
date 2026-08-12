package com.example.phonequery.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BlocklistEntity::class, MarkCacheEntity::class, SpamHashEntity::class, CodeNumberEntity::class, RecentCallEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blocklistDao(): BlocklistDao
    abstract fun markCacheDao(): MarkCacheDao
    abstract fun spamHashDao(): SpamHashDao
    abstract fun codeNumberDao(): CodeNumberDao
    abstract fun recentCallDao(): RecentCallDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** 从版本 1 升级：新增 type / label 字段，旧数据默认视为精确号码 */
        private val MIGRATION_1_2 = Migration(1, 2) {
            it.execSQL("ALTER TABLE blocklist ADD COLUMN type TEXT NOT NULL DEFAULT 'EXACT'")
            it.execSQL("ALTER TABLE blocklist ADD COLUMN label TEXT NOT NULL DEFAULT ''")
        }

        /** 从版本 2 升级：新增 mark_cache 本地标记缓存表 */
        private val MIGRATION_2_3 = Migration(2, 3) {
            it.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `mark_cache` (
                    `id` TEXT NOT NULL,
                    `number` TEXT NOT NULL,
                    `cacheType` TEXT NOT NULL,
                    `province` TEXT,
                    `city` TEXT,
                    `carrier` TEXT,
                    `spamType` TEXT,
                    `spamCount` TEXT,
                    `marksJson` TEXT,
                    `enterpriseJson` TEXT,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }

        /** 从版本 3 升级：新增 spam_hash 社区骚扰号码哈希表（md5 离线匹配） */
        private val MIGRATION_3_4 = Migration(3, 4) {
            it.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `spam_hash` (
                    `id` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }

        /** 从版本 4 升级：新增 code_number 工信部码号资源离线表（95/96/106/400/800 号段→使用单位） */
        private val MIGRATION_4_5 = Migration(4, 5) {
            it.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `code_number` (
                    `id` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `owner` TEXT NOT NULL,
                    `purpose` TEXT,
                    `valid_until` TEXT,
                    `note` TEXT,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }

        /** 从版本 5 升级：新增 recent_call 最近来电表（来电识别结果本地留痕） */
        private val MIGRATION_5_6 = Migration(5, 6) {
            it.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recent_call` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `number` TEXT NOT NULL,
                    `digits` TEXT NOT NULL DEFAULT '',
                    `name` TEXT,
                    `description` TEXT,
                    `blocked` INTEGER NOT NULL DEFAULT 0,
                    `spamType` TEXT,
                    `timestamp` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "phone_query.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
