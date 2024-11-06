// TodoEntity.kt

package com.example.todo

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_table")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    var isCompleted: Boolean,
    val category: String,
    val date: String,
    val time: String
)