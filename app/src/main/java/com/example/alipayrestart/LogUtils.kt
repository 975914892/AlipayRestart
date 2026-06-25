package com.example.alipayrestart

import android.os.Environment
import de.robv.android.xposed.XposedBridge
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志工具类
 * 同时输出到 XposedBridge 和本地文件，方便调试
 * 支持开关控制，从配置文件读取是否启用日志
 */
object LogUtils {

    private const val LOG_DIR = "AlipayRestart"
    private const val LOG_PREFIX = "log_"
    private const val LOG_SUFFIX = ".txt"
    private const val CONFIG_FILE = "config.json"

    private var logFile: File? = null
    private var initialized = false
    private var logEnabled = true // 默认开启

    /**
     * 初始化日志文件
     */
    fun init() {
        if (initialized) return

        try {
            // 先加载配置
            loadConfig()

            // 如果日志未启用，直接返回，不创建日志文件
            if (!logEnabled) {
                initialized = true
                return
            }

            val logDir = File(Environment.getExternalStorageDirectory(), LOG_DIR)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            logFile = File(logDir, "$LOG_PREFIX$timeStamp$LOG_SUFFIX")

            // 写入日志头
            appendLog("========== AlipayRestart 模块启动 ==========")
            appendLog("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLog("日志状态: 已启用")
            appendLog("============================================")

            initialized = true
        } catch (e: Exception) {
            XposedBridge.log("AlipayRestart: 日志初始化失败 - ${e.message}")
        }
    }

    /**
     * 加载配置文件
     */
    private fun loadConfig() {
        try {
            val configDir = File(Environment.getExternalStorageDirectory(), LOG_DIR)
            val configFile = File(configDir, CONFIG_FILE)

            if (configFile.exists()) {
                val content = configFile.readText()
                val json = JSONObject(content)
                logEnabled = json.optBoolean("log_enabled", true)
            } else {
                // 配置文件不存在，默认开启
                logEnabled = true
            }
        } catch (e: Exception) {
            // 读取配置失败，默认开启
            logEnabled = true
        }
    }

    /**
     * 检查日志是否启用
     */
    fun isLogEnabled(): Boolean {
        return logEnabled
    }

    /**
     * 记录信息日志
     */
    fun i(tag: String, message: String) {
        if (!logEnabled) return

        val fullMessage = "[INFO][$tag] $message"
        XposedBridge.log("AlipayRestart: $fullMessage")
        appendLog(fullMessage)
    }

    /**
     * 记录警告日志
     */
    fun w(tag: String, message: String) {
        if (!logEnabled) return

        val fullMessage = "[WARN][$tag] $message"
        XposedBridge.log("AlipayRestart: $fullMessage")
        appendLog(fullMessage)
    }

    /**
     * 记录错误日志
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (!logEnabled) return

        val fullMessage = "[ERROR][$tag] $message"
        XposedBridge.log("AlipayRestart: $fullMessage")
        appendLog(fullMessage)

        if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()
            XposedBridge.log("AlipayRestart: $stackTrace")
            appendLog(stackTrace)
        }
    }

    /**
     * 记录调试日志
     */
    fun d(tag: String, message: String) {
        if (!logEnabled) return

        val fullMessage = "[DEBUG][$tag] $message"
        XposedBridge.log("AlipayRestart: $fullMessage")
        appendLog(fullMessage)
    }

    /**
     * 获取日志文件路径
     */
    fun getLogFilePath(): String? {
        return logFile?.absolutePath
    }

    /**
     * 获取所有日志文件
     */
    fun getAllLogFiles(): Array<File>? {
        return try {
            val logDir = File(Environment.getExternalStorageDirectory(), LOG_DIR)
            if (logDir.exists() && logDir.isDirectory) {
                logDir.listFiles { _, name -> name.startsWith(LOG_PREFIX) && name.endsWith(LOG_SUFFIX) }
                    ?.sortedByDescending { it.lastModified() }
                    ?.toTypedArray()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 追加日志到文件
     */
    private fun appendLog(message: String) {
        if (logFile == null) return

        try {
            val timeStamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logLine = "[$timeStamp] $message\n"

            FileWriter(logFile, true).use {
                it.write(logLine)
            }
        } catch (e: Exception) {
            // 静默失败，避免日志写入失败影响主功能
        }
    }
}
