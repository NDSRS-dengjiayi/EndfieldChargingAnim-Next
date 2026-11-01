# EndfieldChargingAnim

插拔充电器时播放《明日方舟：终末地》风格「超充模式」HUD 的 Android 应用。仓库按目标设备拆成两个**可独立编译**的工程，共用同一套渲染器、动画时间轴与设置模型，差异只在清单、SDK 版本与「如何让后台弹窗」这一层。

| 目录 | 适用设备 | 系统 | 权限模式 |
| --- | --- | --- | --- |
| [Harmony](Harmony) | 华为 OCE-AN10 / 鸿蒙 EMUI | Android 12（API 31） | 免 root、免 Xposed |
| [OnePlus5T(Root)](OnePlus5T(Root)) | 一加 5T（A5010）/ H2OS 10.0.2 | Android 10（API 29） | root（Magisk） |

| 工程 | minSdk | targetSdk | applicationId |
| --- | --- | --- | --- |
| Harmony | 31 | 35 | `com.lemoneko.endfieldcharge` |
| OnePlus5T(Root) | 26 | 28 | `com.lemoneko.endfieldcharge.root` |

两个包名不同，可同时装在同一台手机上。

## 来源

- **[NingmengLemon/EndfieldChargingAnim](https://github.com/NingmengLemon/EndfieldChargingAnim)** —— 代码基础。原项目是基于 libxposed API 102 的 Xposed 模块，通过 Hook SystemUI 替换 ROM 充电动画。**本改编版已移除全部 Xposed / Hook 代码**，原项目的逆向分析资料作为历史归档保留在 [Harmony/docs/](Harmony/docs/README.md)。
- **[QinAnze/zmd-charge](https://github.com/QinAnze/zmd-charge)** —— 视觉规格来源（Avalonia 桌面端）。胶囊几何形状、配色、16 条动画轨道与每一个 cubic-bezier 控制点均以该项目源码为规格移植。

## 效果

- **插入充电**：完整三态动画——闪电图标弹出、胶囊撑高为圆角矩形显示「超充模式」、涟漪扩散、标题与电量数字依次呈现、停留后整体缩小收回
- **拔出充电**：简化动画——只弹电量圆胶囊，内容快速显现后收回
- HUD 显示电量百分比与电池容量（mAh，已充电量 = 总容量 × 百分比）
- 桌面、其他应用内、熄屏状态下均可弹出；插电时可自动点亮屏幕
- 中英文跟随系统语言（可手动切换）

## 共同工作原理

免 root 模式（Harmony）下应用是普通 App，无法 Hook SystemUI，也无法抑制系统自带的充电弹窗，只能自己监听充电事件并拉起一个全屏 Activity 播放 HUD：

```
充电广播 ──► StandaloneMonitorService（常驻前台服务，进程不被冻结）
              └─ 服务内动态注册的 BroadcastReceiver 收到 ACTION_POWER_CONNECTED / DISCONNECTED
                   └─ ChargeEvents（2 秒去抖）
                        └─ StandaloneLauncher ──► StandaloneChargeActivity（全屏、可在锁屏之上显示）
                                                     └─ EndfieldHudView 播放动画
```

两版都遵循这条链路，各自的差异处理见下面两节。

## 变体一：Harmony（华为，免 root）

### 绕过 EMUI 限制的关键处理

1. **常驻前台服务 + 动态接收器**：实测华为 iaware 会冻结后台应用并丢弃隐式充电广播，清单接收器在桌面 / 其他 App 下收不到事件。前台服务（FGS，类型 `specialUse`）进程免于冻结，动态注册的接收器随活进程同步收到事件。清单接收器 `ChargeEventReceiver` 作为兜底保留。
2. **悬浮窗权限（`SYSTEM_ALERT_WINDOW`）**：仅有前台服务仍不够——EMUI 会以 `activity start fail: Background activity start` 静默拒绝后台拉起界面。授予「显示在其他应用上层」后获得 BAL 豁免。
3. **全屏意图通知兜底**：直接 startActivity 失败时，通过带全屏 Intent 的通知兜底拉起。
4. **事件去抖**：清单接收器与服务动态接收器可能同时命中同一事件，2 秒内重复事件忽略。
5. **电池容量**：无 root 读不到电池 sysfs。首次启动反射隐藏 API `com.android.internal.os.PowerProfile#getBatteryCapacity()` 探测设计容量（真机 4200 mAh），失败回退 1000 mAh；用户可在设置页手动修改（500–20000）。

### 安装与首次设置

```bash
adb install -r "Harmony\app\build\outputs\apk\debug\app-debug.apk"
```

1. 打开 **Endfield Charge**，打开 **Enable standalone charge animation** 开关（默认关），允许通知权限
2. 点 **Display over other apps: GRANT (required)**，允许「显示在其他应用上层」——**必授**，否则后台弹不出
3. 点 **Battery: allow background**，允许忽略电池优化
4. 点 **App info: enable auto-start**，在应用信息 → 电池 → 启动管理中放开自启动 / 后台活动
5. 点 **Test charge / Test unplug** 验证

### 限制

- **无法隐藏华为系统自带的充电动画 / 冒泡提示**——需要系统权限或 Xposed，免 root 做不到。
- **不保证开机自启**：手机重启后需**手动打开一次 App**（开关保持开启即会重新启动监控服务）。应用不申请开机广播权限。
- 熄屏深睡（Doze）期间的拔出广播投递属尽力而为。

## 变体二：OnePlus5T(Root)（一加 5T，root）

总开关**默认开启**，装好并授权 root 一次后，重启、锁屏、深睡插拔都会显示，不需要再打开 App。

### 实测结论（Android 10 / H2OS）

- 进程存活时，`ACTION_POWER_CONNECTED` 只投递给**动态注册**的接收器；清单里的 `ChargeEventReceiver` 在后台会被 `BroadcastQueue: Background execution not allowed` 直接丢弃——前台服务是必需的，不是可选项。
- 后台 `startActivity` 在 Android 10 上默认被静默拒绝（不抛异常），只有持有 `SYSTEM_ALERT_WINDOW` 才放行。
- H2OS 会拦截第三方 `BOOT_COMPLETED`，`BootReceiver` 实际收不到，开机自启必须由 Magisk 钩子完成。

### 保障链

1. **Magisk 开机钩子**：`/data/adb/service.d/endfieldcharge_boot.sh` 等待 `sys.boot_completed`，然后用 `am start` 启动 `BootProxyActivity`（无窗口的导出 Activity），由它在应用进程内拉起前台服务；失败重试 5 次。日志写 `/data/local/tmp/endfieldcharge_boot.log`。
2. **常驻前台服务**：`StandaloneMonitorService` 动态注册插/拔电与电池广播接收器，`START_STICKY`；每收到一次充电事件还会顺带确保服务在运行。
3. **双事件源**：同时监听 `ACTION_POWER_CONNECTED/DISCONNECTED` 和 `ACTION_BATTERY_CHANGED`，用 `EXTRA_PLUGGED` 的 0↔非0 变化合成同样事件，带 2 秒去抖。
4. **清单接收器兜底**：`ChargeEventReceiver` 在应用处于前台时仍可用。
5. **root 启动兜底**：没有悬浮窗权限时直接走 `su -c am start`（uid 0 不受后台启动限制），失败才降级为全屏意图通知。

### 一次性 root 授权（应用自动执行）

| 命令 | 作用 |
| --- | --- |
| `appops set <包名> SYSTEM_ALERT_WINDOW allow` | 后台拉起 HUD 的必要条件 |
| `dumpsys deviceidle whitelist +<包名>` | 加入 Doze 白名单 |
| `appops set <包名> RUN_ANY_IN_BACKGROUND allow` | 避免后台被冻结 |
| 写入 `/data/adb/service.d/endfieldcharge_boot.sh` | 开机自启（H2OS 拦截 `BOOT_COMPLETED`，只能这样做） |

### 安装与首次设置

```bash
adb install -r "OnePlus5T(Root)\app\build\outputs\apk\debug\app-debug.apk"
```

1. 打开 **Endfield Charge (Root)**，总开关默认为开；Magisk 弹授权时**允许**并选「永久记住」
2. 授权后自动完成上表四项（状态栏显示 `root: ... granted`）
3. 点 **Test charge / Test unplug** 验证
4. 之后重启手机无需再打开 App

想临时退出：关掉 **Enable standalone charge animation**，会停服务并删除 Magisk 钩子（重启后也不再自启）。

### 实测记录（adb 验证）

| 场景 | 结果 |
| --- | --- |
| 桌面 / 其他应用内插电、拔电 | HUD 正常显示 |
| 熄屏（Dozing）插电 | 自动亮屏并显示 |
| 进程被 kill 后插电 | 冷启动进程并显示 |
| 重启后不打开 App 直接插拔 | 钩子拉起服务，正常显示 |

### 限制

- **无法隐藏一加/氧 OS 自带的充电提示**：由 SystemUI 绘制，不 Hook 系统界面压不住，本版只做叠加显示。
- 按 Android 8 行为设计（`targetSdk 28`），已在 Android 10 验证；更高版本系统请用 `Harmony` 版本。
- 熄屏深睡期间的拔出广播能否投递取决于系统，已用 `ACTION_BATTERY_CHANGED` 兜底尽量覆盖。
- 开机钩子写入 `/data/adb/service.d`，卸载 App 不会自动删除；需要时可手动删掉该文件。

## 设置项

| 设置 | 默认值 | 说明 |
| --- | --- | --- |
| Enable standalone charge animation | Harmony 关 / Root 开 | 总开关，启停监控服务（Root 版同时启停开机钩子） |
| battery total capacity (mAh) | 自动探测，失败 1000 | 电池总容量（Harmony 真机 4200 / 5T 3300），可 Save / Auto detect（500–20000） |
| top offset | 8 dp | HUD 距屏幕挖孔 / 状态栏顶部的距离 |
| width | 屏幕的 92% | HUD 宽度占比 |
| duration | 6.0 s | 动画总时长，3–10 s（入场段固定 2.52 s） |
| bounce | 0.275 | BackOut 回弹过冲量 |
| ripple intensity / spread | 1.0 | 涟漪强度与扩散 |
| Wake the screen on plug | 开 | 插电时点亮屏幕 |
| Play on unplug | 开 | 拔出时播放简化动画 |
| language | Auto | 自动跟随系统 / 中文 / English |

设置页同时是预览器：拖动 cue 滑条可逐帧查看，Play charge / Play unplug 在应用内完整播放。

## 项目结构

```
Harmony/ 与 OnePlus5T(Root)/ 结构相同（目录名含括号，PowerShell 中需加引号）
app/src/main/java/com/lemoneko/endfieldcharge/
  EndfieldApp.kt               Application，启动设置仓库（Root 版另起监控服务与 root 探测）
  core/                        设备无关的共享核心
    BatterySnapshot.kt         电池快照（百分比/电压/mAh 容量）
    HudMetrics.kt              顶部间距等尺寸计算（API 30 以下自动降级）
    HudPlayMode.kt             CHARGE / UNPLUG 两种时间轴枚举
    HudLog.kt                  日志
    timeline/                  移植自 zmd-charge 的动画核心（无 Android import，可 JVM 单测）
      HudCues.kt / HudState.kt / HudTimeline.kt / AnimationOptions.kt
    settings/                  设置模型、JSON 编解码（HudSettings / HudSettingsJson / ContentSettings）
  standalone/                  独立模式
    StandaloneMonitorService.kt   常驻前台服务 + 动态接收器（Root 版另监听电池广播）
    ChargeEventReceiver.kt        清单接收器（兜底）
    ChargeEvents.kt               插/拔事件共用处理（2 秒去抖、开关判断）
    StandaloneChargeActivity.kt   全屏 HUD 宿主（showWhenLocked / turnScreenOn）
    StandaloneLauncher.kt         后台拉起 HUD（Root 版含 root am start 兜底）
    StandaloneNotifier.kt         全屏意图通知
    StandaloneScreen.kt           亮屏 / 解锁处理
    StandalonePrefs.kt            开关与容量存取、PowerProfile 容量探测
    BootReceiver.kt               （仅 Root）开机广播兜底
    BootProxyActivity.kt          （仅 Root）Magisk 钩子的无窗口入口
  root/
    RootShell.kt                （仅 Root）su 封装：appops/白名单/开机钩子/am start
  settings/                    SettingsProvider、SettingsRepository
  ui/                          EndfieldHudView 渲染器、HudStrings、HudTypefaces、MainActivity
  debug/                       FrameSheetRenderer（离屏逐帧渲染 PNG）
app/src/main/assets/fonts/     Inter-Variable.ttf（SIL OFL）
app/src/main/res/mipmap-anydpi-v26/  自适应启动图标（前景 drawable/ic_launcher_foreground.xml，
                                     设计源稿为仓库根目录 ic_launcher.svg）
app/src/test/                  JVM 单元测试（时间轴不变量、设置 JSON）
docs/                          上游 Xposed 时代的逆向分析（历史归档，见 docs/README.md）
tools/capture-reference-hud.ps1  逐帧抓取桌面参考实现画面做 A/B 对比（Windows）
```

## 编译

环境：**JDK 17**、Android SDK `platforms;android-36`、`build-tools;36.0.0`、Gradle 8.12（Wrapper 自带）。

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"

.\gradlew.bat testDebugUnitTest assembleDebug                              # 在 Harmony\ 下
& ".\gradlew.bat" testDebugUnitTest assembleDebug -p "..\OnePlus5T(Root)"   # 或指定工程目录
```

macOS / Linux 用 `./gradlew` 即可。SDK 不在默认位置时，在对应工程目录建 `local.properties`：

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

产物：`<工程>/app/build/outputs/apk/debug/app-debug.apk`（debug 签名，可直接安装）。`assembleRelease` 未配置签名，需自行签名。

国内网络卡在 `dl.google.com` 时，可在 `~/.gradle/init.gradle` 配置阿里云 Maven 镜像，仓库构建文件无需改动。

## 调试

```bash
# 监控服务是否在前台运行
adb shell dumpsys activity services com.lemoneko.endfieldcharge        # Harmony
adb shell dumpsys activity services com.lemoneko.endfieldcharge.root   # Root

# 悬浮窗权限（应为 allow）
adb shell appops get <包名> SYSTEM_ALERT_WINDOW

# 充电事件日志
adb logcat -s EndfieldCharge/Rx:* EndfieldCharge/Svc:* EndfieldCharge/Launch:* EndfieldCharge/Hud:*
adb logcat -s EndfieldCharge/Boot:* EndfieldCharge/BootProxy:* EndfieldCharge/Su:*   # 仅 Root

# 开机钩子执行记录（仅 Root）
adb shell su -c "cat /data/local/tmp/endfieldcharge_boot.log"

# 模拟插拔（不拔线也能测）
adb shell cmd battery unplug -f
adb shell cmd battery set usb 1 -f
adb shell cmd battery reset -f

# 离屏逐帧渲染 PNG / 指定帧预览
adb shell am start -n <包名>/.ui.MainActivity --ez renderSheets true
adb shell am start -n <包名>/.ui.MainActivity --ei cue 250
adb shell am start -n <包名>/.ui.MainActivity --ez unplug true
```

时间轴不变量已移植为 JVM 单元测试：`./gradlew testDebugUnitTest`。

## 免责声明

本项目仅作学习研究使用，因此不发布 Release。

**作者及贡献者不对因使用（或无法使用）本项目而造成的任何直接或间接后果负责**，包括但不限于：

- 手机变砖、无法开机、系统异常、数据丢失、硬件损坏；
- 电池异常（鼓包、过热、加速老化等）、充电故障；
- 因授予悬浮窗 / 后台活动 / root 等高权限，或修改系统设置导致的保修失效；
- 常驻前台服务、Magisk 开机钩子带来的额外耗电，以及与系统电源管理策略冲突引起的任何异常。

一旦安装或使用本项目，即视为你已理解并自愿承担全部风险；作者及贡献者不承担任何责任，也没有义务提供修复或赔偿。

## 许可证

- 应用代码：**AGPL-3.0**（见 [LICENSE](LICENSE)，两个工程各有一份）
- 内置 Inter 字体：SIL Open Font License（见 `app/src/main/assets/fonts/Inter-OFL.txt`）
