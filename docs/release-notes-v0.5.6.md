# 26.3 Speedrun Mods — v0.5.6

> [!WARNING]
> **WARNING（警告）** — 本项目为纯AI（LLM）生成移植，无人工代码审查，无法保证与原版逻辑一致，可能存在未知Bug或改变原版游戏机制。仅供娱乐，未经速通社区（MCSR）合法性认证，严禁用于提交任何排行榜成绩。
>
> / This project is a pure AI (LLM) generated port with no human code review; behaviour may differ from vanilla and unknown bugs or gameplay changes are possible. For entertainment only, not certified by the MCSR community, and must not be used to submit any leaderboard run.

## 相对 v0.5.5 的变化 / Changes since v0.5.5

### 修复 / Fixes

- 游玩时后台生成改为“让出 CPU、回墙后续跑”，减少世界里（尤其下界拉大渲染距离时）的掉帧。/ Background generation now yields while playing and resumes on the wall, reducing stutter in-world.
- StandardSettings 只在**新世界**自动套用设置（以世界目录中是否存在 `standardoptions.txt` 判断），点进已有存档不会被改设置。/ Settings are applied only to new worlds, detected by the absence of `standardoptions.txt`.
- 切换维度（下界/末地）不再重新套用设置（改为按连接对象判断是否进入新世界）。/ Dimension changes no longer re-apply settings.
- “Use StandardSettings” 开关现在真正生效（关闭后完全不自动套用）。/ The Use StandardSettings toggle now works.
- 进入世界后会重新应用并同步 ClientInformation 类设置（渲染距离、模拟距离、聊天可见性、皮肤部件、主手、粒子）。/ Client-information settings are re-applied and resynchronised on join.

### 其它 / Other

- SeedQueue 现在在 `fabric.mod.json` 中声明对 WorldPreview 的硬依赖（`>=6.3.0-rc.1 <7.0.0`），缺少该模组时会给出明确提示而不是 `NoClassDefFoundError`。/ SeedQueue now declares WorldPreview as a hard dependency.
- 预览渲染日志附带种子，便于排查“预览与实际世界不一致”的问题。/ The preview log line now includes the seed for diagnostics.

### 版本 / Versions

- SeedQueue `1.0.0-rc.6` → `1.0.0-rc.7`
- StandardSettings `2.3.0-rc.6` → `2.3.0-rc.7`
- SpeedrunAPI / WorldPreview / Atum / FastReset 版本不变。

## 包含的模组 / Included mods

| 文件 / File | 版本 / Version |
| --- | --- |
| `1.0.0-rc.7+26.3-SeedQueue.jar` | 1.0.0-rc.7 |
| `2.1.0-rc.4+26.3-Speedrun-API.jar` | 2.1.0-rc.4 |
| `2.3.0-rc.7+26.3-StandardSettings.jar` | 2.3.0-rc.7 |
| `6.3.0-rc.1+26.3-WorldPreview.jar` | 6.3.0-rc.1 |
| `1.4.0-rc.2+26.3-Atum.jar` | 1.4.0-rc.2 |
| `1.6.0-rc.1+26.3-FastReset.jar` | 1.6.0-rc.1 |

## 已知问题 / Known issues

1. 第三人称下玩家皮肤不显示 / no player skin in third-person previews
2. 远景仍为黑色 / far distance still black
3. 偶尔地形闪烁 / occasional terrain flickering
4. 26.x 水面透明度存在原版回归（MC-206561 仍为 Open）/ vanilla water transparency regression
5. 长时间游玩仍可能出现 GC 引起的偶发卡顿 / occasional GC-induced stutter in long sessions

## 安装 / Install

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议 Sodium 0.9.3）
2. 6 个 jar 放入 mods/，先删除旧版本
