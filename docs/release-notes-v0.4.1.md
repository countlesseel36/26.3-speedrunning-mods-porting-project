# 26.3 Speedrun Mods — v0.4.1

## 警告 / WARNING

**WARNING（测试版本）** — 社区自行移植，未获 speedrun.com 官方承认；vibe coding 产物，可能存在重大 bug。
/ Unofficial community ports, **not recognised by speedrun.com**; test builds, vibe-coded, major bugs may exist.

## 包含的模组 / Included mods

| 文件 / File | 版本 / Version |
| --- | --- |
| `0.9.0-rc.6+26.3-SeedQueue.jar` | 0.9.0-rc.6 |
| `6.3.0-rc.1+26.3-WorldPreview.jar` | 6.3.0-rc.1 |
| `2.1.0-rc.3+26.3-Speedrun-API.jar` | 2.1.0-rc.3 |
| `2.3.0-rc.2+26.3-StandardSettings.jar` | 2.3.0-rc.2 |
| `1.4.0-rc.2+26.3-Atum.jar` | 1.4.0-rc.2 |
| `1.6.0-rc.1+26.3-FastReset.jar` | 1.6.0-rc.1 |

## 相对 v0.4.0 / Changes since v0.4.0

### 修复 / Fixes

- **疾跑 / 潜行切换状态不再被翻转**：进世界时只在保存状态为「按下」时恢复，保存为「未按下」时保持不动，避免切换键被意外触发一次。
  / **Sprint / sneak toggle state no longer flips**: the state is only restored when it was saved as pressed, so the toggle key is not triggered spuriously.
- **F6 / 切卡的跳过保存标志改为同步恢复**：disconnect 一返回立刻还原，消除点「保存并退出」被误跳过保存的窗口。
  / **The skip-saves flag used by F6 / next-instance is restored synchronously** after disconnect returns, removing the window where Save and Quit could skip saving.

### 变更 / Changes

- **跑分不再强制 maxWorldGenerationPercentage**（此前固定 80%），完全跟随玩家配置；RPS 会据此回落，属预期变化。
  / **The benchmark no longer forces maxWorldGenerationPercentage** (previously 80%) and follows the player config; RPS drops accordingly.
- **新增跑分环境摘要日志**：java / OS / 核心数 / 堆 / 窗口尺寸 / 队列容量 / 生成百分比 / 并发 / Sodium 版本，便于跨机对比。
  / **New benchmark environment summary log** for cross-machine comparison.

## 实测参考 / Measured reference

开发机 / Developer PC：RTX 4060 Laptop 8 GB / i9-12900HX / 32 GB DDR5-4800

| 配置 / Config | RPS | 单实例构建 / Build |
| --- | --- | --- |
| 生成 40%，并发 4 / gen 40%, conc 4 | 2.25 | 1.66 s |
| 生成 40%，并发 8 / gen 40%, conc 8 | 2.54 | 2.7–3.2 s |

RTX 5060 + Ultra 9 275HX 报告 1.5 RPS，通常源于生成百分比为默认 100% 或并发未设置；对比时请附 benchmark env 日志行。
/ A 1.5 RPS report on RTX 5060 + Ultra 9 275HX usually comes from the default 100% generation percentage or unset concurrency.

## 已知问题 / Known issues

1. 第三人称下玩家皮肤不显示 / no player skin in third-person previews
2. 远景仍为黑色 / far distance still black
3. 偶尔地形闪烁 / occasional terrain flickering

## 安装 / Install

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议 Sodium 0.9.3）
2. 6 个 jar 放入 mods/，先删除旧版本
