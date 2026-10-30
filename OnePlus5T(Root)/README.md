# OnePlus5T(Root) · Endfield Charge（Root 版）

插拔充电器时播放《明日方舟：终末地》风格「超充模式」HUD 的 Android 应用，**专为一加 5T（已 root / Magisk）构建**。

与 [`../Harmony`](../Harmony) 是同一套渲染与时间轴代码，区别只在运行环境：本版面向 Android 8.0/8.1（API 26/27）、有 root 的一加 5T，因此**恢复了开机自启**，并用 `su` 自动完成权限授予。

改编自：

- [NingmengLemon/EndfieldChargingAnim](https://github.com/NingmengLemon/EndfieldChargingAnim)（代码基础）
- [QinAnze/zmd-charge](https://github.com/QinAnze/zmd-charge)（视觉规格来源，Avalonia 桌面端）

## 与原版（Harmony 目录）的差异

| 项目 | Harmony（免 root） | 本版（Root） |
| --- | --- | --- |
| 目标设备 | 华为 OCE-AN10 / 鸿蒙 Android 12 | 一加 5T / Android 8.0–8.1 |
| minSdk | 31 | 26 |
| targetSdk | 35 | 28（Oreo 时代行为，避免新系统限制干扰） |
| 开机自启 | 无（EMUI 拦截第三方开机广播） | **有**，`BootReceiver` 监听 `BOOT_COMPLETED` / `QUICKBOOT_POWERON` |
| 悬浮窗权限 | 必须手动授予 | `su` 自动 `appops set ... SYSTEM_ALERT_WINDOW allow` |
| 电池优化白名单 | 手动 | `su` 自动 `dumpsys deviceidle whitelist +<包名>` |
| 后台拉起保底 | 全屏意图通知 | 直接 startActivity → `su -c am start` → 全屏意图通知 |
| applicationId | `com.lemoneko.endfieldcharge` | `com.lemoneko.endfieldcharge.root`（可与原版共存） |

## 保证「没打开过 App 也能弹」

1. **开机自启**：`BootReceiver` 在开机完成后读取开关状态，若已开启则拉起前台服务，并顺带重新执行 root 授权（防止 OTA / 权限重置后失效）。
2. **常驻前台服务**：`StandaloneMonitorService` 进程内动态注册充电广播接收器，`START_STICKY`，被系统回收后自动重建。
3. **清单接收器兜底**：`ChargeEventReceiver` 同时监听插/拔电，服务异常时仍能收到。
4. **root 启动兜底**：万一后台拉起界面被拒，改用 `su -c am start` 以 uid 0 启动 HUD；再失败才走全屏意图通知。

一次开启后即可不再打开 App：插电/拔电都会显示。

## 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或把 APK 传到手机点击安装。已 root 的设备首次打开时 Magisk 会请求授权，**允许**即可（选择"永久记住"）。

首次使用：

1. 打开 **Endfield Charge (Root)** → 打开 **Enable standalone charge animation**
2. 首次会自动弹 Magisk 授权；授权后自动完成悬浮窗 + 电池白名单（也可手动点 **Grant via root**）
3. 若 root 未授权，可退回手动：**Display over other apps** / **Battery: allow background**
4. 点 **Test charge / Test unplug** 验证效果
5. 之后重启手机无需再打开 App，插电即显示

## 设置项

| 设置 | 默认值 | 说明 |
| --- | --- | --- |
| Enable standalone charge animation | 关 | 总开关，启停前台监控服务 |
| battery total capacity (mAh) | 自动探测，失败 1000 | 电池总容量（5T 实测 3300），可手动 Save / Auto detect |
| top offset | 8 dp | HUD 距屏幕顶部距离 |
| width | 屏幕 92% | HUD 宽度占比 |
| duration | 6.0 s | 动画总时长（3–10 s） |
| bounce | 0.275 | 回弹过冲 |
| ripple intensity / spread | 1.0 | 涟漪强度与扩散 |
| Wake the screen on plug | 开 | 插电点亮屏幕 |
| Play on unplug | 开 | 拔出时播放简化动画 |
| language | Auto | 跟随系统 / 中文 / English |

## 编译

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（debug 签名，可直接安装）。

环境：JDK 17、Android SDK `platforms;android-36` / `build-tools;36.0.0`、Gradle 8.12（Wrapper 自带）。
如 SDK 不在默认位置，在本目录建 `local.properties`：`sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk`。

## 调试

```bash
# 服务是否在前台运行
adb shell dumpsys activity services com.lemoneko.endfieldcharge.root

# root 授权是否生效（应为 allow）
adb shell appops get com.lemoneko.endfieldcharge.root SYSTEM_ALERT_WINDOW

# 日志
adb logcat -s EndfieldCharge/Rx:* EndfieldCharge/Svc:* EndfieldCharge/Launch:* \
             EndfieldCharge/Hud:* EndfieldCharge/Boot:* EndfieldCharge/Su:*
```

## 限制

- **无法隐藏系统自带的充电提示**：一加/氧 OS 的充电提示由 SystemUI 绘制，不 Hook 系统界面就压不住，本版只做叠加显示。
- 本版按 Android 8 行为设计（`targetSdk 28`），未在 Android 12+ 上验证；那些系统请用 `../Harmony` 版本。
- 熄屏深睡（Doze）期间拔出广播属尽力而为。

## 免责声明

本项目仅作学习研究使用。**作者及贡献者不对使用本项目造成的任何后果负责**，包括但不限于变砖、无法开机、数据丢失、电池异常、保修失效等。root、刷机、授予高权限均存在不可逆风险，操作前请自行备份并评估；一旦安装或使用即视为你已理解并自愿承担全部风险。

## 许可证

应用代码 AGPL-3.0（见 [LICENSE](LICENSE)）；内置 Inter 字体为 SIL OFL（见 `app/src/main/assets/fonts/Inter-OFL.txt`）。
