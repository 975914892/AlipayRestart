package com.example.alipayrestart

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * 透明 Activity，用于执行重启操作
 * 点击快捷方式后启动此 Activity，执行 force-stop 然后重新打开支付宝
 */
class RestartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置透明
        window.setBackgroundDrawableResource(android.R.color.transparent)

        // 执行重启逻辑
        executeRestart()
    }

    private fun executeRestart() {
        Thread {
            try {
                // 检查 root 权限
                if (!RootUtils.checkRoot()) {
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
                RootUtils.forceStopPackage(MainHook.TARGET_PACKAGE)

                // 等待应用完全停止，给系统足够时间清理
                Thread.sleep(1500)

                // 2. 尝试用多种方式启动支付宝
                var started = false

                // 方式1：用 PackageManager 获取官方启动 Intent（最可靠）
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(MainHook.TARGET_PACKAGE)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(launchIntent)
                        started = true
                    }
                } catch (e: Exception) {
                    // 方式1失败，继续尝试其他方式
                }

                // 方式2：用 am start 命令启动
                if (!started) {
                    try {
                        val result = RootUtils.executeCommand(
                            "am start -n ${MainHook.TARGET_PACKAGE}/com.eg.android.AlipayGphone.AlipayLoginActivity"
                        )
                        if (!result.contains("Error")) {
                            started = true
                        }
                    } catch (e: Exception) {
                        // 继续尝试
                    }
                }

                // 方式3：用 monkey 命令启动（备用）
                if (!started) {
                    try {
                        RootUtils.launchPackage(MainHook.TARGET_PACKAGE)
                        started = true
                    } catch (e: Exception) {
                        // 继续
                    }
                }

                // 方式4：再试一次 am start，用 action 方式
                if (!started) {
                    try {
                        RootUtils.executeCommand(
                            "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p ${MainHook.TARGET_PACKAGE}"
                        )
                        started = true
                    } catch (e: Exception) {
                        // 失败
                    }
                }

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
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "重启失败: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }.start()
    }
}
