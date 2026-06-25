package com.example.alipayrestart

import android.content.Context
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.annotation.RequiresApi
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed 模块主入口
 * 作用域：支付宝 (com.eg.android.AlipayGphone)
 */
class MainHook : IXposedHookLoadPackage {

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 只处理支付宝包
        if (lpparam.packageName != TARGET_PACKAGE) {
            return
        }

        XposedBridge.log("AlipayRestart: 支付宝进程加载成功，开始注入...")

        try {
            // Hook Application 的 onCreate 方法
            val appClass = lpparam.classLoader.loadClass("android.app.Application")
            XposedHelpers.findAndHookMethod(
                appClass,
                "onCreate",
                object : de.robv.android.xposed.XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.thisObject as Context
                        XposedBridge.log("AlipayRestart: Application onCreate 触发")

                        // 延迟添加快捷方式
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                ShortcutHook.ensureRestartShortcut(context)
                                XposedBridge.log("AlipayRestart: 初始快捷方式添加完成")
                            } catch (e: Exception) {
                                XposedBridge.log("AlipayRestart: 初始添加快捷方式失败 - ${e.message}")
                            }
                        }, 3000)
                    }
                }
            )

            // Hook ShortcutManager 的 setDynamicShortcuts 方法，防止支付宝覆盖我们的快捷方式
            try {
                val shortcutManagerClass = lpparam.classLoader.loadClass("android.content.pm.ShortcutManager")

                // Hook setDynamicShortcuts
                XposedHelpers.findAndHookMethod(
                    shortcutManagerClass,
                    "setDynamicShortcuts",
                    MutableList::class.java,
                    object : de.robv.android.xposed.XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val context = getContextFromShortcutManager(param.thisObject)
                                if (context != null) {
                                    XposedBridge.log("AlipayRestart: 检测到 setDynamicShortcuts 调用，重新添加快捷方式")
                                    ShortcutHook.ensureRestartShortcut(context)
                                }
                            } catch (e: Exception) {
                                XposedBridge.log("AlipayRestart: setDynamicShortcuts hook 失败 - ${e.message}")
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
                                // 静默失败
                            }
                        }
                    }
                )

                XposedBridge.log("AlipayRestart: ShortcutManager hook 成功")
            } catch (e: Exception) {
                XposedBridge.log("AlipayRestart: Hook ShortcutManager 失败 - ${e.message}")
            }

        } catch (e: Exception) {
            XposedBridge.log("AlipayRestart: Hook 初始化失败 - ${e.message}")
            e.printStackTrace()
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
