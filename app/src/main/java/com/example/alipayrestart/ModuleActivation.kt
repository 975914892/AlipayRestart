package com.example.alipayrestart

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 模块激活状态管理
 * 使用文件标记方式，解决不同 ClassLoader 下静态变量不共享的问题
 */
object ModuleActivation {

    private const val ACTIVATION_FILE = "module_activated"
    private const val TAG = "ModuleActivation"

    /**
     * 标记模块已激活（使用 dataDir 路径，不需要 Context）
     * 在 MainHook 中调用
     */
    fun markActivatedWithDataDir(dataDir: String) {
        try {
            val file = File(dataDir, "files/$ACTIVATION_FILE")
            file.parentFile?.mkdirs()
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            file.writeText("activated at $time")
            LogUtils.i(TAG, "模块激活标记已写入: ${file.absolutePath}")
        } catch (e: Exception) {
            LogUtils.e(TAG, "写入激活标记失败", e)
        }
    }

    /**
     * 检查模块是否已激活
     * 在 MainActivity 中调用
     */
    fun isActivated(context: Context): Boolean {
        return try {
            val file = getActivationFile(context)
            val activated = file.exists()
            LogUtils.i(TAG, "检查激活状态: $activated, 文件: ${file.absolutePath}")
            activated
        } catch (e: Exception) {
            LogUtils.e(TAG, "检查激活状态失败", e)
            false
        }
    }

    /**
     * 获取激活标记文件
     * 使用应用专属内部存储，不需要权限
     */
    private fun getActivationFile(context: Context): File {
        return File(context.filesDir, ACTIVATION_FILE)
    }
}
