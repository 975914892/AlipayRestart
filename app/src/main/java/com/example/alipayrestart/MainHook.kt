package com.example.alipayrestart

import android.content.Context
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.annotation.RequiresApi
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed 模块主入口
 * 作用域：支付宝 (com.eg.android.AlipayGphone)
 */
class MainHook : IXposedHookLoadPackage {

    private val TAG = "MainHook"

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 只处理支付宝包
        if (lpparam.packageName != TARGET_PACKAGE) {
            return
        }

        // 初始化日志
        LogUtils.init()
        LogUtils.i(TAG, "支付宝进程加载成功，开始注入...")
        LogUtils.i(TAG, "进程名: ${lpparam.processName}")

        try {
            // Hook Application 的 onCreate 方法
            val appClass = lpparam.classLoader.loadClass("android.app.Application")
            XposedHelpers.findAndHookMethod(
                appClass,
                "onCreate",
                object : de.robv.android.xposed.XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.thisObject as Context
                        LogUtils.i(TAG, "Application.onCreate 触发")

                        // 延迟添加快捷方式
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                LogUtils.d(TAG, "开始添加初始快捷方式")
                                ShortcutHook.ensureRestartShortcut(context)
                                LogUtils.i(TAG, "初始快捷方式添加完成")
                            } catch (e: Exception) {
                                LogUtils.e(TAG, "初始添加快捷方式失败", e)
                            }
                        }, 3000)
                    }
                }
            )

            // Hook ShortcutManager 的方法，防止支付宝覆盖我们的快捷方式
            try {
                val shortcutManagerClass = lpparam.classLoader.loadClass("android.content.pm.ShortcutManager")

                // Hook setDynamicShortcuts
                XposedHelpers.findAndHookMethod(
                    shortcutManagerClass,
                    "setDynamicShortcuts",
                    MutableList::class.java,
                    object : de.robv.android.xposed.XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val shortcutList = param.args[0] as? List<*>
                            LogUtils.d(TAG, "检测到 setDynamicShortcuts 调用，传入 ${shortcutList?.size ?: 0} 个快捷方式")
                        }

                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val context = getContextFromShortcutManager(param.thisObject)
                                if (context != null) {
                                    LogUtils.i(TAG, "setDynamicShortcuts 调用完成，重新添加我们的快捷方式")
                                    ShortcutHook.ensureRestartShortcut(context)
                                } else {
                                    LogUtils.e(TAG, "无法从 ShortcutManager 获取 Context")
                                }
                            } catch (e: Exception) {
                                LogUtils.e(TAG, "setDynamicShortcuts hook 失败", e)
                            }
                        }
                    }
                )

                // Hook addDynamicShortcuts
                XposedHelpers.findAndHookMethod(
                    shortcutManagerClass,
                    "addDynamicShortcuts",
                    MutableList::class.java,
                    object : de.robv.android.xposed.XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val context = getContextFromShortcutManager(param.thisObject)
                                if (context != null) {
                                    ShortcutHook.ensureRestartShortcut(context)
                                }
                            } catch (e: Exception) {
                                LogUtils.e(TAG, "addDynamicShortcuts hook 失败", e)
                            }
                        }
                    }
                )

                LogUtils.i(TAG, "ShortcutManager hook 成功")
            } catch (e: Exception) {
                LogUtils.e(TAG, "Hook ShortcutManager 失败", e)
            }

        } catch (e: Exception) {
            LogUtils.e(TAG, "Hook 初始化失败", e)
        }
    }

    /**
     * 从 ShortcutManager 实例中获取 Context
     */
    private fun getContextFromShortcutManager(shortcutManager: Any): Context? {
        return try {
            val mContextField = shortcutManager.javaClass.getDeclaredField("mContext")
            mContextField.isAccessible = true
            mContextField.get(shortcutManager) as? Context
        } catch (e: Exception) {
            try {
                // 备用方案：通过 ActivityThread 获取 ApplicationContext
                val activityThread = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentActivityThread"
                )
                XposedHelpers.callMethod(activityThread, "getApplication") as? Context
            } catch (e2: Exception) {
                null
            }
        }
    }

    companion object {
        const val TARGET_PACKAGE = "com.eg.android.AlipayGphone"
        const val MODULE_PACKAGE = "com.example.alipayrestart"
        const val SHORTCUT_ID = "alipay_restart_shortcut"
    }
}
