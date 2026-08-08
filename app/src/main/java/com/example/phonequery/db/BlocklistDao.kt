package com.example.phonequery.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlocklistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BlocklistEntity)

    @Delete
    suspend fun delete(entity: BlocklistEntity)

    @Query("SELECT * FROM blocklist WHERE isBlock = 1 ORDER BY createdAt DESC")
    fun getBlacklist(): Flow<List<BlocklistEntity>>

    @Query("SELECT * FROM blocklist WHERE isBlock = 0 ORDER BY createdAt DESC")
    fun getWhitelist(): Flow<List<BlocklistEntity>>

    @Query("SELECT * FROM blocklist ORDER BY createdAt DESC")
    fun getAll(): Flow<List<BlocklistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocklist WHERE isBlock = 1 AND :number LIKE number || '%')")
    suspend fun isBlacklisted(number: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM blocklist WHERE isBlock = 0 AND :number LIKE number || '%')")
    suspend fun isWhitelisted(number: String): Boolean

    @Query("SELECT * FROM blocklist WHERE number = :number LIMIT 1")
    suspend fun findByNumber(number: String): BlocklistEntity?

    @Query("SELECT COUNT(*) FROM blocklist WHERE isBlock = 1")
    fun countBlacklist(): Flow<Int>

    @Query("SELECT COUNT(*) FROM blocklist WHERE isBlock = 0")
    fun countWhitelist(): Flow<Int>
}