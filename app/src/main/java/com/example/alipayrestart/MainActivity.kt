package com.example.alipayrestart

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

class MainActivity : AppCompatActivity() {

    private lateinit var tvModuleStatus: TextView
    private lateinit var tvModuleTip: TextView
    private lateinit var switchLogEnable: Switch
    private lateinit var tvLogPath: TextView
    private lateinit var btnViewLog: Button
    private lateinit var btnExportLog: Button
    private lateinit var btnClearLog: Button

    private val configDir: File by lazy {
        File(Environment.getExternalStorageDirectory(), "AlipayRestart")
    }
    private val configFile: File by lazy {
        File(configDir, "config.json")
    }

    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 1001
        private const val REQUEST_EXPORT_LOG = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化视图
        tvModuleStatus = findViewById(R.id.tvModuleStatus)
        tvModuleTip = findViewById(R.id.tvModuleTip)
        switchLogEnable = findViewById(R.id.switchLogEnable)
        tvLogPath = findViewById(R.id.tvLogPath)
        btnViewLog = findViewById(R.id.btnViewLog)
        btnExportLog = findViewById(R.id.btnExportLog)
        btnClearLog = findViewById(R.id.btnClearLog)

        // 检查模块状态
        checkModuleStatus()

        // 检查存储权限
        checkStoragePermission()

        // 加载配置
        loadConfig()

        // 设置点击事件
        switchLogEnable.setOnCheckedChangeListener { _, isChecked ->
            saveConfig(isChecked)
            Toast.makeText(this, "日志记录已${if (isChecked) "开启" else "关闭"}", Toast.LENGTH_SHORT).show()
        }

        btnViewLog.setOnClickListener {
            viewLatestLog()
        }

        btnExportLog.setOnClickListener {
            exportLog()
        }

        btnClearLog.setOnClickListener {
            clearAllLogs()
        }

        // 更新日志路径显示
        tvLogPath.text = "日志目录：${configDir.absolutePath}/"
    }

    /**
     * 检查模块是否激活
     */
    private fun checkModuleStatus() {
        // 通过尝试读取一个 Hook 标记来判断模块是否激活
        try {
            // 如果模块被激活，MainHook 会设置这个标记
            val clazz = Class.forName("com.example.alipayrestart.MainHook")
            // 如果能加载到类，说明模块可能已激活（但不一定作用于正确的包）
            tvModuleStatus.text = "✅ 模块已安装"
            tvModuleStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            tvModuleTip.text = "请在 LSPosed 中确认作用域已选择支付宝"
        } catch (e: ClassNotFoundException) {
            tvModuleStatus.text = "❌ 模块未激活"
            tvModuleStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            tvModuleTip.text = "请在 LSPosed 中启用本模块，作用域选择支付宝"
        }
    }

    /**
     * 检查存储权限
     */
    private fun checkStoragePermission() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermission) {
            // 请求存储权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    REQUEST_STORAGE_PERMISSION
                )
            }
        } else {
            // 确保目录存在
            ensureConfigDir()
        }
    }

    /**
     * 确保配置目录存在
     */
    private fun ensureConfigDir() {
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
    }

    /**
     * 加载配置
     */
    private fun loadConfig() {
        try {
            ensureConfigDir()
            if (configFile.exists()) {
                val content = configFile.readText()
                val json = JSONObject(content)
                val logEnabled = json.optBoolean("log_enabled", true)
                switchLogEnable.isChecked = logEnabled
            } else {
                // 默认开启日志
                switchLogEnable.isChecked = true
                saveConfig(true)
            }
        } catch (e: Exception) {
            switchLogEnable.isChecked = true
        }
    }

    /**
     * 保存配置
     */
    private fun saveConfig(logEnabled: Boolean) {
        try {
            ensureConfigDir()
            val json = JSONObject()
            json.put("log_enabled", logEnabled)
            FileWriter(configFile).use {
                it.write(json.toString(2))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "保存配置失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 查看最新日志
     */
    private fun viewLatestLog() {
        try {
            val logFiles = LogUtils.getAllLogFiles()
            if (logFiles.isNullOrEmpty()) {
                Toast.makeText(this, "还没有日志文件", Toast.LENGTH_SHORT).show()
                return
            }

            val latestLog = logFiles[0]

            // 用 FileProvider 打开
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                latestLog
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "查看日志"))

        } catch (e: Exception) {
            Toast.makeText(this, "打开日志失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 导出日志（自选目录）
     */
    private fun exportLog() {
        try {
            val logFiles = LogUtils.getAllLogFiles()
            if (logFiles.isNullOrEmpty()) {
                Toast.makeText(this, "还没有日志文件", Toast.LENGTH_SHORT).show()
                return
            }

            val latestLog = logFiles[0]

            // 使用系统文件选择器让用户选择保存位置
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, latestLog.name)
            }

            startActivityForResult(intent, REQUEST_EXPORT_LOG)

        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 清理所有日志
     */
    private fun clearAllLogs() {
        try {
            val logFiles = LogUtils.getAllLogFiles()
            if (logFiles.isNullOrEmpty()) {
                Toast.makeText(this, "还没有日志文件", Toast.LENGTH_SHORT).show()
                return
            }

            var count = 0
            logFiles.forEach { file ->
                if (file.delete()) {
                    count++
                }
            }

            Toast.makeText(this, "已清理 $count 个日志文件", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "清理失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_EXPORT_LOG && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    // 找到最新的日志文件
                    val logFiles = LogUtils.getAllLogFiles()
                    if (!logFiles.isNullOrEmpty()) {
                        val latestLog = logFiles[0]

                        // 写入到用户选择的位置
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            latestLog.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }

                        Toast.makeText(this, "日志已导出", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ensureConfigDir()
                loadConfig()
                Toast.makeText(this, "存储权限已获取", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要存储权限才能记录日志", Toast.LENGTH_LONG).show()
            }
        }
    }
}
