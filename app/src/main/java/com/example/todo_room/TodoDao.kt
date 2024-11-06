package com.example.todo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete

@Dao
interface TodoDao {
    @Insert
    suspend fun insert(todo: TodoEntity): Long // 返回插入的ID

    @Update
    suspend fun update(todo: TodoEntity)

    @Delete
    suspend fun delete(todo: TodoEntity)

    @Query("SELECT * FROM todo_table")
    suspend fun getAllTodos(): List<TodoEntity>

    @Query("SELECT * FROM todo_table WHERE title = :title LIMIT 1")
    suspend fun queryByTitle(title: String): TodoEntity?

    @Query("SELECT * FROM todo_table WHERE id = :id LIMIT 1")
    suspend fun getTodoById(id: Int): TodoEntity?

    @Query("SELECT * FROM todo_table ORDER BY id DESC LIMIT 1")
    suspend fun getLastInsertedTodo(): TodoEntity?
    @Query("SELECT * FROM todo_table WHERE category = :category")
    suspend fun queryByCategory(category: String): List<TodoEntity>
}