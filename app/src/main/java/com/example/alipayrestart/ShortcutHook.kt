package com.example.alipayrestart

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * 快捷方式注入逻辑
 * 向支付宝的动态快捷方式中添加"重启支付宝"选项
 */
object ShortcutHook {

    private val TAG = "ShortcutHook"

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun ensureRestartShortcut(context: Context) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: run {
            LogUtils.e(TAG, "ShortcutManager 获取失败")
            return
        }

        try {
            // 检查是否已存在该快捷方式
            val existingShortcuts = shortcutManager.dynamicShortcuts
            LogUtils.d(TAG, "当前已有 ${existingShortcuts.size} 个动态快捷方式")
            existingShortcuts.forEachIndexed { index, shortcut ->
                LogUtils.d(TAG, "  [$index] id=${shortcut.id}, label=${shortcut.shortLabel}")
            }

            if (existingShortcuts.any { it.id == MainHook.SHORTCUT_ID }) {
                LogUtils.d(TAG, "快捷方式已存在，跳过添加")
                return
            }

            LogUtils.i(TAG, "开始添加重启快捷方式...")

            // 创建重启 Intent - 跳转到模块的透明 Activity
            val restartIntent = Intent().apply {
                component = ComponentName(
                    MainHook.MODULE_PACKAGE,
                    "${MainHook.MODULE_PACKAGE}.RestartActivity"
                )
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            LogUtils.d(TAG, "目标组件: ${restartIntent.component}")

            // 创建快捷方式信息
            val shortcutInfo = ShortcutInfo.Builder(context, MainHook.SHORTCUT_ID)
                .setShortLabel("重启支付宝")
                .setLongLabel("强行停止并重启支付宝")
                .setDisabledMessage("需要启用模块才能使用")
                .setIntent(restartIntent)
                .setRank(0) // 排在第一位
                .setIcon(Icon.createWithResource(context, android.R.drawable.ic_menu_rotate))
                .build()

            // 先尝试添加
            val success = shortcutManager.addDynamicShortcuts(listOf(shortcutInfo))
            if (success) {
                LogUtils.i(TAG, "快捷方式添加成功")
            } else {
                LogUtils.w(TAG, "快捷方式添加失败，尝试更新")
                // 添加失败（可能是数量超限），尝试更新
                try {
                    shortcutManager.updateShortcuts(listOf(shortcutInfo))
                    LogUtils.i(TAG, "快捷方式更新成功")
                } catch (e: Exception) {
                    LogUtils.e(TAG, "快捷方式更新也失败", e)
                }
            }

            // 验证是否添加成功
            val afterShortcuts = shortcutManager.dynamicShortcuts
            val exists = afterShortcuts.any { it.id == MainHook.SHORTCUT_ID }
            LogUtils.i(TAG, "添加后验证: ${if (exists) "成功" else "失败"}，当前共 ${afterShortcuts.size} 个快捷方式")

        } catch (e: Exception) {
            LogUtils.e(TAG, "确保快捷方式存在时出错", e)
        }
    }
}
