# 26.3 Speedrun Mods — v0.9.0-beta.52

> ⚠️ 这些 mod 由中国 1.16.2+ 速通社区自行移植，**未获得 speedrun.com 的官方承认**；若使用这些 mod 进行速通，
> 你将在 speedrun.com **无法通过审核**。这些 mod 是 **vibe coding 的产物**，且为**测试版本**，可能会出现重大 bug，
> 可能会影响你的速通，**请权衡利弊后使用**。
>
> These mods are unofficial ports by the Chinese 1.16.2+ speedrunning community, **not officially recognised
> by speedrun.com**. They are **products of vibe coding** and **test builds** — weigh the trade-offs.

## 包含的模组 / Included

| 文件 | 模组 | 说明 |
| --- | --- | --- |
| `0.9.0-beta.52+26.3-SeedQueue.jar` | SeedQueue | 后台预生成世界 + 3D 墙界面 + 跑分 |
| `6.3.0-beta.31+26.3-WorldPreview.jar` | WorldPreview | 世界生成期间的实时预览 |
| `2.1.0-beta.8+26.3-Speedrun-API.jar` | SpeedrunAPI | 配置框架（26.3 重写） |
| `1.4.0-beta.4+26.3-Atum.jar` | Atum | 金靴子按钮 / 自动重置 |
| `1.6.0-beta.3+26.3-FastReset.jar` | FastReset | 重置时跳过存档 |
| `2.3.0-beta.2+26.3-StandardSettings.jar` | StandardSettings | 开局重置设置 |
| `SeedQueue-LockedPool.zip` | 资源包 | 锁定实例放进独立池（可选） |

## 安装 / Install

1. 把 6 个 jar 放进 `mods/`（先删掉旧版本）；
2. 可选：`SeedQueue-LockedPool.zip` 放进 `resourcepacks/` 并在游戏里启用；
3. 需要 Fabric Loader 0.19.5+ 与 Minecraft 26.3。

## 本版变更 / Changes

- 跑分重置次数上限由 10000 调整为 **2000**；
- 六个模组的作者信息补上移植者 **CountlessEel36**，说明为中国 1.16.2+ 速通社区移植；
- SeedQueue 恢复为未做闪烁实验修复的稳定行为（F6 回墙、原版建世界前释放墙、退出兜底等修复保留）。

## 实测 / Benchmark

开发者机器（12th Gen Intel(R) Core(TM) i9-12900HX，NVIDIA GeForce RTX 4060 Laptop GPU）：

| chunkRadius | RPS |
| --- | --- |
| 2 | 2.07 |
| 4 | 1.57 |
