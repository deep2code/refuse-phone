package com.example.phonequery.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SpamHashDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<SpamHashEntity>)

    @Query("SELECT * FROM spam_hash WHERE id = :hash LIMIT 1")
    suspend fun getByHash(hash: String): SpamHashEntity?

    @Query("SELECT COUNT(*) FROM spam_hash")
    suspend fun count(): Int

    @Query("DELETE FROM spam_hash")
    suspend fun clearAll()
}
