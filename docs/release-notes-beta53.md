# 26.3 Speedrun Mods — v0.9.0-beta.53

> ⚠️ 这些 mod 由中国 1.16.2+ 速通社区自行移植，**未获得 speedrun.com 的官方承认**；若使用这些 mod 进行速通，
> 你将在 speedrun.com **无法通过审核**。这些 mod 是 **vibe coding 的产物**，且为**测试版本**，可能会出现重大 bug，
> 可能会影响你的速通，**请权衡利弊后使用**。

## 本次修复 / Fixes

- **墙界面鼠标按键错位**：Minecraft 26.3 的鼠标按键来自 SDL，编号是 `1=左键 / 2=中键 / 3=右键`，
  而代码此前按 GLFW 的 `0/1/2` 判断，导致「左键锁卡、右键无反应」。现已改为明确常量：
  **左键进入实例、右键锁定/解锁、中键丢弃并补位**，侧键（SDL 4/5）忽略；
- 键位设置界面录制鼠标键时改为 `event.button() - 1` 转成 GLFW 索引后再保存，
  避免绑定到错误按键；
- SeedQueue 版本号 `0.9.0-beta.52` → `0.9.0-beta.53`，其余五个模组未变。

## 包含的模组 / Included

| 文件 | 模组 |
| --- | --- |
| `0.9.0-beta.53+26.3-SeedQueue.jar` | SeedQueue |
| `6.3.0-beta.31+26.3-WorldPreview.jar` | WorldPreview |
| `2.1.0-beta.8+26.3-Speedrun-API.jar` | SpeedrunAPI |
| `1.4.0-beta.4+26.3-Atum.jar` | Atum |
| `1.6.0-beta.3+26.3-FastReset.jar` | FastReset |
| `2.3.0-beta.2+26.3-StandardSettings.jar` | StandardSettings |
| `SeedQueue-LockedPool.zip` | 锁定实例独立池资源包（可选） |

## 安装 / Install

1. 把 6 个 jar 放进 `mods/`（**先删掉旧版本的 SeedQueue**，同名 mod 不能装两份）；
2. 可选：`SeedQueue-LockedPool.zip` 放进 `resourcepacks/` 并在游戏里启用；
3. 需要 Fabric Loader 0.19.5+ 与 Minecraft 26.3。

## 实测 / Benchmark

开发者机器（12th Gen Intel(R) Core(TM) i9-12900HX，NVIDIA GeForce RTX 4060 Laptop GPU）：
`chunkRadius = 2` 时 **2.07 RPS**，`chunkRadius = 4` 时 **1.57 RPS**。
