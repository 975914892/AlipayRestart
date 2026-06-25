package com.example.alipayrestart

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed 模块主入口
 * 作用域：模块自身（用于检测激活状态）
 */
class MainHook : IXposedHookLoadPackage {

    companion object {
        // 模块是否已激活的标记
        @Volatile
        var isModuleActivated = false
            private set

        // 支付宝包名
        const val TARGET_PACKAGE = "com.eg.android.AlipayGphone"
        // 模块自身包名
        const val MODULE_PACKAGE = "com.example.alipayrestart"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 只处理模块自身的包
        if (lpparam.packageName != MODULE_PACKAGE) return

        try {
            // 标记模块已激活
            isModuleActivated = true

            // 初始化日志
            LogUtils.init()
            LogUtils.i("MainHook", "模块已成功激活，包名: ${lpparam.packageName}")
            LogUtils.i("MainHook", "LSPosed API 版本: 102")

        } catch (e: Throwable) {
            // 这里不能用 LogUtils，因为可能还没初始化
            de.robv.android.xposed.XposedBridge.log("AlipayRestart: 模块初始化失败 - ${e.message}")
        }
    }
}
