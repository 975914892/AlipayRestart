package com.example.alipayrestart

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import de.robv.android.xposed.XposedBridge

/**
 * 快捷方式注入逻辑
 * 向支付宝的动态快捷方式中添加"重启支付宝"选项
 */
object ShortcutHook {

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun addRestartShortcut(context: Context) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: run {
            XposedBridge.log("AlipayRestart: ShortcutManager 获取失败")
            return
        }

        // 检查是否已存在该快捷方式
        val existingShortcuts = shortcutManager.dynamicShortcuts
        if (existingShortcuts.any { it.id == MainHook.SHORTCUT_ID }) {
            XposedBridge.log("AlipayRestart: 快捷方式已存在，跳过添加")
            return
        }

        // 创建重启 Intent - 跳转到模块的透明 Activity
        val restartIntent = Intent().apply {
            component = ComponentName(
                MainHook.MODULE_PACKAGE,
                "${MainHook.MODULE_PACKAGE}.RestartActivity"
            )
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // 创建快捷方式信息
        val shortcutInfo = ShortcutInfo.Builder(context, MainHook.SHORTCUT_ID)
            .setShortLabel("重启支付宝")
            .setLongLabel("强行停止并重启支付宝")
            .setDisabledMessage("需要启用模块才能使用")
            .setIntent(restartIntent)
            .setRank(0) // 排在第一位
            .setIcon(Icon.createWithResource(context, android.R.drawable.ic_menu_rotate))
            .build()

        // 添加动态快捷方式
        val success = shortcutManager.addDynamicShortcuts(listOf(shortcutInfo))
        if (success) {
            XposedBridge.log("AlipayRestart: 快捷方式添加成功")
        } else {
            XposedBridge.log("AlipayRestart: 快捷方式添加失败，尝试更新")
            // 如果添加失败，尝试更新
            try {
                shortcutManager.updateShortcuts(listOf(shortcutInfo))
                XposedBridge.log("AlipayRestart: 快捷方式更新成功")
            } catch (e: Exception) {
                XposedBridge.log("AlipayRestart: 快捷方式更新也失败 - ${e.message}")
            }
        }
    }
}
