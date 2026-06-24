package com.example.alipayrestart

import android.app.Activity
import android.content.Intent
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
                val forceStopResult = RootUtils.executeCommand("am force-stop ${MainHook.TARGET_PACKAGE}")
                
                // 等待应用完全停止
                Thread.sleep(500)

                // 2. 重新启动支付宝
                val startResult = RootUtils.executeCommand(
                    "monkey -p ${MainHook.TARGET_PACKAGE} -c android.intent.category.LAUNCHER 1"
                )

                // 3. 也可以尝试用 am start 方式启动（备用）
                if (!startResult.contains("Events injected: 1")) {
                    RootUtils.executeCommand(
                        "am start -n ${MainHook.TARGET_PACKAGE}/com.eg.android.AlipayGphone.AlipayLoginActivity"
                    )
                }

                // 延迟关闭，让用户看到提示
                Handler(Looper.getMainLooper()).postDelayed({
                    finish()
                }, 1000)

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
