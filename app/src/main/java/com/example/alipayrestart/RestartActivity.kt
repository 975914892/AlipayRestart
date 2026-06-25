package com.example.alipayrestart

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * 透明 Activity，用于执行重启操作
 * 点击快捷方式后启动此 Activity，执行 force-stop 然后重新打开支付宝
 */
class RestartActivity : Activity() {

    private val TAG = "RestartActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化日志（确保在模块进程中也有日志）
        LogUtils.init()
        LogUtils.i(TAG, "RestartActivity 启动")

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
                    runOnUiThread {
                        Toast.makeText(this, "需要 Root 权限才能使用此功能", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    return@Thread
                }

                runOnUiThread {
                    Toast.makeText(this, "正在重启支付宝...", Toast.LENGTH_SHORT).show()
                }

                // 1. 强行停止支付宝
                LogUtils.i(TAG, "执行 force-stop: ${ModuleStatus.TARGET_PACKAGE}")
                val forceStopResult = RootUtils.executeCommand("am force-stop ${ModuleStatus.TARGET_PACKAGE}")
                LogUtils.d(TAG, "force-stop 输出: $forceStopResult")

                // 确认进程是否已停止
                val checkProcess = RootUtils.executeCommand("ps | grep ${ModuleStatus.TARGET_PACKAGE}")
                LogUtils.d(TAG, "进程检查结果: ${if (checkProcess.isBlank()) "已停止" else "仍在运行"}")

                // 等待应用完全停止，给系统足够时间清理
                LogUtils.d(TAG, "等待 1500ms 让系统清理...")
                Thread.sleep(1500)

                // 2. 尝试用多种方式启动支付宝
                var started = false
                var startMethod = ""

                // 方式1：用 PackageManager 获取官方启动 Intent（最可靠）
                try {
                    LogUtils.i(TAG, "尝试方式1: PackageManager 获取启动 Intent")
                    val launchIntent = packageManager.getLaunchIntentForPackage(ModuleStatus.TARGET_PACKAGE)
                    if (launchIntent != null) {
                        LogUtils.d(TAG, "启动 Intent: $launchIntent")
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(launchIntent)
                        started = true
                        startMethod = "PackageManager"
                        LogUtils.i(TAG, "方式1 启动成功")
                    } else {
                        LogUtils.w(TAG, "方式1 失败: 无法获取启动 Intent")
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "方式1 启动异常", e)
                }

                // 方式2：用 am start 命令启动
                if (!started) {
                    try {
                        LogUtils.i(TAG, "尝试方式2: am start 命令")
                        val result = RootUtils.executeCommand(
                            "am start -n ${ModuleStatus.TARGET_PACKAGE}/com.eg.android.AlipayGphone.AlipayLoginActivity"
                        )
                        LogUtils.d(TAG, "方式2 输出: $result")
                        if (!result.contains("Error")) {
                            started = true
                            startMethod = "am start"
                            LogUtils.i(TAG, "方式2 启动成功")
                        } else {
                            LogUtils.w(TAG, "方式2 失败: $result")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "方式2 启动异常", e)
                    }
                }

                // 方式3：用 monkey 命令启动（备用）
                if (!started) {
                    try {
                        LogUtils.i(TAG, "尝试方式3: monkey 命令")
                        val result = RootUtils.executeCommand(
                            "monkey -p ${ModuleStatus.TARGET_PACKAGE} -c android.intent.category.LAUNCHER 1"
                        )
                        LogUtils.d(TAG, "方式3 输出: $result")
                        if (result.contains("Events injected: 1")) {
                            started = true
                            startMethod = "monkey"
                            LogUtils.i(TAG, "方式3 启动成功")
                        } else {
                            LogUtils.w(TAG, "方式3 失败")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "方式3 启动异常", e)
                    }
                }

                // 方式4：再试一次 am start，用 action 方式
                if (!started) {
                    try {
                        LogUtils.i(TAG, "尝试方式4: am start action 方式")
                        val result = RootUtils.executeCommand(
                            "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p ${ModuleStatus.TARGET_PACKAGE}"
                        )
                        LogUtils.d(TAG, "方式4 输出: $result")
                        if (!result.contains("Error")) {
                            started = true
                            startMethod = "am start action"
                            LogUtils.i(TAG, "方式4 启动成功")
                        } else {
                            LogUtils.w(TAG, "方式4 失败: $result")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "方式4 启动异常", e)
                    }
                }

                LogUtils.i(TAG, "重启流程结束，启动方式: $startMethod, 结果: ${if (started) "成功" else "失败"}")
                LogUtils.i(TAG, "日志文件路径: ${LogUtils.getLogFilePath()}")

                val finalStarted = started
                runOnUiThread {
                    if (finalStarted) {
                        Toast.makeText(this, "支付宝已重启", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "重启失败，请手动打开支付宝", Toast.LENGTH_LONG).show()
                    }
                }

                // 延迟关闭
                Handler(Looper.getMainLooper()).postDelayed({
                    finish()
                }, 800)

            } catch (e: Exception) {
                LogUtils.e(TAG, "重启流程异常", e)
                runOnUiThread {
                    Toast.makeText(this, "重启失败: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }.start()
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
