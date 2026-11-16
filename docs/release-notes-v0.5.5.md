# 26.3 Speedrun Mods — v0.5.5

> [!WARNING]
> **WARNING（警告）** — 本项目为纯AI（LLM）生成移植，无人工代码审查，无法保证与原版逻辑一致，可能存在未知Bug或改变原版游戏机制。仅供娱乐，未经速通社区（MCSR）合法性认证，严禁用于提交任何排行榜成绩。
>
> / This project is a pure AI (LLM) generated port with no human code review; behaviour may differ from vanilla and unknown bugs or gameplay changes are possible. For entertainment only, not certified by the MCSR community, and must not be used to submit any leaderboard run.

## 相对 v0.5.4 的变化 / Changes since v0.5.4

### 修复 / Fixes

- 实例数会超过设定值（如 13/10）：自动补队列只统计未锁定实例，现改为按总数统计并受 `行 × 列` 上限约束。
  / The queue could exceed the configured size (e.g. 13/10); it now counts all entries and respects the wall grid.
- 回到墙时游戏短暂未响应：原先会一次性创建多个实例（每个都要启动服务端并加载 1866 个进度），现改为每次只补 1 个。
  / Brief freeze when returning to the wall: entries are now created one at a time instead of all at once.
- 丢弃实例后删除世界目录卡顿：删除改为后台执行、延迟 3 秒、每 tick 最多处理一个，失败只记录一次并交给下次启动清理。
  / Deleting discarded world folders no longer stalls the render thread.

### 版本 / Versions

- SeedQueue `1.0.0-rc.5` → `1.0.0-rc.6`
- StandardSettings `2.3.0-rc.3` → `2.3.0-rc.4`
- SpeedrunAPI / WorldPreview / Atum / FastReset 版本不变。

## 包含的模组 / Included mods

| 文件 / File | 版本 / Version |
| --- | --- |
| `1.0.0-rc.6+26.3-SeedQueue.jar` | 1.0.0-rc.6 |
| `2.1.0-rc.4+26.3-Speedrun-API.jar` | 2.1.0-rc.4 |
| `2.3.0-rc.4+26.3-StandardSettings.jar` | 2.3.0-rc.4 |
| `6.3.0-rc.1+26.3-WorldPreview.jar` | 6.3.0-rc.1 |
| `1.4.0-rc.2+26.3-Atum.jar` | 1.4.0-rc.2 |
| `1.6.0-rc.1+26.3-FastReset.jar` | 1.6.0-rc.1 |

## 已知问题 / Known issues

1. 第三人称下玩家皮肤不显示 / no player skin in third-person previews
2. 远景仍为黑色 / far distance still black
3. 偶尔地形闪烁 / occasional terrain flickering
4. 长时间游玩仍可能出现 GC 引起的偶发卡顿 / occasional GC-induced stutter in long sessions

## 安装 / Install

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议 Sodium 0.9.3）
2. 6 个 jar 放入 mods/，先删除旧版本
