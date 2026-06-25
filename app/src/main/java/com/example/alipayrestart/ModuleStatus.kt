package com.example.alipayrestart

/**
 * 模块状态和常量类
 * 独立于 Xposed API，避免外部类直接引用 MainHook 导致类加载失败
 */
object ModuleStatus {

    /**
     * 支付宝包名
     */
    const val TARGET_PACKAGE = "com.eg.android.AlipayGphone"

    /**
     * 模块自身包名
     */
    const val MODULE_PACKAGE = "com.example.alipayrestart"

    /**
     * 模块是否已激活
     * 由 MainHook 在 handleLoadPackage 中设置为 true
     */
    @Volatile
    var isActivated = false
        internal set
}
