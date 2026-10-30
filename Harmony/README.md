# EndfieldChargingAnim · Harmony 版

> 本目录是**华为鸿蒙 / EMUI 免 root 版**。有 root 的一加 5T 请使用同级目录 [`../OnePlus5T(Root)`](../OnePlus5T(Root))；两个工程共用同一套渲染与动画代码，只是清单与后台保活方式不同。

在插拔充电器时播放《明日方舟：终末地》（Arknights: Endfield）风格"超充模式"HUD 动画的 Android 应用，**面向华为鸿蒙 / EMUI（Android 兼容层）设备，免 root、无需 Xposed 框架**。

真机验证：HUAWEI OCE-AN10，HarmonyOS / EMUI，Android 12（API 31）。

本项目改编自两个上游项目：

- **[NingmengLemon/EndfieldChargingAnim](https://github.com/NingmengLemon/EndfieldChargingAnim)** —— 代码基础。原项目是基于 libxposed API 102 的 Xposed 模块，通过 Hook SystemUI 替换 ROM 充电动画（适配 HyperOS / OxygenOS）。**本改编版已移除全部 Xposed / Hook 代码**，原项目的逆向分析资料作为历史归档保留在 [docs/](docs/README.md)。
- **[QinAnze/zmd-charge](https://github.com/QinAnze/zmd-charge)** —— 视觉规格来源（Avalonia 桌面端实现）。胶囊几何形状、配色、16 条动画轨道与每一个 cubic-bezier 控制点均以该项目源码为规格移植。喜欢这个效果欢迎去给原项目点 Star。

> Fully Powered by DeepSeek V4.1 Flash.

## 效果

- **插入充电**：完整三态动画——闪电图标弹出、胶囊撑高为圆角矩形显示「超充模式」、涟漪扩散、标题与电量数字依次呈现、停留后整体缩小收回
- **拔出充电**：简化动画——只弹电量圆胶囊，内容快速显现后收回
- HUD 显示电量百分比与电池容量（mAh，已充电量 = 总容量 × 百分比）
- 桌面、其他应用内、熄屏状态下均可弹出；插电时可自动点亮屏幕
- 中英文跟随系统语言（可手动切换）

## 工作原理

免 root 模式下应用是一个普通 App，无法 Hook SystemUI，也无法抑制华为自带的充电弹窗，只能自己监听充电事件并拉起一个全屏 Activity 播放 HUD：

```
充电广播 ──► StandaloneMonitorService（常驻前台服务，进程不被冻结）
              └─ 服务内动态注册的 BroadcastReceiver 收到 ACTION_POWER_CONNECTED / DISCONNECTED
                   └─ ChargeEvents（2 秒去抖）
                        └─ StandaloneLauncher ──► StandaloneChargeActivity（全屏、可在锁屏之上显示）
                                                     └─ EndfieldHudView 播放动画
```

### 为绕过 EMUI 限制所做的关键处理

1. **常驻前台服务 + 动态接收器**：实测华为 iaware 电源管理会冻结后台应用并丢弃隐式充电广播，清单接收器在桌面 / 其他 App 下收不到事件。前台服务（FGS，类型 `specialUse`）进程免于冻结，其动态注册的接收器随活进程同步收到事件。服务随独立模式开关启停，通知栏有一条最低优先级常驻通知。清单接收器 `ChargeEventReceiver` 作为兜底保留。
2. **悬浮窗权限（`SYSTEM_ALERT_WINDOW`）**：仅有前台服务仍不够——EMUI 会以 `activity start fail: Background activity start` 静默拒绝后台拉起界面。授予"显示在其他应用上层"后获得 BAL 豁免，桌面、其他 App、熄屏均可弹出 HUD。
3. **全屏意图通知兜底**：直接 startActivity 失败时，通过带全屏 Intent（full-screen intent）的通知兜底拉起。
4. **事件去抖**：清单接收器与服务动态接收器可能同时命中同一事件，2 秒内重复事件忽略。
5. **电池容量**：无 root 读不到电池 sysfs（华为节点 `/sys/class/power_supply/Battery` 权限封闭）。首次启动通过反射隐藏 API `com.android.internal.os.PowerProfile#getBatteryCapacity()` 探测设计容量（真机返回 4200 mAh）；失败回退 **1000 mAh** 示例值；用户可在设置页手动修改，保存时限制在 500–20000 mAh。实时电量百分比、电压取自粘性广播 `ACTION_BATTERY_CHANGED`。

### 限制（请务必知悉）

- **无法隐藏华为系统自带的充电动画 / 冒泡提示**——抑制 SystemUI 动画需要系统权限或 Xposed，免 root 做不到。
- **不保证开机自启**：手机重启后需**手动打开一次 App**（开关保持开启即会重新启动监控服务），之后正常使用。应用不申请开机广播权限。
- 熄屏深睡（Doze）期间的拔出广播投递属于尽力而为，不保证 100% 送达。

## 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或直接把 APK 传到手机点击安装（需允许"安装未知来源应用"）。

首次使用按以下顺序授权（均为一次性设置）：

1. 打开 **Endfield Charge**，打开 **Enable standalone charge animation** 开关，允许通知权限
2. 点击 **Display over other apps: GRANT (required)**，在系统设置中允许"显示在其他应用上层"——**必授**，否则后台无法弹出 HUD
3. 点击 **Battery: allow background**，允许忽略电池优化
4. 点击 **App info: enable auto-start**，在应用信息页 → 电池 → 启动管理中建议放开自启动 / 后台活动（不同鸿蒙版本路径略有差异）
5. 点击 **Test charge / Test unplug** 立即验证效果

重启手机后重新打开一次 App 即可恢复监控。

## 设置项

| 设置 | 默认值 | 说明 |
| --- | --- | --- |
| Enable standalone charge animation | 关 | 免 root 模式总开关，同时启停监控服务 |
| battery total capacity (mAh) | 自动探测，失败 1000 | 电池总容量，可手动 Save 或重新 Auto detect（500–20000） |
| top offset | 8 dp | HUD 距屏幕挖孔 / 状态栏顶部的距离 |
| width | 屏幕的 92% | HUD 宽度占比 |
| duration | 6.0 s | 动画总时长，范围 3–10 s（入场段固定 2.52 s） |
| bounce | 0.275 | BackOut 回弹过冲量 |
| ripple intensity / spread | 1.0 | 涟漪强度与扩散 |
| Wake the screen on plug | 开 | 插电时点亮屏幕 |
| Play on unplug | 开 | 拔出时播放简化动画 |
| language | Auto | 自动跟随系统 / 中文 / English |

设置页同时是预览器：拖动 cue 滑条可逐帧查看动画，Play charge / Play unplug 在应用内完整播放。

## 编译

### 环境要求

- **JDK 17**
- **Android SDK**：`platforms;android-36`、`build-tools;36.0.0`
- Gradle 8.12（已随 Wrapper 提供，无需单独安装）
- `minSdk` 31 / `targetSdk` 35 / `compileSdk` 36

如本机 SDK 不在默认位置，在仓库根目录创建 `local.properties`（已被 git 忽略）：

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

Windows（PowerShell）：

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest assembleDebug
```

macOS / Linux：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # 仅 macOS 示例
./gradlew testDebugUnitTest assembleDebug
```

> 国内网络若卡在 `dl.google.com` 下载依赖超时，可在 `~/.gradle/init.gradle` 配置阿里云 Maven 镜像，仓库构建文件无需改动。

### 产物

| 任务 | 产物 | 说明 |
| --- | --- | --- |
| `assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` | debug 密钥签名，可直接安装 |
| `assembleRelease` | `app/build/outputs/apk/release/app-release-unsigned.apk` | 未配置 release 签名，需自行签名 |

## 项目结构

```
app/src/main/java/com/lemoneko/endfieldcharge/
  EndfieldApp.kt               Application，启动设置仓库
  core/                        ROM 无关的共享核心
    BatterySnapshot.kt         电池快照（百分比/电压/mAh 容量）
    HudMetrics.kt              顶部间距等尺寸计算
    HudPlayMode.kt             CHARGE / UNPLUG 两种时间轴枚举
    HudLog.kt                  日志
    timeline/                  移植自 zmd-charge 的动画核心（无 Android import，可 JVM 单测）
      HudCues.kt / HudState.kt / HudTimeline.kt / AnimationOptions.kt
    settings/                  设置模型、JSON 编解码（HudSettings / HudSettingsJson / ContentSettings）
  standalone/                  免 root 独立模式
    StandaloneMonitorService.kt   常驻前台服务 + 动态充电广播接收器
    ChargeEventReceiver.kt        清单接收器（兜底）
    ChargeEvents.kt               插/拔事件共用处理（2 秒去抖、开关与设置判断）
    StandaloneChargeActivity.kt   全屏 HUD 宿主（showWhenLocked / turnScreenOn，真实电量数据）
    StandaloneLauncher.kt         后台拉起 HUD，失败时走全屏意图通知
    StandaloneNotifier.kt         全屏意图通知
    StandaloneScreen.kt           亮屏 / 解锁处理
    StandalonePrefs.kt            开关与容量存取、PowerProfile 容量探测
  settings/
    SettingsProvider.kt        应用内 ContentProvider（authority <包名>.settings）
    SettingsRepository.kt      设置读写 / 观察
  ui/
    EndfieldHudView.kt         Canvas HUD 渲染器
    HudStrings.kt              中英文文案
    HudTypefaces.kt            Inter 可变字体加载
    MainActivity.kt            设置 + 预览界面
  debug/
    FrameSheetRenderer.kt      离屏逐帧渲染 PNG
app/src/main/assets/fonts/     Inter-Variable.ttf（SIL OFL，见 Inter-OFL.txt）
app/src/test/                  JVM 单元测试（时间轴不变量、设置 JSON）
docs/                          上游 Xposed 时代的逆向分析（历史归档，见 docs/README.md）
tools/capture-reference-hud.ps1  逐帧抓取桌面参考实现画面做 A/B 对比（Windows）
```

## 调试

```bash
# 监控服务是否在跑、是否为前台服务
adb shell dumpsys activity services com.lemoneko.endfieldcharge

# 悬浮窗权限状态（应为 allow）
adb shell appops get com.lemoneko.endfieldcharge SYSTEM_ALERT_WINDOW

# 充电事件相关日志
adb logcat -s EndfieldCharge/Rx:* EndfieldCharge/Svc:* EndfieldCharge/Launch:* EndfieldCharge/Hud:*

# 离屏逐帧渲染 PNG（输出到应用外部存储 files/frames）
adb shell am start -n com.lemoneko.endfieldcharge/.ui.MainActivity --ez renderSheets true
adb pull /sdcard/Android/data/com.lemoneko.endfieldcharge/files/frames

# 直接打开指定帧 / 拔线时间轴的预览
adb shell am start -n com.lemoneko.endfieldcharge/.ui.MainActivity --ei cue 250
adb shell am start -n com.lemoneko.endfieldcharge/.ui.MainActivity --ez unplug true
```

时间轴不变量已移植为 JVM 单元测试：`./gradlew testDebugUnitTest`。

## 免责声明

本项目仅作学习研究使用，不推荐尝试或传播，因此不会发布 Release。

**作者及贡献者不对因使用（或无法使用）本项目而造成的任何直接或间接后果负责**，包括但不限于：

- 手机变砖、无法开机、系统异常、数据丢失、硬件损坏；
- 电池异常（鼓包、过热、加速老化等）、充电故障；
- 因授予悬浮窗 / 后台活动等敏感权限、修改系统设置导致的保修失效；
- 常驻前台服务带来的额外耗电，以及与系统电源管理策略冲突引起的任何异常。

一旦安装或使用本项目，即视为你已理解并自愿承担全部风险；作者及贡献者不承担任何责任，也没有义务提供修复或赔偿。

## 许可证

- 应用代码：**AGPL-3.0**（见 [LICENSE](LICENSE)）
- 内置 Inter 字体：SIL Open Font License（见 `app/src/main/assets/fonts/Inter-OFL.txt`）
