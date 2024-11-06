// EditTodoActivity.kt
package com.example.todo

import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class EditTodoActivity : AppCompatActivity() {

    private var todoId: Int = -1
    private var isEditMode: Boolean = false
    private lateinit var spinnerCategory: Spinner
    private lateinit var categories: MutableList<String>
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var database: TodoDatabase
    private lateinit var todoDao: TodoDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_todo)

        // 初始化資料庫和 DAO
        database = TodoDatabase.getDatabase(this)
        todoDao = database.todoDao()

        // 返回按鈕
        val btnBack: Button = findViewById(R.id.btn_back)
        btnBack.setOnClickListener {
            finish()
        }

        // 初始化 UI 元件
        val edTitle: EditText = findViewById(R.id.ed_title)
        val edDescription: EditText = findViewById(R.id.ed_description)
        val checkboxCompleted: CheckBox = findViewById(R.id.checkbox_completed)
        spinnerCategory = findViewById(R.id.spinner_category)
        val btnSelectDate: Button = findViewById(R.id.btn_select_date)
        val btnSelectTime: Button = findViewById(R.id.btn_select_time)
        val btnSave: Button = findViewById(R.id.btn_save)
        val btnManageCategories: Button = findViewById(R.id.btn_manage_categories) // 新增管理類別按鈕

        // 載入類別列表
        categories = loadCategories()

        // 確保「新增類別」和「未分類」選項存在
        if (!categories.contains("新增類別")) {
            categories.add("新增類別")
        }
        if (!categories.contains("未分類")) {
            categories.add(0, "未分類") // 將「未分類」放在第一個位置
        }

        // 設定 Spinner 的 Adapter
        adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter

        // 設定 Spinner 的選擇監聽器
        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedCategory = categories[position]
                if (selectedCategory == "新增類別") {
                    showAddCategoryDialog()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // 不需處理
            }
        }

        // 設定「管理類別」按鈕的點擊監聽器
        btnManageCategories.setOnClickListener {
            showManageCategoriesDialog()
        }

        // 檢查是否有傳遞待辦事項資料
        todoId = intent.getIntExtra("TODO_ID", -1)
        isEditMode = todoId != -1

        if (isEditMode) {
            // 預填待辦事項資料
            edTitle.setText(intent.getStringExtra("TITLE"))
            edDescription.setText(intent.getStringExtra("DESCRIPTION"))
            checkboxCompleted.isChecked = intent.getBooleanExtra("COMPLETED", false)
            val category = intent.getStringExtra("CATEGORY") ?: "未分類"

            // 如果類別列表中沒有該類別，則添加進列表
            if (!categories.contains(category)) {
                categories.add(categories.size - 1, category)
                adapter.notifyDataSetChanged()
                saveCategories(categories.filter { it != "新增類別" && it != "未分類" })
            }

            spinnerCategory.setSelection(categories.indexOf(category))
            btnSelectDate.text = intent.getStringExtra("DATE") ?: "選擇日期"
            btnSelectTime.text = intent.getStringExtra("TIME") ?: "選擇時間"
        } else {
            // 預設選擇第一個類別
            spinnerCategory.setSelection(0)
        }

        // 日期選擇器
        btnSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val date = String.format("%04d/%02d/%02d", selectedYear, selectedMonth + 1, selectedDay)
                btnSelectDate.text = date
            }, year, month, day)
            datePickerDialog.show()
        }

        // 時間選擇器
        btnSelectTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)

            val timePickerDialog = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                val time = String.format("%02d:%02d", selectedHour, selectedMinute)
                btnSelectTime.text = time
            }, hour, minute, true)
            timePickerDialog.show()
        }

        // 保存按鈕點擊事件
        btnSave.setOnClickListener {
            val title = edTitle.text.toString()
            val description = edDescription.text.toString()
            val isCompleted = checkboxCompleted.isChecked
            val category = spinnerCategory.selectedItem.toString()
            val date = btnSelectDate.text.toString()
            val time = btnSelectTime.text.toString()

            if (title.isBlank() || description.isBlank()) {
                Toast.makeText(this, "請輸入完整的標題和描述", Toast.LENGTH_SHORT).show()
            } else {
                val resultIntent = intent
                resultIntent.putExtra("TITLE", title)
                resultIntent.putExtra("DESCRIPTION", description)
                resultIntent.putExtra("COMPLETED", isCompleted)
                resultIntent.putExtra("CATEGORY", category)
                resultIntent.putExtra("DATE", date)
                resultIntent.putExtra("TIME", time)

                // 傳遞待辦事項的 ID（如果是編輯模式）
                if (isEditMode) {
                    resultIntent.putExtra("TODO_ID", todoId)
                }

                setResult(RESULT_OK, resultIntent)

                // 安排通知
                scheduleNotification(title, description, date, time)

                finish()
            }
        }

        // 建立通知渠道
        createNotificationChannel()
    }

    // 將 dp 轉換為像素的輔助函數
    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    // 設置圓角背景的輔助函數
    private fun setRoundedCorners(dialog: AlertDialog, cornerRadiusDp: Float) {
        val cornerRadiusPx = dpToPx(cornerRadiusDp)
        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx // 設置圓角半徑（以像素為單位）
            setColor(ContextCompat.getColor(this@EditTodoActivity, android.R.color.white)) // 設置背景顏色
            setStroke(2, ContextCompat.getColor(this@EditTodoActivity, android.R.color.darker_gray)) // 可選：設置邊框
        }
        dialog.window?.setBackgroundDrawable(backgroundDrawable)
    }

    private fun showAddCategoryDialog() {
        val editText = EditText(this)
        editText.hint = "請輸入新的類別名稱"

        // 設置 Padding（例如：16dp）
        val paddingDp = 16f
        val paddingPx = dpToPx(paddingDp).toInt()
        editText.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)

        // 建立 AlertDialog
        val builder = AlertDialog.Builder(this)
            .setTitle("新增類別")
            .setView(editText)
            .setPositiveButton("確認") { dialogInterface, _ ->
                val newCategory = editText.text.toString().trim()
                if (newCategory.isNotEmpty() && !categories.contains(newCategory)) {
                    // 在「新增類別」選項之前插入新類別
                    categories.add(categories.size - 1, newCategory)
                    // 保存到 SharedPreferences
                    saveCategories(categories.filter { it != "新增類別" && it != "未分類" })
                    adapter.notifyDataSetChanged()
                    // 設定 Spinner 選擇新加入的類別
                    spinnerCategory.setSelection(categories.indexOf(newCategory))
                } else {
                    Toast.makeText(this, "類別名稱無效或已存在", Toast.LENGTH_SHORT).show()
                    // 如果類別無效，將 Spinner 選擇設為第一個有效類別
                    spinnerCategory.setSelection(0)
                }
                dialogInterface.dismiss()
            }
            .setNegativeButton("取消") { dialogInterface, _ ->
                dialogInterface.dismiss()
                // 如果使用者取消，將 Spinner 選擇設為第一個有效類別
                spinnerCategory.setSelection(0)
            }
            .setCancelable(false)

        // 創建並顯示 AlertDialog
        val dialog = builder.create()
        dialog.show()

        // 設置圓角背景
        setRoundedCorners(dialog, 20f) // 設置圓角半徑為 20dp
    }

    private fun showManageCategoriesDialog() {
        // 獲取當前的類別列表，排除「新增類別」和「未分類」
        val deletableCategories = categories.filter { it != "新增類別" && it != "未分類" }

        if (deletableCategories.isEmpty()) {
            Toast.makeText(this, "沒有可刪除的類別", Toast.LENGTH_SHORT).show()
            return
        }

        // 將類別轉換為陣列
        val categoriesArray = deletableCategories.toTypedArray()

        // 使用多選列表顯示類別
        val selectedItems = BooleanArray(categoriesArray.size) { false }

        // 建立 AlertDialog
        val builder = AlertDialog.Builder(this)
            .setTitle("刪除類別")
            .setMultiChoiceItems(categoriesArray, selectedItems) { _, which, isChecked ->
                selectedItems[which] = isChecked
            }
            .setPositiveButton("刪除") { dialogInterface, _ ->
                val categoriesToDelete = deletableCategories.filterIndexed { index, _ -> selectedItems[index] }
                if (categoriesToDelete.isEmpty()) {
                    Toast.makeText(this, "請選擇要刪除的類別", Toast.LENGTH_SHORT).show()
                } else {
                    // 確認刪除
                    val confirmDialog = AlertDialog.Builder(this)
                        .setTitle("確認刪除")
                        .setMessage("確定要刪除選定的類別嗎？")
                        .setPositiveButton("確定") { confirmDialogInterface, _ ->
                            deleteCategories(categoriesToDelete)
                            confirmDialogInterface.dismiss()
                        }
                        .setNegativeButton("取消") { confirmDialogInterface, _ ->
                            confirmDialogInterface.dismiss()
                        }
                        .create()

                    // 顯示確認刪除對話框
                    confirmDialog.show()

                    // 設置圓角背景
                    setRoundedCorners(confirmDialog, 20f) // 設置圓角半徑為 20dp
                }
                dialogInterface.dismiss()
            }
            .setNegativeButton("取消") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }

        // 創建並顯示 AlertDialog
        val dialog = builder.create()
        dialog.show()

        // 設置圓角背景
        setRoundedCorners(dialog, 20f) // 設置圓角半徑為 20dp
    }

    private fun deleteCategories(categoriesToDelete: List<String>) {
        // 檢查是否有待辦事項使用這些類別
        lifecycleScope.launch {
            val usedCategories = mutableListOf<String>()
            for (category in categoriesToDelete) {
                val todosWithCategory = todoDao.queryByCategory(category)
                if (todosWithCategory.isNotEmpty()) {
                    usedCategories.add(category)
                }
            }

            if (usedCategories.isNotEmpty()) {
                // 顯示警告，告知用戶哪些類別正在被使用
                val builder = AlertDialog.Builder(this@EditTodoActivity)
                    .setTitle("刪除失敗")
                    .setMessage("以下類別正在被使用，無法刪除：\n${usedCategories.joinToString(", ")}")
                    .setPositiveButton("確定") { dialogInterface, _ ->
                        dialogInterface.dismiss()
                    }

                val warningDialog = builder.create()
                warningDialog.show()

                // 設置圓角背景
                setRoundedCorners(warningDialog, 20f) // 設置圓角半徑為 20dp
            } else {
                // 執行刪除
                categories.removeAll(categoriesToDelete)
                saveCategories(categories.filter { it != "新增類別" && it != "未分類" })

                // 更新 Spinner 的 Adapter
                adapter.notifyDataSetChanged()

                Toast.makeText(this@EditTodoActivity, "類別已刪除", Toast.LENGTH_SHORT).show()

                // 如果當前選中的類別被刪除，重置選擇
                val currentSelection = spinnerCategory.selectedItem.toString()
                if (currentSelection in categoriesToDelete) {
                    spinnerCategory.setSelection(0)
                }
            }
        }
    }

    // 載入類別列表
    private fun loadCategories(): MutableList<String> {
        val sharedPrefs = getSharedPreferences("todo_prefs", Context.MODE_PRIVATE)
        val categoriesSet = sharedPrefs.getStringSet("categories", null)
        val categories = if (categoriesSet != null) {
            categoriesSet.toMutableList()
        } else {
            mutableListOf("工作", "學習", "生活", "其他")
        }
        // 確保添加「新增類別」和「未分類」選項
        if (!categories.contains("新增類別")) {
            categories.add("新增類別")
        }
        if (!categories.contains("未分類")) {
            categories.add(0, "未分類") // 將「未分類」放在第一個位置
        }
        return categories
    }

    // 保存類別列表
    private fun saveCategories(categoriesToSave: List<String>) {
        val sharedPrefs = getSharedPreferences("todo_prefs", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        // 過濾掉「新增類別」和「未分類」選項
        val categoriesFiltered = categoriesToSave.filter { it != "新增類別" && it != "未分類" }
        editor.putStringSet("categories", categoriesFiltered.toSet())
        editor.apply()
    }

    private fun scheduleNotification(title: String, description: String, date: String, time: String) {
        // 將日期和時間轉換為毫秒值
        val datetimeInMillis = convertToMillis(date, time)
        if (datetimeInMillis != null && datetimeInMillis > System.currentTimeMillis()) {
            val data = Data.Builder()
                .putString("TITLE", title)
                .putString("DESCRIPTION", description)
                .build()

            val delay = datetimeInMillis - System.currentTimeMillis()

            val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()

            WorkManager.getInstance(this).enqueue(workRequest)
        } else {
            Toast.makeText(this, "請選擇未來的日期和時間以安排通知", Toast.LENGTH_SHORT).show()
        }
    }

    private fun convertToMillis(date: String, time: String): Long? {
        val datetimeString = "$date $time"
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        return try {
            val date = sdf.parse(datetimeString)
            date?.time
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }
}