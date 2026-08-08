package com.example.phonequery.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CodeNumberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<CodeNumberEntity>)

    @Query("SELECT * FROM code_number")
    suspend fun getAll(): List<CodeNumberEntity>

    @Query("SELECT COUNT(*) FROM code_number")
    suspend fun count(): Int

    @Query("DELETE FROM code_number")
    suspend fun clearAll()
}
