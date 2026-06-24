package com.example.alipayrestart

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed 模块主入口
 * 作用域：支付宝 (com.eg.android.AlipayGphone)
 */
class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 只处理支付宝包
        if (lpparam.packageName != TARGET_PACKAGE) {
            return
        }

        XposedBridge.log("AlipayRestart: 支付宝进程加载成功，开始注入快捷方式...")

        try {
            // Hook Application 的 onCreate 方法，在应用启动完成后添加快捷方式
            val appClass = lpparam.classLoader.loadClass("android.app.Application")
            de.robv.android.xposed.XposedHelpers.findAndHookMethod(
                appClass,
                "onCreate",
                object : de.robv.android.xposed.XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.thisObject as android.content.Context
                        XposedBridge.log("AlipayRestart: Application onCreate 触发，准备添加快捷方式")
                        
                        // 延迟一点执行，确保应用完全初始化
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                ShortcutHook.addRestartShortcut(context)
                                XposedBridge.log("AlipayRestart: 快捷方式添加成功")
                            } catch (e: Exception) {
                                XposedBridge.log("AlipayRestart: 添加快捷方式失败 - ${e.message}")
                                e.printStackTrace()
                            }
                        }, 2000) // 延迟2秒
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("AlipayRestart: Hook Application 失败 - ${e.message}")
            e.printStackTrace()
        }
    }

    companion object {
        const val TARGET_PACKAGE = "com.eg.android.AlipayGphone"
        const val MODULE_PACKAGE = "com.example.alipayrestart"
        const val SHORTCUT_ID = "alipay_restart_shortcut"
    }
}
