package com.example.bmi_room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bmi_table")
data class BMIEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val gender: String,
    val height: Float,
    val weight: Float,
    val bmi: Float
)