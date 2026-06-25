package com.example.alipayrestart

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed 模块主入口
 * 作用域：模块自身（用于检测激活状态）
 */
class MainHook : IXposedHookLoadPackage {

    companion object {
        // 支付宝包名
        const val TARGET_PACKAGE = "com.eg.android.AlipayGphone"
        // 模块自身包名
        const val MODULE_PACKAGE = "com.example.alipayrestart"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 只处理模块自身的包
        if (lpparam.packageName != MODULE_PACKAGE) return

        try {
            // 标记模块已激活（通过文件标记方式，解决不同 ClassLoader 不共享的问题）
            ModuleActivation.markActivatedWithDataDir(lpparam.appInfo.dataDir)

            // 初始化日志
            LogUtils.init()
            LogUtils.i("MainHook", "模块已成功激活，包名: ${lpparam.packageName}")
            LogUtils.i("MainHook", "LSPosed API 版本: 102")

        } catch (e: Throwable) {
            // 这里可以直接用 XposedBridge，因为是在 Hook 进程中
            de.robv.android.xposed.XposedBridge.log("AlipayRestart: 模块初始化失败 - ${e.message}")
        }
    }
}
