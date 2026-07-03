package com.example.alipayrestart

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * 透明 Activity，用于执行重启操作
 * 点击快捷方式后启动此 Activity，执行 force-stop 然后重新打开支付宝
 */
class RestartActivity : Activity() {
    private val TAG = "RestartActivity"
    
    // 保存原始的自动旋转设置
    private var originalRotationSetting = 0
    
    // SharedPreferences 相关
    private lateinit var prefs: SharedPreferences
    
    companion object {
        private const val PREFS_NAME = "alipay_restart_prefs"
        private const val KEY_LAST_SUCCESS_METHOD = "last_success_method"
        private const val METHOD_MONKEY = "monkey"
        private const val METHOD_AM_START = "am_start"
        private const val METHOD_PACKAGE_MANAGER = "package_manager"
        private const val METHOD_AM_START_ACTION = "am_start_action"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化 SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // 初始化日志（确保在模块进程中也有日志）
        LogUtils.init()
        LogUtils.i(TAG, "=== RestartActivity 启动 ===")
        
        // 读取上次成功的启动方式
        val lastMethod = getLastSuccessfulMethod()
        if (lastMethod != null) {
            LogUtils.i(TAG, "上次成功的启动方式: $lastMethod")
        } else {
            LogUtils.i(TAG, "没有记录上次成功的启动方式，将依次尝试")
        }
        
        // 记录启动时的自动旋转设置
        try {
            originalRotationSetting = Settings.System.getInt(
                contentResolver, 
                Settings.System.ACCELEROMETER_ROTATION, 
                0
            )
            LogUtils.i(TAG, "启动时自动旋转设置: ${if (originalRotationSetting == 1) "开启" else "关闭"}")
        } catch (e: Exception) {
            LogUtils.e(TAG, "读取自动旋转设置失败", e)
        }
        
        // 设置透明
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        // 检查是否是导出日志的请求
        if (intent?.action == "com.example.alipayrestart.EXPORT_LOG") {
            exportLog()
            return
        }
        
        // 执行重启逻辑
        executeRestart()
    }

    private fun executeRestart() {
        Thread {
            try {
                LogUtils.i(TAG, "开始执行重启流程")
                
                // 检查 root 权限
                val hasRoot = RootUtils.checkRoot()
                LogUtils.i(TAG, "Root 权限检查: ${if (hasRoot) "通过" else "失败"}")
                
                if (!hasRoot) {
                    showToast("需要 Root 权限才能使用此功能")
                    finish()
                    return@Thread
                }
                
                // 记录 force-stop 前的自动旋转状态
                logRotationStatus("force-stop 前")
                
                // 1. 强行停止支付宝
                showToast("正在停止支付宝...")
                LogUtils.i(TAG, "执行 force-stop: ${ModuleStatus.TARGET_PACKAGE}")
                val forceStopResult = RootUtils.executeCommand("am force-stop ${ModuleStatus.TARGET_PACKAGE}")
                LogUtils.d(TAG, "force-stop 输出: $forceStopResult")
                
                // 记录 force-stop 后的自动旋转状态
                logRotationStatus("force-stop 后")
                
                // 确认进程是否已停止
                val checkStopped = isProcessRunning(ModuleStatus.TARGET_PACKAGE)
                LogUtils.d(TAG, "进程检查结果: ${if (checkStopped) "仍在运行" else "已停止"}")
                
                // 等待应用完全停止，给系统足够时间清理
                LogUtils.d(TAG, "等待 3000ms 让系统清理...")
                Thread.sleep(3000)
                
                // 记录启动前的自动旋转状态
                logRotationStatus("启动支付宝前")
                
                // 2. 尝试启动支付宝（优先使用上次成功的方式）
                showToast("正在启动支付宝...")
                var started = false
                var startMethod = ""
                
                // 先读取上次成功的方式
                val lastMethod = getLastSuccessfulMethod()
                
                // 如果有上次成功的方式，先尝试它
                if (lastMethod != null) {
                    LogUtils.i(TAG, "优先尝试上次成功的方式: $lastMethod")
                    val result = tryStartMethod(lastMethod)
                    if (result.first) {
                        started = true
                        startMethod = lastMethod
                        LogUtils.i(TAG, "上次成功的方式依然有效: $lastMethod")
                    } else {
                        LogUtils.w(TAG, "上次成功的方式失效了，开始依次尝试所有方式")
                    }
                }
                
                // 如果上次成功的方式失败了，或者没有记录，依次尝试所有方式
                if (!started) {
                    val methods = listOf(
                        METHOD_MONKEY to "monkey 命令",
                        METHOD_AM_START to "am start 命令",
                        METHOD_PACKAGE_MANAGER to "PackageManager",
                        METHOD_AM_START_ACTION to "am start action 方式"
                    )
                    
                    for ((methodKey, methodName) in methods) {
                        // 跳过已经试过的方式（如果上次成功的方式试过了）
                        if (lastMethod != null && methodKey == lastMethod) {
                            continue
                        }
                        
                        LogUtils.i(TAG, "尝试方式: $methodName")
                        val result = tryStartMethod(methodKey)
                        if (result.first) {
                            started = true
                            startMethod = methodKey
                            LogUtils.i(TAG, "方式 $methodName 启动成功")
                            break
                        } else {
                            LogUtils.w(TAG, "方式 $methodName 失败")
                        }
                    }
                }
                
                // 记录最终的自动旋转状态
                logRotationStatus("全部启动尝试后")
                
                LogUtils.i(TAG, "重启流程结束，启动方式: $startMethod, 结果: ${if (started) "成功" else "失败"}")
                LogUtils.i(TAG, "日志文件路径: ${LogUtils.getLogFilePath()}")
                
                if (started) {
                    showToast("✅ 支付宝已重启")
                    // 保存成功的方式
                    saveSuccessfulMethod(startMethod)
                    LogUtils.i(TAG, "已保存成功的启动方式: $startMethod")
                } else {
                    showToast("❌ 重启失败，请手动打开支付宝")
                    // 清除上次成功的方式，因为可能都失效了
                    clearLastSuccessfulMethod()
                    LogUtils.i(TAG, "所有方式都失败，已清除记录")
                }
                
                // 尝试恢复自动旋转设置
                restoreRotationSetting()
                
                // 延迟关闭
                Handler(Looper.getMainLooper()).postDelayed({
                    finish()
                }, 800)
                
            } catch (e: Exception) {
                LogUtils.e(TAG, "重启流程异常", e)
                showToast("重启失败: ${e.message}")
                
                // 异常时也尝试恢复
                restoreRotationSetting()
                
                finish()
            }
        }.start()
    }
    
