package com.example.phonequery.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentCallDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecentCallEntity)

    @Query("SELECT * FROM recent_call ORDER BY timestamp DESC LIMIT 200")
    fun getAll(): Flow<List<RecentCallEntity>>

    @Query("SELECT * FROM recent_call ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): RecentCallEntity?

    @Query("SELECT COUNT(*) FROM recent_call")
    fun count(): Flow<Int>

    @Query("DELETE FROM recent_call")
    suspend fun clear()
}
