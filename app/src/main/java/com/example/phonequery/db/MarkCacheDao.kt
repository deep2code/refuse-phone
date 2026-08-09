package com.example.phonequery.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MarkCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MarkCacheEntity)

    @Query("SELECT * FROM mark_cache WHERE number = :number AND cacheType = 'MARK' LIMIT 1")
    suspend fun getMark(number: String): MarkCacheEntity?

    @Query("SELECT * FROM mark_cache WHERE number = :number AND cacheType = 'ENTERPRISE' LIMIT 1")
    suspend fun getEnterprise(number: String): MarkCacheEntity?

    @Query("SELECT COUNT(*) FROM mark_cache")
    suspend fun count(): Int

    @Query("SELECT * FROM mark_cache WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MarkCacheEntity?

    @Query("DELETE FROM mark_cache WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM mark_cache")
    suspend fun clearAll()
}
