package com.example.bmi_room


import androidx.room.*

@Dao
interface BMIDao {
    @Insert
    suspend fun insert(bmi: BMIEntity)

    @Update
    suspend fun update(bmi: BMIEntity)

    @Delete
    suspend fun delete(bmi: BMIEntity)

    @Query("SELECT * FROM bmi_table WHERE name = :name LIMIT 1")
    suspend fun queryByName(name: String): BMIEntity?

    @Query("SELECT * FROM bmi_table")
    suspend fun getAll(): List<BMIEntity>
}

