# EndfieldChargingAnim

插拔充电器时播放《明日方舟：终末地》风格「超充模式」HUD 的 Android 应用。视觉规格 1:1 移植自 [QinAnze/zmd-charge](https://github.com/QinAnze/zmd-charge)，代码基础来自 [NingmengLemon/EndfieldChargingAnim](https://github.com/NingmengLemon/EndfieldChargingAnim)。

仓库按目标设备拆成两个**可独立编译**的工程：

| 目录 | 适用设备 | 权限模式 | README |
| --- | --- | --- | --- |
| [Harmony](Harmony) | 华为鸿蒙 / EMUI（Android 12，OCE-AN10） | 免 root、免 Xposed；依赖前台服务 + 悬浮窗权限 | [Harmony/README.md](Harmony/README.md) |
| [OnePlus5T(Root)](OnePlus5T(Root)) | 一加 5T（Android 8.0/8.1，已 root） | root；su 自动授权、开机自启、root 启动兜底 | [OnePlus5T(Root)/README.md](OnePlus5T(Root)/README.md) |

两套工程共用同一份渲染器（`ui/EndfieldHudView.kt`）、动画时间轴（`core/timeline/`）与设置模型（`core/settings/`），差异只在清单、SDK 版本与「如何让后台弹窗」这一层。

## 编译

各自进入子目录执行（目录名含括号，PowerShell 中需加引号）：

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"

.\gradlew.bat testDebugUnitTest assembleDebug                       # 在 Harmony\ 下
& ".\gradlew.bat" testDebugUnitTest assembleDebug -p "..\OnePlus5T(Root)"   # 或指定工程目录
```

产物均在 `<工程>/app/build/outputs/apk/debug/app-debug.apk`。

## 免责声明

仅供学习研究使用。**作者及贡献者不对因使用本项目造成的任何后果负责**，包括但不限于变砖、无法开机、数据丢失、电池异常、保修失效等。刷机、root、授予高权限均有不可逆风险，请自行备份并评估；一旦安装或使用即视为自愿承担全部风险。

## 许可证

应用代码 AGPL-3.0（见 [LICENSE](LICENSE)）；内置 Inter 字体为 SIL OFL。
