# HookXbud

HookXbud 是面向小步点（`run.xbud.android`）的独立 Android Hook 模块，使用 LSPosed Modern API 102。它不包含启动界面、应用内日志管理或更新页面，只保留 Hook 功能。

## 安装

安装 APK 后，在 LSPosed 中启用 HookXbud 模块并勾选小步点作用域，然后完全退出并重新打开目标应用。Hook 状态与规则日志可在 LSPosed 日志中查看。旧版 AdFree 如仍启用，请先停用以避免重复注入。

当前规则基于目标版本 2.6.8 调研；其他版本会尽力运行，但兼容性尚未验证。规则会屏蔽已识别的普通广告和激励广告，不保留奖励流程。强制拦截可能影响启动检查、登录或奖励入口，也不保证拦截全部广告网络请求。

## 独立构建

使用 Windows PowerShell 7（`pwsh`），需要 JDK 17、Android SDK Platform 37 与 Build Tools 37.0.0。

```powershell
$env:JAVA_HOME = '你的 JDK 17 目录'
$env:ANDROID_HOME = '你的 Android SDK 目录'
.\gradlew.ps1 assembleDebug
```

Debug APK 位于 `build/outputs/apk/debug/`。签名配置齐全时，Debug 与 Release 都使用发布证书。

## Release 签名

将 `signing.local.properties.example` 复制为 `signing.local.properties` 并填写本项目的签名配置；签名配置和私钥不会提交到 Git。GitHub Actions 需要在本仓库配置以下 Repository Secrets：

- `HOOK_KEYSTORE_BASE64`
- `HOOK_KEYSTORE_PASSWORD`
- `HOOK_KEY_ALIAS`
- `HOOK_KEY_PASSWORD`

将 `build.gradle.kts` 的版本号更新后，推送匹配的版本标签即可触发本仓库的发布工作流：

```powershell
git tag v0.12.0
git push origin v0.12.0
```

工作流会构建并发布本项目的 Debug 和 Release APK。
