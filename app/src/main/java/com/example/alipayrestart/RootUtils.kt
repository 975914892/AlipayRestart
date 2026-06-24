package com.example.alipayrestart

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Root 权限工具类
 * 用于执行 shell 命令实现 force-stop 等系统级操作
 */
object RootUtils {

    /**
     * 检查是否有 root 权限
     */
    fun checkRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 执行 root 命令
     * @param command 要执行的命令
     * @return 命令输出结果
     */
    fun executeCommand(command: String): String {
        val output = StringBuilder()
        var process: Process? = null
        var os: DataOutputStream? = null
        var reader: BufferedReader? = null

        try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            reader = BufferedReader(InputStreamReader(process.inputStream))

            // 写入命令
            os.writeBytes("$command\n")
            os.flush()

            // 写入 exit 命令结束 shell
            os.writeBytes("exit\n")
            os.flush()

            // 读取输出
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            // 等待进程结束
            process.waitFor()

        } catch (e: Exception) {
            e.printStackTrace()
            output.append("Error: ${e.message}")
        } finally {
            try {
                os?.close()
                reader?.close()
                process?.destroy()
            } catch (e: Exception) {
                // ignore
            }
        }

        return output.toString()
    }

    /**
     * 强行停止指定包名的应用
     */
    fun forceStopPackage(packageName: String): Boolean {
        return try {
            val result = executeCommand("am force-stop $packageName")
            // force-stop 成功时通常没有输出
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 启动指定应用
     */
    fun launchPackage(packageName: String): Boolean {
        return try {
            val result = executeCommand(
                "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
            )
            result.contains("Events injected: 1")
        } catch (e: Exception) {
            false
        }
    }
}
