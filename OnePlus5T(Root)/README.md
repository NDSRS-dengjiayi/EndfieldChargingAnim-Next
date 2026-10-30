# OnePlus5T(Root) · Endfield Charge（Root 版）

插拔充电器时播放《明日方舟：终末地》风格「超充模式」HUD 的 Android 应用，**专为一加 5T（已 root / Magisk）构建**。

与 [`../Harmony`](../Harmony) 是同一套渲染与时间轴代码，区别只在运行环境：本版面向有 root（Magisk）的一加 5T（氧 OS / H2OS 10.0.2，Android 10），因此**恢复了开机自启**，并用 `su` 自动完成权限授予与保活配置。

改编自：

- [NingmengLemon/EndfieldChargingAnim](https://github.com/NingmengLemon/EndfieldChargingAnim)（代码基础）
- [QinAnze/zmd-charge](https://github.com/QinAnze/zmd-charge)（视觉规格来源，Avalonia 桌面端）

## 与原版（Harmony 目录）的差异

| 项目 | Harmony（免 root） | 本版（Root） |
| --- | --- | --- |
| 目标设备 | 华为 OCE-AN10 / 鸿蒙 Android 12 | 一加 5T（A5010）/ H2OS 10.0.2（Android 10） |
| minSdk | 31 | 26 |
| targetSdk | 35 | 28（Oreo 时代行为，避免新系统限制干扰） |
| 总开关默认值 | 关 | **开**（装好即生效，关掉才是退出） |
| 开机自启 | 无（EMUI 拦截第三方开机广播） | **有**，`BootReceiver` + Magisk `service.d` 钩子（H2OS 会丢弃第三方 `BOOT_COMPLETED`，只有钩子真正生效） |
| 悬浮窗权限 | 必须手动授予 | `su` 自动 `appops set ... SYSTEM_ALERT_WINDOW allow` |
| 电池优化白名单 | 手动 | `su` 自动 `dumpsys deviceidle whitelist +<包名>` |
| 后台运行限制 | 手动 | `su` 自动 `appops set ... RUN_ANY_IN_BACKGROUND allow` |
| 后台拉起保底 | 全屏意图通知 | 直接 startActivity → `su -c am start` → 全屏意图通知 |
| 充电事件来源 | `ACTION_POWER_CONNECTED` | 该广播 + `ACTION_BATTERY_CHANGED` 的 `EXTRA_PLUGGED` 边沿检测（防某些 ROM 只发后者） |
| applicationId | `com.lemoneko.endfieldcharge` | `com.lemoneko.endfieldcharge.root`（可与原版共存） |

## 保证「没打开过 App 也能弹」

实测结论（Android 10 / H2OS）：

- 进程存活时，`ACTION_POWER_CONNECTED` 只投递给**动态注册**的接收器；清单里的 `ChargeEventReceiver` 在后台会被 `BroadcastQueue: Background execution not allowed` 直接丢弃。所以前台服务是必需的，不是可选项。
- 后台 `startActivity` 在 Android 10 上默认被静默拒绝（不抛异常），只有拿到 `SYSTEM_ALERT_WINDOW` 才放行；已 root 的安装会自动 `appops` 授予。
- H2OS 会拦截第三方 `BOOT_COMPLETED`，`BootReceiver` 实际收不到，因此开机自启由 Magisk 钩子完成。

由此形成的保障链：

1. **Magisk 开机钩子**：`/data/adb/service.d/endfieldcharge_boot.sh` 等待 `sys.boot_completed`，然后用 `am start` 启动 `BootProxyActivity`（无窗口的导出 Activity），由它在应用进程内拉起前台服务；失败会重试 5 次。
2. **常驻前台服务**：`StandaloneMonitorService` 动态注册插/拔电与电池广播接收器，`START_STICKY`；每次收到充电事件时还会顺带确保服务在运行。
3. **双事件源**：同时监听 `ACTION_POWER_CONNECTED/DISCONNECTED` 和 `ACTION_BATTERY_CHANGED`，用 `EXTRA_PLUGGED` 的 0↔非0 变化合成同样的事件，带 2 秒去抖。
4. **清单接收器兜底**：`ChargeEventReceiver` 在应用处于前台时仍可用（后台会被系统丢弃，属正常）。
5. **root 启动兜底**：没有悬浮窗权限时直接走 `su -c am start`（uid 0 不受后台启动限制），失败才降级为全屏意图通知。

因此：装好并授权 root 一次后，重启、锁屏、深睡插拔都会显示，**不需要再打开 App**。

## 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或把 APK 传到手机点击安装。已 root 的设备首次打开时 Magisk 会请求授权，**允许**即可（选择"永久记住"）。

首次使用：

1. 打开 **Endfield Charge (Root)**，总开关默认已开；Magisk 会弹授权，**允许**并选"永久记住"
2. 授权后应用自动完成悬浮窗、Doze 白名单、后台运行、Magisk 开机钩子（状态栏会显示 `root: ... granted`）
3. 点 **Test charge / Test unplug** 立刻验证效果
4. 之后重启手机无需再打开 App，插拔即显示

想临时退出：把 **Enable standalone charge animation** 关掉，会停服务并删除 Magisk 钩子（重启后也不再自启）。

## 设置项

| 设置 | 默认值 | 说明 |
| --- | --- | --- |
| Enable standalone charge animation | 开 | 总开关，启停前台监控服务与开机钩子 |
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
             EndfieldCharge/Hud:* EndfieldCharge/Boot:* EndfieldCharge/BootProxy:* EndfieldCharge/Su:*

# 开机钩子的执行记录
adb shell su -c "cat /data/local/tmp/endfieldcharge_boot.log"

# 模拟插拔（不拔线也能测）
adb shell cmd battery unplug -f
adb shell cmd battery set usb 1 -f
adb shell cmd battery reset -f
```

## 限制

- **无法隐藏系统自带的充电提示**：一加/氧 OS 的充电提示由 SystemUI 绘制，不 Hook 系统界面就压不住，本版只做叠加显示。
- 本版按 Android 8 行为设计（`targetSdk 28`），在 Android 10 上验证通过；更高版本系统请用 `../Harmony` 版本。
- 熄屏深睡（Doze）期间，拔出广播能否投递取决于系统；本版已通过 `ACTION_BATTERY_CHANGED` 兜底尽量覆盖。
- 开机钩子写入 `/data/adb/service.d`，卸载 App 不会自动删除；需要时可手动删掉该文件。

## 免责声明

本项目仅作学习研究使用。**作者及贡献者不对使用本项目造成的任何后果负责**，包括但不限于变砖、无法开机、数据丢失、电池异常、保修失效等。root、刷机、授予高权限均存在不可逆风险，操作前请自行备份并评估；一旦安装或使用即视为你已理解并自愿承担全部风险。

## 许可证

应用代码 AGPL-3.0（见 [LICENSE](LICENSE)）；内置 Inter 字体为 SIL OFL（见 `app/src/main/assets/fonts/Inter-OFL.txt`）。
