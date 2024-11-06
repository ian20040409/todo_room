// MainActivity.kt

package com.example.todo

import androidx.appcompat.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import android.Manifest

class MainActivity : AppCompatActivity() {

    private lateinit var database: TodoDatabase
    private lateinit var todoDao: TodoDao
    private lateinit var listView: ListView
    private lateinit var listAdapter: ArrayAdapter<String>
    private val todoList = mutableListOf<String>()
    private val todoEntities = mutableListOf<TodoEntity>()

    // 定義 ActivityResultLauncher，用於從 EditTodoActivity 接收返回的資料
    private val editTodoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val todoId = data?.getIntExtra("TODO_ID", -1) ?: -1
            val title = data?.getStringExtra("TITLE") ?: ""
            val description = data?.getStringExtra("DESCRIPTION") ?: ""
            val isCompleted = data?.getBooleanExtra("COMPLETED", false) ?: false
            val category = data?.getStringExtra("CATEGORY") ?: "未分類"
            val date = data?.getStringExtra("DATE") ?: "未設定"
            val time = data?.getStringExtra("TIME") ?: "未設定"

            val newTodo = TodoEntity(
                id = if (todoId != -1) todoId else 0,
                title = title,
                description = description,
                isCompleted = isCompleted,
                category = category,
                date = date,
                time = time
            )

            lifecycleScope.launch {
                if (todoId != -1) {
                    // 編輯模式，更新資料
                    todoDao.update(newTodo)
                    showToast("更新成功")
                } else {
                    // 新增模式，插入新資料
                    todoDao.insert(newTodo)
                    showToast("新增成功")
                }
                loadAllTodos() // 更新列表
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createNotificationChannel()
        requestNotificationPermission()

        // 初始化資料庫和 ListView
        database = TodoDatabase.getDatabase(this)
        todoDao = database.todoDao()
        listView = findViewById(R.id.todo_list)

        val emptyView = findViewById<TextView>(R.id.empty_view)
        listView.emptyView = emptyView

        // 初始化 ListView 的 Adapter
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, todoList)
        listView.adapter = listAdapter

        // 新增按鈕 - 啟動 EditTodoActivity 進行新增
        findViewById<Button>(R.id.btn_insert).setOnClickListener {
            val intent = Intent(this, EditTodoActivity::class.java)
            editTodoLauncher.launch(intent)
        }

