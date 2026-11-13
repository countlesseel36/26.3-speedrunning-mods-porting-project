# 26.3 Speedrun Mods — v0.5.0

## 警告 / WARNING

**WARNING（测试版本）** — 社区自行移植，未获 speedrun.com 官方承认；vibe coding 产物，可能存在重大 bug。
/ Unofficial community ports, **not recognised by speedrun.com**; test builds, vibe-coded, major bugs may exist.

## 包含的模组 / Included mods

| 文件 / File | 版本 / Version |
| --- | --- |
| `1.0.0-rc.1+26.3-SeedQueue.jar` | 1.0.0-rc.1 |
| `2.1.0-rc.3+26.3-Speedrun-API.jar` | 2.1.0-rc.3 |
| `2.3.0-rc.2+26.3-StandardSettings.jar` | 2.3.0-rc.2 |
| `6.3.0-rc.1+26.3-WorldPreview.jar` | 6.3.0-rc.1 |
| `1.4.0-rc.2+26.3-Atum.jar` | 1.4.0-rc.2 |
| `1.6.0-rc.1+26.3-FastReset.jar` | 1.6.0-rc.1 |

> SeedQueue 进入 `1.0.0-rc` 线；发布文件名不再带 build/序号。

## 相对 v0.4.1 / Changes since v0.4.1

### 修复 / Fixes

- **`damage_event` 掉线**：只允许玩家所在的服务端发送伤害事件，后台队列服务端的一律丢弃（此前 `minecraft:spear` 这类原版伤害类型能绕过注册表检查）。
  / **damage_event disconnects**: only the server the player is actually in may send damage events; background queue servers are dropped.
- **疾跑 / 潜行切换不再被翻转**（进世界只在保存为「按下」时恢复）。
  / Sprint/sneak toggle state no longer flips on world join.
- **锁定实例导致的重复重置**：锁定实例现在计入队列占用槽位。
  / Locked instances now count as occupied queue slots (fixes repeated resets).
- **F6 / 切卡的跳过保存**改为 `disconnect` 返回后同步恢复，杜绝对「保存并退出」的影响。
  / The skip-saves flag is restored synchronously after disconnect.

### 配置界面 / Config UI

- 新增 **Benchmark（跑分）** 大类：Benchmark Resets / Benchmark Concurrency / Benchmark Sample Ticks / Max Generating Count / Benchmark Chunk Radius；
- 移除 **Miscellaneous** 大类：`Check Allocated Memory`、`Chunkmap Freezing` 移入 Performance；`Chunk Progress`、`Show Debug Menu`、`Watchdog` 移入 Debug；
- `Chunkmap Freezing` 滑块范围由 500000 收紧到 10000；删除调试菜单的红色「作废」提示；
- 补齐区块半径、行/列、智能切换等选项的中文悬停说明。

### 性能相关 / Performance

对提高 RPS 的尝试与实测（详见 README 与 `docs/seedqueue-config.md`）：

| 尝试 | 结果 |
| --- | --- |
| 并发 4 → 8 | 2.25 → 2.54 RPS |
| `maxGeneratingCount` 2 / 3 | 2.42 / 2.44 RPS |
| Benchmark Chunk Radius 1 | 2.74 RPS |
| 生成分步化 + ticket 驻留 | 失败（地形消失），已回退 |
| 预览帧缓存 | 失败（残影/纹理已销毁），已回退 |

结论：单实例构建中约 70% 是不可并行的固定成本（世界创建、出生点预生成、光照），继续调并发或半径收益有限。

## 实测参考 / Measured reference

开发机：RTX 4060 Laptop 8 GB / i9-12900HX (16C24T) / 32 GB DDR5-4800，窗口 2560×1472。

| 配置 | RPS | 单实例构建 |
| --- | --- | --- |
| 半径 2，并发 4 | 2.25 | 1.66 s |
| 半径 2，并发 8 | 2.54 | 2.7–3.2 s |
| 半径 1（跑分），并发 6 | **2.74** | 1.35–1.90 s |

## 已知问题 / Known issues

1. 第三人称下玩家皮肤不显示 / no player skin in third-person previews
2. 远景仍为黑色 / far distance still black
3. 偶尔地形闪烁 / occasional terrain flickering
4. 生成分步化与预览帧缓存两条优化路线在 26.3 上不可行（见 README）

## 安装 / Install

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议 Sodium 0.9.3）
2. 6 个 jar 放入 mods/，先删除旧版本