    /**
     * 尝试指定的启动方式
     * @return Pair<是否成功, 方式名称>
     */
    private fun tryStartMethod(method: String): Pair<Boolean, String> {
        return try {
            val success = when (method) {
                METHOD_MONKEY -> tryMonkeyMethod()
                METHOD_AM_START -> tryAmStartMethod()
                METHOD_PACKAGE_MANAGER -> tryPackageManagerMethod()
                METHOD_AM_START_ACTION -> tryAmStartActionMethod()
                else -> false
            }
            success to method
        } catch (e: Exception) {
            LogUtils.e(TAG, "方式 $method 执行异常", e)
            false to method
        }
    }
    
    /**
     * 方式1：monkey 命令
     */
    private fun tryMonkeyMethod(): Boolean {
        return try {
            val result = RootUtils.executeCommand(
                "monkey -p ${ModuleStatus.TARGET_PACKAGE} -c android.intent.category.LAUNCHER --pct-rotation 0 1"
            )
            LogUtils.d(TAG, "monkey 输出: $result")
            if (result.contains("Events injected: 1")) {
                Thread.sleep(1000)
                if (isProcessRunning(ModuleStatus.TARGET_PACKAGE)) {
                    true
                } else {
                    LogUtils.w(TAG, "monkey 命令执行成功但进程未启动")
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "monkey 方式异常", e)
            false
        }
    }
    
    /**
     * 方式2：am start 命令
     */
    private fun tryAmStartMethod(): Boolean {
        return try {
            val result = RootUtils.executeCommand(
                "am start --include-stopped-packages -n ${ModuleStatus.TARGET_PACKAGE}/com.eg.android.AlipayGphone.AlipayLoginActivity"
            )
            LogUtils.d(TAG, "am start 输出: $result")
            if (!result.contains("Error")) {
                Thread.sleep(1000)
                if (isProcessRunning(ModuleStatus.TARGET_PACKAGE)) {
                    true
                } else {
                    LogUtils.w(TAG, "am start 命令执行成功但进程未启动")
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "am start 方式异常", e)
            false
        }
    }
    
    /**
     * 方式3：PackageManager
     */
    private fun tryPackageManagerMethod(): Boolean {
        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(ModuleStatus.TARGET_PACKAGE)
            if (launchIntent != null) {
                LogUtils.d(TAG, "启动 Intent: $launchIntent")
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(launchIntent)
                Thread.sleep(1000)
                if (isProcessRunning(ModuleStatus.TARGET_PACKAGE)) {
                    true
                } else {
                    LogUtils.w(TAG, "PackageManager 启动成功但进程未启动")
                    false
                }
            } else {
                LogUtils.w(TAG, "PackageManager 无法获取启动 Intent")
                false
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "PackageManager 方式异常", e)
            false
        }
    }
    
    /**
     * 方式4：am start action 方式
     */
    private fun tryAmStartActionMethod(): Boolean {
        return try {
            val result = RootUtils.executeCommand(
                "am start --include-stopped-packages -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p ${ModuleStatus.TARGET_PACKAGE}"
            )
            LogUtils.d(TAG, "am start action 输出: $result")
            if (!result.contains("Error")) {
                Thread.sleep(1000)
                if (isProcessRunning(ModuleStatus.TARGET_PACKAGE)) {
                    true
                } else {
                    LogUtils.w(TAG, "am start action 命令执行成功但进程未启动")
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "am start action 方式异常", e)
            false
        }
    }
    
    /**
     * 获取上次成功的启动方式
     */
    private fun getLastSuccessfulMethod(): String? {
        return try {
            val method = prefs.getString(KEY_LAST_SUCCESS_METHOD, null)
            method
        } catch (e: Exception) {
            LogUtils.e(TAG, "读取上次成功方式失败", e)
            null
        }
    }
    
    /**
     * 保存成功的启动方式
     */
    private fun saveSuccessfulMethod(method: String) {
        try {
            prefs.edit().putString(KEY_LAST_SUCCESS_METHOD, method).apply()
            LogUtils.i(TAG, "已保存成功的启动方式到本地: $method")
        } catch (e: Exception) {
            LogUtils.e(TAG, "保存成功方式失败", e)
        }
    }
    
    /**
     * 清除上次成功的启动方式
     */
    private fun clearLastSuccessfulMethod() {
        try {
            prefs.edit().remove(KEY_LAST_SUCCESS_METHOD).apply()
            LogUtils.i(TAG, "已清除上次成功的启动方式记录")
        } catch (e: Exception) {
            LogUtils.e(TAG, "清除成功方式失败", e)
        }
    }
    
    /**
     * 记录当前自动旋转状态
     */
    private fun logRotationStatus(stage: String) {
        try {
            val rotation = Settings.System.getInt(
                contentResolver, 
                Settings.System.ACCELEROMETER_ROTATION, 
                -1
            )
            LogUtils.i(TAG, "[$stage] 自动旋转状态: ${if (rotation == 1) "开启" else if (rotation == 0) "关闭" else "未知($rotation)"}")
        } catch (e: Exception) {
            LogUtils.e(TAG, "[$stage] 读取自动旋转状态失败", e)
        }
    }
    
    /**
     * 恢复原始的自动旋转设置
     * 使用 Root 权限执行 settings put 命令，不需要用户授权
     */
    private fun restoreRotationSetting() {
        try {
            val currentRotation = Settings.System.getInt(
                contentResolver, 
                Settings.System.ACCELEROMETER_ROTATION, 
                -1
            )
            
            LogUtils.i(TAG, "恢复自动旋转设置 - 原始值: $originalRotationSetting, 当前值: $currentRotation")
            
            if (currentRotation != originalRotationSetting && originalRotationSetting >= 0) {
                // 使用 Root 权限恢复设置
                val result = RootUtils.executeCommand(
                    "settings put system accelerometer_rotation $originalRotationSetting"
                )
                LogUtils.i(TAG, "Root 恢复自动旋转结果: $result")
                
                // 验证是否恢复成功
                Thread.sleep(200)
                val verifyRotation = Settings.System.getInt(
                    contentResolver, 
                    Settings.System.ACCELEROMETER_ROTATION, 
                    -1
                )
                LogUtils.i(TAG, "恢复后验证: $verifyRotation (期望: $originalRotationSetting)")
                
                if (verifyRotation == originalRotationSetting) {
                    LogUtils.i(TAG, "自动旋转设置已成功恢复为: ${if (originalRotationSetting == 1) "开启" else "关闭"}")
                } else {
                    LogUtils.w(TAG, "自动旋转恢复失败，当前值: $verifyRotation")
                }
            } else {
                LogUtils.i(TAG, "自动旋转设置未变化，无需恢复")
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "恢复自动旋转设置失败", e)
        }
    }

    /**
     * 在主线程显示 Toast
     */
    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 检查进程是否正在运行
     */
    private fun isProcessRunning(packageName: String): Boolean {
        return try {
            val result = RootUtils.executeCommand("ps -A | grep $packageName")
            val running = result.isNotBlank() && result.contains(packageName)
            LogUtils.d(TAG, "检查进程 $packageName: ${if (running) "运行中" else "未运行"}")
            running
        } catch (e: Exception) {
            LogUtils.e(TAG, "检查进程失败", e)
            false
        }
    }

    /**
     * 导出日志文件
     */
    private fun exportLog() {
        try {
            val logFiles = LogUtils.getAllLogFiles()
            if (logFiles.isNullOrEmpty()) {
                Toast.makeText(this, "没有找到日志文件", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            // 取最新的日志文件
            val latestLog = logFiles[0]
            LogUtils.i(TAG, "导出日志: ${latestLog.absolutePath}")
            
            // 用 FileProvider 分享
            try {
                val uri: Uri = FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    latestLog
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "导出日志"))
            } catch (e: Exception) {
                // 如果 FileProvider 失败，直接打开文件
                LogUtils.e(TAG, "FileProvider 分享失败，尝试直接打开", e)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.fromFile(latestLog), "text/plain")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            
            Toast.makeText(this, "日志文件: ${latestLog.name}", Toast.LENGTH_LONG).show()
            
            // 延迟关闭
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 500)
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "导出日志失败", e)
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