        // 設定 ListView 的長按監聽器，用於編輯待辦事項
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val todoEntity = todoEntities[position]
            // 啟動 EditTodoActivity，並傳遞待辦事項資料
            val intent = Intent(this, EditTodoActivity::class.java).apply {
                putExtra("TODO_ID", todoEntity.id)
                putExtra("TITLE", todoEntity.title)
                putExtra("DESCRIPTION", todoEntity.description)
                putExtra("COMPLETED", todoEntity.isCompleted)
                putExtra("CATEGORY", todoEntity.category)
                putExtra("DATE", todoEntity.date)
                putExtra("TIME", todoEntity.time)
            }
            editTodoLauncher.launch(intent)
            true
        }

        // 設定 ListView 的點擊監聽器，用於標記待辦事項已完成
        listView.setOnItemClickListener { _, _, position, _ ->
            val todoEntity = todoEntities[position]
            val status = if (todoEntity.isCompleted) "未完成" else "已完成"
            val message = "是否將待辦事項「${todoEntity.title}」標記為$status？"

            val builder = MaterialAlertDialogBuilder(this)
                .setTitle("更新狀態")
                .setMessage(message)
                .setPositiveButton("確定") { dialog, _ ->
                    // 創建新的實例，更新 isCompleted
                    val updatedTodo = todoEntity.copy(isCompleted = !todoEntity.isCompleted)
                    lifecycleScope.launch {
                        todoDao.update(updatedTodo)
                        loadAllTodos()
                        showToast("狀態已更新")
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("取消") { dialog, _ ->
                    dialog.dismiss()
                }

            // 創建並顯示圓角 AlertDialog
            val dialog = builder.create()
            dialog.show()
            setRoundedCorners(dialog, 30f) // 設置圓角半徑為 80dp
        }

        // 刪除按鈕 - 彈出對話框詢問要刪除的待辦事項標題
        findViewById<Button>(R.id.btn_del).setOnClickListener {
            showDeleteDialog()
        }

        // 查詢按鈕 - 顯示搜尋對話框
        findViewById<Button>(R.id.btn_query).setOnClickListener {
            showSearchDialog()
        }

        // 重新整理按鈕 - 點擊後重新載入所有待辦事項
        findViewById<Button>(R.id.btn_refresh).setOnClickListener {
            loadAllTodos()
            showToast("已重新整理")
        }

        // 載入所有待辦事項
        loadAllTodos()
    }

    // 從資料庫中載入所有待辦事項
    private fun loadAllTodos() {
        lifecycleScope.launch {
            val allTodos = todoDao.getAllTodos()
            updateListView(allTodos)
        }
    }

    // 更新 ListView 的函數
    private fun updateListView(entities: List<TodoEntity>) {
        todoList.clear()
        todoEntities.clear()
        entities.forEach { entity ->
            todoList.add(entityToString(entity))
            todoEntities.add(entity)
        }
        listAdapter.notifyDataSetChanged()
    }

    // 將 TodoEntity 轉為單行字串表示
    private fun entityToString(entity: TodoEntity): String {
        return "標題: ${entity.title}\n" +
                "描述: ${entity.description}\n" +
                "類別: ${entity.category}\n" +
                "日期: ${entity.date} ${entity.time}\n" +
                "已完成: ${if (entity.isCompleted) "是" else "否"}"
    }

    // 顯示浮動訊息的函數
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // 顯示刪除彈出視窗
    private fun showDeleteDialog() {
        val editText = EditText(this)
        editText.hint = "請輸入要刪除的待辦事項標題"


        // 設置 Padding（例如：16dp）
        val paddingDp = 19f
        val paddingPx = dpToPx(paddingDp).toInt()
        editText.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)


        val builder = MaterialAlertDialogBuilder(this)
            .setTitle("刪除待辦事項")
            .setView(editText)
            .setPositiveButton("刪除") { dialog, _ ->
                val title = editText.text.toString()
                if (title.isNotBlank()) {
                    deleteTodoByTitle(title)
                } else {
                    showToast("請輸入有效的標題")
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }

        // 創建並顯示圓角 AlertDialog
        val dialog = builder.create()
        dialog.show()
        setRoundedCorners(dialog, 30f) // 設置圓角半徑為 80dp
    }

    // 根據標題刪除待辦事項
    private fun deleteTodoByTitle(title: String) {
        lifecycleScope.launch {
            val entity = todoDao.queryByTitle(title)
            if (entity != null) {
                todoDao.delete(entity)
                showToast("待辦事項已刪除")
                loadAllTodos() // 更新列表
            } else {
                showToast("找不到該待辦事項")
            }
        }
    }

    // 顯示搜尋彈出視窗
    private fun showSearchDialog() {
        val editText = EditText(this)
        editText.hint = "請輸入待辦事項標題"

        // 設置 Padding（例如：16dp）
        val paddingDp = 19f
        val paddingPx = dpToPx(paddingDp).toInt()
        editText.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle("搜尋待辦事項")
            .setView(editText)
            .setPositiveButton("搜尋") { dialog, _ ->
                val title = editText.text.toString()
                if (title.isNotBlank()) {
                    searchTodoByTitle(title)
                } else {
                    showToast("請輸入有效的標題")
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }

        // 創建並顯示圓角 AlertDialog
        val dialog = builder.create()
        dialog.show()
        setRoundedCorners(dialog, 30f) // 設置圓角半徑為 80dp
    }

    // 查詢特定標題的待辦事項
    private fun searchTodoByTitle(title: String) {
        lifecycleScope.launch {
            val entity = todoDao.queryByTitle(title)
            if (entity != null) {
                updateListView(listOf(entity))
            } else {
                showToast("找不到該待辦事項")
                loadAllTodos() // 如果找不到，仍然載入所有待辦事項
            }
        }
    }

    // 創建通知渠道
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "待辦事項提醒"
            val descriptionText = "待辦事項時間到達時的通知提醒"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("todo_reminder_channel", name, importance).apply {
                description = descriptionText
            }
            // 註冊渠道
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 請求通知權限（適用於 Android 13+）
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    // 設置圓角背景的輔助函數
    private fun setRoundedCorners(dialog: AlertDialog, cornerRadiusDp: Float) {
        val cornerRadiusPx = dpToPx(cornerRadiusDp)
        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx // 設置圓角半徑（以像素為單位）
            setColor(ContextCompat.getColor(this@MainActivity, android.R.color.white)) // 設置背景顏色
            setStroke(2, ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray)) // 可選：設置邊框
        }
        dialog.window?.setBackgroundDrawable(backgroundDrawable)
    }

    // 將 dp 轉換為像素的輔助函數
    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}