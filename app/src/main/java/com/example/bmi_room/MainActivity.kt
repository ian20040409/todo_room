package com.example.bmi_room
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var database: BMIDatabase
    private lateinit var bmiDao: BMIDao
    private lateinit var listView: ListView
    private lateinit var listAdapter: ArrayAdapter<String>
    private val bmiList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化資料庫和 ListView
        database = BMIDatabase.getDatabase(this)
        bmiDao = database.bmiDao()
        listView = findViewById(R.id.bmi_list)

        // 初始化 ListView 的 Adapter
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, bmiList)
        listView.adapter = listAdapter

        // 新增按鈕
        findViewById<Button>(R.id.btn_insert).setOnClickListener {
            val entity = getInputData() ?: return@setOnClickListener
            lifecycleScope.launch {
                bmiDao.insert(entity)
                showToast("新增成功")
                updateListView(listOf(entity))
                clearInputFields()
            }
        }

        // 修改按鈕
        findViewById<Button>(R.id.btn_update).setOnClickListener {
            val name = findViewById<EditText>(R.id.ed_name).text.toString()
            lifecycleScope.launch {
                val entity = bmiDao.queryByName(name)
                if (entity != null) {
                    val updatedEntity = getInputData()?.copy(id = entity.id) ?: return@launch
                    bmiDao.update(updatedEntity)
                    showToast("更新成功")
                    updateListView(listOf(updatedEntity))
                    clearInputFields()
                } else {
                    showToast("找不到該使用者資料")
                }
            }
        }

        // 刪除按鈕
        findViewById<Button>(R.id.btn_del).setOnClickListener {
            val name = findViewById<EditText>(R.id.ed_name).text.toString()
            lifecycleScope.launch {
                val entity = bmiDao.queryByName(name)
                if (entity != null) {
                    bmiDao.delete(entity)
                    showToast("資料已刪除")
                    updateListView(emptyList()) // 清空 ListView
                    clearInputFields()
                } else {
                    showToast("找不到該使用者資料")
                }
            }
        }

        // 查詢按鈕
        findViewById<Button>(R.id.btn_query).setOnClickListener {
            val name = findViewById<EditText>(R.id.ed_name).text.toString()
            lifecycleScope.launch {
                val entity = bmiDao.queryByName(name)
                if (entity != null) {
                    updateListView(listOf(entity))
                    clearInputFields()
                } else {
                    showToast("找不到該使用者資料")
                }
            }
        }
    }

    // 取得使用者輸入的資料並建立 BMIEntity
    private fun getInputData(): BMIEntity? {
        val name = findViewById<EditText>(R.id.ed_name).text.toString()
        val gender = if (findViewById<RadioButton>(R.id.btn_m).isChecked) "男" else "女"

        val heightInput = findViewById<EditText>(R.id.ed_h).text.toString()
        val weightInput = findViewById<EditText>(R.id.ed_w).text.toString()

        val height = heightInput.toFloatOrNull()
        val weight = weightInput.toFloatOrNull()

        if (height == null || weight == null) {
            showToast("請輸入有效的身高和體重")
            return null
        }

        val bmi = calculateBMI(height, weight)

        return BMIEntity(name = name, gender = gender, height = height, weight = weight, bmi = bmi)
    }

    // 計算 BMI 的函數
    private fun calculateBMI(height: Float, weight: Float): Float {
        return weight / ((height / 100) * (height / 100))
    }

    // 更新 ListView 的函數
    private fun updateListView(entities: List<BMIEntity>) {
        bmiList.clear()
        entities.forEach { entity ->
            bmiList.add(entityToString(entity))
        }
        listAdapter.notifyDataSetChanged()
    }

    // 將 BMIEntity 轉為單行字串表示
    private fun entityToString(entity: BMIEntity): String {
        return "姓名: ${entity.name}, 性別: ${entity.gender}, 身高: ${entity.height}cm, 體重: ${entity.weight}kg, BMI: ${entity.bmi}"
    }

    // 清除輸入欄位的內容
    private fun clearInputFields() {
        findViewById<EditText>(R.id.ed_name).text.clear()
        findViewById<EditText>(R.id.ed_h).text.clear()
        findViewById<EditText>(R.id.ed_w).text.clear()
        findViewById<RadioButton>(R.id.btn_m).isChecked = true // 重設為預設性別為 "男"
    }

    // 顯示浮動訊息的函數
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
