package com.example.alipayrestart

import android.app.Activity
import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化日志（确保在模块进程中也有日志）
        LogUtils.init()
        LogUtils.i(TAG, "=== RestartActivity 启动 ===")
        
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
                LogUtils.d(TAG, "等待 1500ms 让系统清理...")
                Thread.sleep(1500)
                
                // 记录启动前的自动旋转状态
                logRotationStatus("启动支付宝前")
                
                // 2. 尝试用多种方式启动支付宝
                showToast("正在启动支付宝...")
                var started = false
                var startMethod = ""
                
                // 方式1：用 monkey 命令启动（最可靠，能启动停止状态的应用）
                if (!started) {
                    try {
                        LogUtils.i(TAG, "尝试方式1: monkey 命令（禁用旋转事件）")
                        val result = RootUtils.executeCommand(
                            "monkey -p ${ModuleStatus.TARGET_PACKAGE} -c android.intent.category.LAUNCHER --pct-rotation 0 1"
                        )
                        LogUtils.d(TAG, "方式1 输出: $result")
                        if (result.contains("Events injected: 1")) {
                            // 等待一下，然后检查进程
                            Thread.sleep(1000)
                            if (isProcessRunning(ModuleStatus.TARGET_PACKAGE)) {
                                started = true
                                startMethod = "monkey"
                                LogUtils.i(TAG, "方式1 启动成功")
                            } else {
                                LogUtils.w(TAG, "方式1 命令执行成功但进程未启动")
                            }
                        } else {
                            LogUtils.w(TAG, "方式1 失败")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "方式1 启动异常", e)
                    }
                }
                
                // 记录方式1后的自动旋转状态
                logRotationStatus("方式1后")
                
                // 方式2：用 am start 命令，加上 --include-stopped-packages
                if (!started) {
                    try {
                        LogUtils.i(TAG, "尝试方式2: am start 命令（含停止包）")
                        val result = RootUtils.executeCommand(
                            "am start --include-stopped-packages -n ${ModuleStatus.TARGET_PACKAGE}/com.eg.android.AlipayGphone.AlipayLoginActivity"
                        )
                        LogUtils.d(TAG, "方式2 输出: $result")
                        if (!result.contains("Error")) {
                            // 等待一下，然后检查进程
                            Thread.sleep(1000)
                            if (isProcessRunning(ModuleStatus.TARGET_PACKAGE)) {
                                started = true
                                startMethod = "am start"
                                LogUtils.i(TAG, "方式2 启动成功")
                            } else {
                                LogUtils.w(TAG, "方式2 命令执行成功但进程未启动")
                            }
                        } else {
                            LogUtils.w(TAG, "方式2 失败: $result")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "方式2 启动异常", e)
                    }
                }
                
                // 记录方式2后的自动旋转状态
                logRotationStatus("方式2后")
                
                // 方式3：用 PackageManager 获取官方启动 Intent
                if (!started) {
                    try {
                        LogUtils.i(TAG, "尝试方式3: PackageManager 获取启动 Intent")
                        val launchIntent = packageManager.getLaunchIntentForPackage(ModuleStatus.TARGET_PACKAGE)
                        if (launchIntent != null) {
                            LogUtils.d(TAG, "启动 Intent: $launchIntent")
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            startActivity(launchIntent)
                            // 等待一下，然后检查进程
                            Thread.sleep(1000)
                            if (isProcessRunning(ModuleStatus.TARGET_PACKAGE)) {
                                started = true
                                startMethod = "PackageManager"
                                LogUtils.i(TAG, "方式3 启动成功")
                            } else {
                                LogUtils.w(TAG, "方式3 命令执行成功但进程未启动")
                            }
                        } else {
                            LogUtils.w(TAG, "方式3 失败: 无法获取启动 Intent")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "方式3 启动异常", e)
                    }
                }
                
                // 记录方式3后的自动旋转状态
                logRotationStatus("方式3后")
                
                // 方式4：再试一次 am start，用 action 方式
                if (!started) {
                    try {
                        LogUtils.i(TAG, "尝试方式4: am start action 方式")
                        val result = RootUtils.executeCommand(
                            "am start --include-stopped-packages -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p ${ModuleStatus.TARGET_PACKAGE}"
                        )
                        LogUtils.d(TAG, "方式4 输出: $result")
                        if (!result.contains("Error")) {
                            // 等待一下，然后检查进程
                            Thread.sleep(1000)
                            if (isProcessRunning(ModuleStatus.TARGET_PACKAGE)) {
                                started = true
                                startMethod = "am start action"
                                LogUtils.i(TAG, "方式4 启动成功")
                            } else {
                                LogUtils.w(TAG, "方式4 命令执行成功但进程未启动")
                            }
                        } else {
                            LogUtils.w(TAG, "方式4 失败: $result")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "方式4 启动异常", e)
                    }
                }
                
                // 记录最终的自动旋转状态
                logRotationStatus("全部启动尝试后")
                
                LogUtils.i(TAG, "重启流程结束，启动方式: $startMethod, 结果: ${if (started) "成功" else "失败"}")
                LogUtils.i(TAG, "日志文件路径: ${LogUtils.getLogFilePath()}")
                
                if (started) {
                    showToast("✅ 支付宝已重启")
                } else {
                    showToast("❌ 重启失败，请手动打开支付宝")
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
