# 一键重启支付宝 - LSPosed 模块

## 功能说明
在支付宝的长按图标快捷方式（三维触控）中添加「重启支付宝」选项，点击后会强行停止支付宝并重新前台打开。

## 技术原理
1. **注入方式**：通过 LSPosed 注入支付宝进程
2. **快捷方式添加**：使用 `ShortcutManager.addDynamicShortcuts()` 动态添加快捷方式
3. **强行停止**：通过 Root 权限执行 `am force-stop` 命令（等同于应用详情中的「强行停止」）
4. **重新启动**：通过 `monkey` 命令或 `am start` 重新启动支付宝

## 环境要求
- Android 14 - 16 (API 34+)
- LSPosed / EdXposed 框架
- **Root 权限**（必需，用于执行 force-stop 命令）

## 项目结构
```
AlipayRestart/
├── build.gradle.kts          # 项目级构建配置
├── settings.gradle.kts       # 项目设置
├── gradle.properties         # Gradle 属性
└── app/
    ├── build.gradle.kts      # 应用级构建配置
    ├── proguard-rules.pro    # 混淆规则
    └── src/main/
        ├── AndroidManifest.xml          # 清单文件
        ├── assets/
        │   └── xposed_init              # Xposed 入口
        ├── res/
        │   ├── values/
        │   │   ├── strings.xml          # 字符串资源
        │   │   ├── arrays.xml           # 作用域配置
        │   │   └── themes.xml           # 透明主题
        │   └── drawable/
        │       └── ic_restart.xml       # 模块图标
        └── java/com/example/alipayrestart/
            ├── MainHook.kt              # LSPosed 主入口
            ├── ShortcutHook.kt          # 快捷方式注入逻辑
            ├── RestartActivity.kt       # 重启执行 Activity
            └── RootUtils.kt             # Root 权限工具类
```

## 核心代码说明

### MainHook.kt
- LSPosed 模块入口，实现 `IXposedHookLoadPackage` 接口
- Hook `Application.onCreate()` 方法，在支付宝启动后延迟 2 秒添加快捷方式

### ShortcutHook.kt
- 调用系统 `ShortcutManager` 添加动态快捷方式
- 快捷方式 ID: `alipay_restart_shortcut`
- 点击快捷方式会启动模块的 `RestartActivity`

### RestartActivity.kt
- 透明 Activity，用户无感知
- 检查 Root 权限
- 执行 `am force-stop` 强行停止支付宝
- 等待 500ms 后重新启动支付宝

### RootUtils.kt
- Root 权限检查
- Shell 命令执行封装

## 构建方法

### 方法一：Android Studio 构建（推荐）
1. 使用 Android Studio 打开项目
2. 等待 Gradle 同步完成
3. 点击 Build → Build APK(s)
4. 生成的 APK 在 `app/build/outputs/apk/debug/` 目录下

### 方法二：命令行构建
```bash
# Linux/Mac
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

## 使用方法
1. 安装构建好的 APK
2. 在 LSPosed 管理器中启用模块
3. 勾选作用域：支付宝
4. 强制停止支付宝后重新打开（让模块生效）
5. 长按支付宝图标，即可看到「重启支付宝」选项

## 注意事项
1. **必须有 Root 权限**，否则 force-stop 无法执行
2. 首次启用模块后，需要重启支付宝一次才能看到快捷方式
3. 快捷方式是动态添加的，清除支付宝数据后需要重新打开支付宝才会再次出现
4. 如果快捷方式没有出现，请检查 LSPosed 日志确认模块是否正常加载

## 常见问题

**Q: 长按图标没有看到重启选项？**
A: 请确认：
1. 模块已在 LSPosed 中启用
2. 支付宝已被勾选为作用域
3. 支付宝已经被强制停止并重新打开过
4. 查看 LSPosed 日志是否有 "AlipayRestart" 相关输出

**Q: 点击后提示需要 Root 权限？**
A: 本模块的「强行停止」功能依赖 Root 权限执行系统命令，请确保你的设备已 Root 并授予了权限。

**Q: force-stop 和普通杀后台有什么区别？**
A: force-stop 是系统级的强行停止，会完全终止应用的所有进程，包括后台服务、广播接收器等，等同于在应用详情中点击「强行停止」按钮。普通杀后台只是杀死前台进程，应用可能还会有后台服务在运行。

## 版本历史
- v1.0: 初始版本，支持添加快捷方式和一键重启功能
