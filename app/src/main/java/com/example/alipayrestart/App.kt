package com.example.alipayrestart

import android.app.Application
import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自定义 Application 类
 * 用于早期日志初始化和全局异常捕获
 */
class App : Application() {

    companion object {
        private const val TAG = "App"
        private const val LOG_DIR = "AlipayRestart"
        private const val CRASH_LOG_PREFIX = "crash_"
        private const val EARLY_LOG_PREFIX = "early_"
        private const val LOG_SUFFIX = ".txt"

        // 早期日志文件（使用内部存储，不需要权限）
        private var earlyLogFile: File? = null
        private var earlyLogInitialized = false

        /**
         * 获取早期日志文件列表
         */
        fun getEarlyLogFiles(context: Context): Array<File>? {
            return try {
                val logDir = File(context.filesDir, LOG_DIR)
                if (logDir.exists() && logDir.isDirectory) {
                    logDir.listFiles { _, name ->
                        (name.startsWith(EARLY_LOG_PREFIX) || name.startsWith(CRASH_LOG_PREFIX))
                                && name.endsWith(LOG_SUFFIX)
                    }?.sortedByDescending { it.lastModified() }
                        ?.toTypedArray()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        /**
         * 将早期日志复制到外部存储
         */
        fun copyEarlyLogsToExternal(context: Context) {
            try {
                val earlyLogs = getEarlyLogFiles(context) ?: return
                val externalDir = File(Environment.getExternalStorageDirectory(), LOG_DIR)
                if (!externalDir.exists()) {
                    externalDir.mkdirs()
                }

                earlyLogs.forEach { logFile ->
                    try {
                        val destFile = File(externalDir, logFile.name)
                        if (!destFile.exists()) {
                            logFile.copyTo(destFile, overwrite = false)
                        }
                    } catch (e: Exception) {
                        // 静默失败
                    }
                }
            } catch (e: Exception) {
                // 静默失败
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 1. 最先初始化早期日志（使用内部存储，不需要权限）
        initEarlyLog()

        // 2. 记录应用启动
        logEarly("========== 应用启动 ==========")
        logEarly("时间: ${getCurrentTime()}")
        logEarly("包名: $packageName")
        logEarly("==============================")

        // 3. 设置全局异常捕获
        setupGlobalExceptionHandler()

        // 4. 尝试初始化 LogUtils（外部存储日志）
        try {
            LogUtils.init()
            logEarly("LogUtils 初始化成功")
        } catch (e: Exception) {
            logEarly("LogUtils 初始化失败: ${e.message}")
        }

        logEarly("Application onCreate 完成")
    }

    /**
     * 初始化早期日志（使用内部存储）
     */
    private fun initEarlyLog() {
        if (earlyLogInitialized) return

        try {
            val logDir = File(filesDir, LOG_DIR)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            earlyLogFile = File(logDir, "$EARLY_LOG_PREFIX$timeStamp$LOG_SUFFIX")

            earlyLogInitialized = true
        } catch (e: Exception) {
            // 静默失败
        }
    }

    /**
     * 记录早期日志
     */
    private fun logEarly(message: String) {
        if (earlyLogFile == null) return

        try {
            val timeStamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logLine = "[$timeStamp] $message\n"
            FileWriter(earlyLogFile, true).use {
                it.write(logLine)
            }
        } catch (e: Exception) {
            // 静默失败
        }
    }

    /**
     * 设置全局异常捕获
     */
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // 记录崩溃信息到早期日志
                logEarly("========== 应用崩溃 ==========")
                logEarly("崩溃线程: ${thread.name}")
                logEarly("崩溃时间: ${getCurrentTime()}")
                logEarly("异常信息: ${throwable.message}")
                logEarly("异常类型: ${throwable.javaClass.name}")
                logEarly("堆栈信息:")

                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()
                logEarly(stackTrace)

                logEarly("==============================")

                // 同时保存单独的崩溃日志文件
                saveCrashLog(throwable)

            } catch (e: Exception) {
                // 静默失败
            }

            // 继续执行默认的异常处理（让应用崩溃）
            defaultHandler?.uncaughtException(thread, throwable)
        }

        logEarly("全局异常捕获已设置")
    }

    /**
     * 保存崩溃日志到单独文件
     */
    private fun saveCrashLog(throwable: Throwable) {
        try {
            val logDir = File(filesDir, LOG_DIR)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val crashFile = File(logDir, "$CRASH_LOG_PREFIX$timeStamp$LOG_SUFFIX")

            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))

            val crashContent = buildString {
                append("========== AlipayRestart 崩溃日志 ==========\n")
                append("时间: ${getCurrentTime()}\n")
                append("包名: $packageName\n")
                append("异常: ${throwable.message}\n")
                append("类型: ${throwable.javaClass.name}\n")
                append("============================================\n\n")
                append("堆栈信息:\n")
                append(sw.toString())
                append("\n============================================\n")
            }

            FileWriter(crashFile).use {
                it.write(crashContent)
            }

        } catch (e: Exception) {
            // 静默失败
        }
    }

    /**
     * 获取当前时间字符串
     */
    private fun getCurrentTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
