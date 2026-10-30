# docs

本目录的两份文档是**上游 Xposed 时代的逆向分析归档，描述的代码已从本仓库移除**。

当前版本是免 root 的鸿蒙 / EMUI 独立模式，工作原理与使用方式见根目录 [README](../README.md)。当前代码中：

- 没有 Xposed 模块入口（原 `ModuleEntry.kt`、`META-INF/xposed/*` 已删除）；
- 没有任何 ROM 适配器（原 `rom/hyperos`、`rom/oxygenos` 已删除）；
- 没有 Hook SystemUI、抑制 ROM 动画、类型 `2026` 系统窗口相关代码（原 `EndfieldHudController` / `HudWindow` / `SystemUiContext` 等已删除）。

以下文档保留作为视觉移植与历史调研资料阅读，其中记录的类名、Hook 点、设备数据**均不再对应本仓库代码，也不可用于当前版本的排障**。

| 文档 | 内容 | 现状 |
| --- | --- | --- |
| [01-feasibility.md](01-feasibility.md) | Redmi K60 / HyperOS 2 充电动画逆向、Xposed 抑制方案、`zmd-charge` 视觉规格、时间轴移植与 A/B 对比 | 历史归档：Hook 方案已移除；其中时间轴 / 视觉规格部分与当前 `core/timeline/`、`ui/EndfieldHudView.kt` 仍同源，可作设计背景阅读 |
| [02-oxygenos-charging-animation.md](02-oxygenos-charging-animation.md) | OnePlus 15 / OxygenOS 16 充电动画逆向与适配器设计 | 历史归档：适配器代码已全部移除，仅作逆向资料留存 |
