package com.example.alipayrestart

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
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
    private lateinit var btnRestartNow: Button
    private lateinit var btnCreateShortcut: Button
    private lateinit var switchLogEnable: Switch
    private lateinit var tvLogPath: TextView
    private lateinit var btnViewLog: Button
    private lateinit var btnExportLog: Button
    private lateinit var btnClearLog: Button
    private lateinit var btnViewEarlyLog: Button
    private lateinit var btnViewCrashLog: Button

    private val configDir: File by lazy {
        File(Environment.getExternalStorageDirectory(), "AlipayRestart")
    }

    private val configFile: File by lazy {
        File(configDir, "config.json")
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_STORAGE_PERMISSION = 1001
        private const val REQUEST_EXPORT_LOG = 1002
        private const val SHORTCUT_ID = "alipay_restart_desktop_shortcut"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 记录生命周期日志
        logSafe("onCreate 开始")

        try {
            setContentView(R.layout.activity_main)
            logSafe("setContentView 完成")
        } catch (e: Exception) {
            logSafe("setContentView 失败: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "布局加载失败: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        try {
            // 初始化视图
            initViews()
            logSafe("视图初始化完成")
        } catch (e: Exception) {
            logSafe("视图初始化失败: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "视图初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        try {
            // 初始化日志
            LogUtils.init()
            LogUtils.i(TAG, "MainActivity onCreate")
            logSafe("LogUtils 初始化完成")
        } catch (e: Exception) {
            logSafe("LogUtils 初始化失败: ${e.message}")
        }

        try {
            // 检查模块状态
            checkModuleStatus()
            logSafe("模块状态检查完成")
        } catch (e: Exception) {
            logSafe("模块状态检查失败: ${e.message}")
        }

        try {
            // 检查存储权限
            checkStoragePermission()
            logSafe("存储权限检查完成")
        } catch (e: Exception) {
            logSafe("存储权限检查失败: ${e.message}")
        }

        try {
            // 加载配置
            loadConfig()
            logSafe("配置加载完成")
        } catch (e: Exception) {
            logSafe("配置加载失败: ${e.message}")
        }

        try {
            // 设置点击事件
            setupClickListeners()
            logSafe("点击事件设置完成")
        } catch (e: Exception) {
            logSafe("点击事件设置失败: ${e.message}")
        }

        try {
            // 更新日志路径显示
            tvLogPath.text = "日志目录：${configDir.absolutePath}/"
            logSafe("日志路径更新完成")
        } catch (e: Exception) {
            logSafe("日志路径更新失败: ${e.message}")
        }

        logSafe("onCreate 完成")
    }

    /**
     * 初始化视图
     */
    private fun initViews() {
        tvModuleStatus = findViewById(R.id.tvModuleStatus)
        tvModuleTip = findViewById(R.id.tvModuleTip)
        btnRestartNow = findViewById(R.id.btnRestartNow)
        btnCreateShortcut = findViewById(R.id.btnCreateShortcut)
        switchLogEnable = findViewById(R.id.switchLogEnable)
        tvLogPath = findViewById(R.id.tvLogPath)
        btnViewLog = findViewById(R.id.btnViewLog)
        btnExportLog = findViewById(R.id.btnExportLog)
        btnClearLog = findViewById(R.id.btnClearLog)
        btnViewEarlyLog = findViewById(R.id.btnViewEarlyLog)
        btnViewCrashLog = findViewById(R.id.btnViewCrashLog)
    }

    /**
     * 设置点击事件
     */
    private fun setupClickListeners() {
        btnRestartNow.setOnClickListener {
            restartAlipay()
        }

        btnCreateShortcut.setOnClickListener {
            createDesktopShortcut()
        }

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

        btnViewEarlyLog.setOnClickListener {
            viewEarlyLog()
        }

        btnViewCrashLog.setOnClickListener {
            viewCrashLog()
        }
    }

    /**
     * 安全记录日志（不会抛出异常）
     */
    private fun logSafe(message: String) {
        try {
            if (LogUtils.isLogEnabled()) {
                LogUtils.i(TAG, message)
            }
        } catch (e: Exception) {
            // 静默失败
        }
    }

    /**
     * 检查模块是否激活
     */
    private fun checkModuleStatus() {
        // 通过 MainHook 的静态变量判断模块是否激活
        // 只有当模块作用域包含自身包名时，这个变量才会被设为 true
        try {
            if (MainHook.isModuleActivated) {
                tvModuleStatus.text = "✅ 模块已激活"
                tvModuleStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                tvModuleTip.text = "模块运行正常，LSPosed API 102"
                LogUtils.i(TAG, "模块状态：已激活")
            } else {
                tvModuleStatus.text = "❌ 模块未激活"
                tvModuleStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                tvModuleTip.text = "请在 LSPosed 中启用本模块，然后重启应用"
                LogUtils.i(TAG, "模块状态：未激活")
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "检查模块状态失败", e)
            tvModuleStatus.text = "❓ 状态检测失败"
            tvModuleTip.text = "错误: ${e.message}"
        }
    }

    /**
     * 立即重启支付宝
     */
    private fun restartAlipay() {
        LogUtils.i(TAG, "点击立即重启按钮")
        val intent = Intent(this, RestartActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    /**
     * 创建桌面快捷方式
     */
    private fun createDesktopShortcut() {
        LogUtils.i(TAG, "点击创建桌面快捷方式按钮")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = getSystemService(ShortcutManager::class.java) ?: run {
                Toast.makeText(this, "无法获取 ShortcutManager", Toast.LENGTH_SHORT).show()
                LogUtils.e(TAG, "无法获取 ShortcutManager", null)
                return
            }

            // 检查是否支持固定快捷方式
            if (!shortcutManager.isRequestPinShortcutSupported) {
                Toast.makeText(this, "当前设备不支持创建桌面快捷方式", Toast.LENGTH_LONG).show()
                LogUtils.w(TAG, "当前设备不支持创建桌面快捷方式")
                return
            }

            // 创建快捷方式 Intent - 直接启动 RestartActivity
            val shortcutIntent = Intent(this, RestartActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            // 构建快捷方式信息
            val shortcutInfo = ShortcutInfo.Builder(this, SHORTCUT_ID)
                .setShortLabel("重启支付宝")
                .setLongLabel("强行停止并重启支付宝")
                .setIcon(Icon.createWithResource(this, R.drawable.ic_restart))
                .setIntent(shortcutIntent)
                .build()

            // 请求固定快捷方式
            shortcutManager.requestPinShortcut(shortcutInfo, null)
            Toast.makeText(this, "请在弹出的对话框中确认添加", Toast.LENGTH_SHORT).show()
            LogUtils.i(TAG, "已请求创建桌面快捷方式")

        } else {
            // 旧版本兼容方式
            val shortcutIntent = Intent(this, RestartActivity::class.java).apply {
                action = Intent.ACTION_MAIN
            }

            val addIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                putExtra(Intent.EXTRA_SHORTCUT_NAME, "重启支付宝")
                putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this@MainActivity, R.drawable.ic_restart))
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
                putExtra("duplicate", false)
            }

            sendBroadcast(addIntent)
            Toast.makeText(this, "已尝试创建桌面快捷方式", Toast.LENGTH_SHORT).show()
            LogUtils.i(TAG, "已发送创建快捷方式广播（旧版本）")
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
            LogUtils.i(TAG, "没有存储权限，开始请求")
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
            // 将早期日志复制到外部存储
            App.copyEarlyLogsToExternal(this)
            LogUtils.i(TAG, "已有存储权限")
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
                LogUtils.i(TAG, "配置加载成功，日志开关: $logEnabled")
            } else {
                // 默认开启日志
                switchLogEnable.isChecked = true
                saveConfig(true)
                LogUtils.i(TAG, "配置文件不存在，使用默认配置（日志开启）")
            }
        } catch (e: Exception) {
            switchLogEnable.isChecked = true
            LogUtils.e(TAG, "加载配置失败，使用默认配置", e)
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
            LogUtils.i(TAG, "配置已保存，日志开关: $logEnabled")
        } catch (e: Exception) {
            Toast.makeText(this, "保存配置失败: ${e.message}", Toast.LENGTH_SHORT).show()
            LogUtils.e(TAG, "保存配置失败", e)
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
            LogUtils.i(TAG, "查看日志: ${latestLog.name}")

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
            LogUtils.e(TAG, "打开日志失败", e)
        }
    }

    /**
     * 查看早期启动日志
     */
    private fun viewEarlyLog() {
        try {
            val logFiles = App.getEarlyLogFiles(this)
            if (logFiles.isNullOrEmpty()) {
                Toast.makeText(this, "还没有早期启动日志", Toast.LENGTH_SHORT).show()
                return
            }

            val latestLog = logFiles[0]
            LogUtils.i(TAG, "查看早期日志: ${latestLog.name}")

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

            startActivity(Intent.createChooser(intent, "查看早期启动日志"))
        } catch (e: Exception) {
            Toast.makeText(this, "打开早期日志失败: ${e.message}", Toast.LENGTH_LONG).show()
            LogUtils.e(TAG, "打开早期日志失败", e)
        }
    }

    /**
     * 查看崩溃日志
     */
    private fun viewCrashLog() {
        try {
            val logFiles = App.getEarlyLogFiles(this)
            if (logFiles.isNullOrEmpty()) {
                Toast.makeText(this, "还没有崩溃日志", Toast.LENGTH_SHORT).show()
                return
            }

            // 只找崩溃日志
            val crashLogs = logFiles.filter { it.name.startsWith("crash_") }
            if (crashLogs.isEmpty()) {
                Toast.makeText(this, "还没有崩溃日志", Toast.LENGTH_SHORT).show()
                return
            }

            val latestCrash = crashLogs[0]
            LogUtils.i(TAG, "查看崩溃日志: ${latestCrash.name}")

            // 用 FileProvider 打开
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                latestCrash
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "查看崩溃日志"))
        } catch (e: Exception) {
            Toast.makeText(this, "打开崩溃日志失败: ${e.message}", Toast.LENGTH_LONG).show()
            LogUtils.e(TAG, "打开崩溃日志失败", e)
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
            LogUtils.i(TAG, "导出日志: ${latestLog.name}")

            // 使用系统文件选择器让用户选择保存位置
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, latestLog.name)
            }

            startActivityForResult(intent, REQUEST_EXPORT_LOG)
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
            LogUtils.e(TAG, "导出日志失败", e)
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
            LogUtils.i(TAG, "已清理 $count 个日志文件")
        } catch (e: Exception) {
            Toast.makeText(this, "清理失败: ${e.message}", Toast.LENGTH_LONG).show()
            LogUtils.e(TAG, "清理日志失败", e)
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
                        LogUtils.i(TAG, "日志已导出")
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                    LogUtils.e(TAG, "导出日志失败", e)
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
                // 将早期日志复制到外部存储
                App.copyEarlyLogsToExternal(this)
                Toast.makeText(this, "存储权限已获取", Toast.LENGTH_SHORT).show()
                LogUtils.i(TAG, "存储权限已获取")
            } else {
                Toast.makeText(this, "需要存储权限才能记录日志", Toast.LENGTH_LONG).show()
                LogUtils.w(TAG, "存储权限被拒绝")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        logSafe("onResume")
    }

    override fun onStart() {
        super.onStart()
        logSafe("onStart")
    }

    override fun onPause() {
        super.onPause()
        logSafe("onPause")
    }

    override fun onStop() {
        super.onStop()
        logSafe("onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        logSafe("onDestroy")
    }
}
