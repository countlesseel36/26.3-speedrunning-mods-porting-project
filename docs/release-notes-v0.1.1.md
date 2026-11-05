# 26.3 Speedrun Mods — v0.1.1

> ⚠️ 这些 mod 由中国 1.16.2+ 速通社区自行移植，**未获得 speedrun.com 的官方承认**；若使用这些 mod 进行速通，
> 你将在 speedrun.com **无法通过审核**。这些 mod 是 **vibe coding 的产物**，且为**测试版本**，可能会出现重大 bug，
> 可能会影响你的速通，**请权衡利弊后使用**。

## 包含的模组 / Included mods

| 文件 File | 模组 Mod | 版本 Version |
| --- | --- | --- |
| `0.9.0-beta.60+26.3-SeedQueue.jar` | SeedQueue | 0.9.0-beta.60 |
| `6.3.0-beta.35+26.3-WorldPreview.jar` | WorldPreview | 6.3.0-beta.35 |
| `2.1.0-beta.8+26.3-Speedrun-API.jar` | SpeedrunAPI | 2.1.0-beta.8 |
| `1.4.0-beta.4+26.3-Atum.jar` | Atum | 1.4.0-beta.4 |
| `1.6.0-beta.3+26.3-FastReset.jar` | FastReset | 1.6.0-beta.3 |
| `2.3.0-beta.2+26.3-StandardSettings.jar` | StandardSettings | 2.3.0-beta.2 |
| `SeedQueue-LockedPool.zip` | 锁定实例独立池资源包（可选） | — |

## 本次修复 / Fixes in this release

- **改预览半径后卡片出现不渲染区域**：预览的 Sodium section 图按旧半径缓存，半径变化时没有重建。现在检测半径变化并重建，重建后重新喂入区块快照（`invalidateSections()`）；
- **标题界面点金靴子卡在"正在准备生成世界"**：加载屏只会在墙内加入路径被关掉。现在世界与玩家就绪即摘除原版加载屏，不再依赖具体路径；
- **安装资源包后行列数设置失效**：资源包 `custom_layout.json` 里写死的网格覆盖了设置。现在布局只取包里的位置/大小，网格数仍用 `rows`/`columns`；
- **F6 回墙后卡片不完整**：`chunksApplied` 在 manager 重建后没有重置，新 manager 的 tracker 收不到区块。现在重建处都会重置该状态；
- **开 F3 时卡片里出现叠加层**：卡片是主渲染目标的拷贝，原版 F3 被一起拷入。墙打开期间强制隐藏该叠加层；
- **退出墙不清空已预览的世界**：现在退出墙会丢弃所有未锁定预览并释放其 Sodium arena；
- **白块/闪烁**：Sodium 的顶点 arena 按设备共享而 render list 按 manager 各自持有，多 manager 共存时会互相回收 arena 导致白块。改为单 manager，并且只在 Sodium 报告地形编译完成且稳定后才把帧拷进卡片；
- 跑分重置次数上限 10000 → **2000**。

## 安装 / Install

1. 把 6 个 jar 放进 `mods/`（**先删掉旧版本的 SeedQueue / WorldPreview**，同名 mod 不能装两份）；
2. 可选：`SeedQueue-LockedPool.zip` 放进 `resourcepacks/` 并在游戏里启用；
3. 需要 Minecraft 26.3 + Fabric Loader 0.19.5+，建议 Sodium 0.9.3；

## 实测 / Benchmark

开发者机器（12th Gen Intel(R) Core(TM) i9-12900HX，NVIDIA GeForce RTX 4060 Laptop GPU）：
`chunkRadius = 2` 时 **2.07 RPS**，`chunkRadius = 4` 时 **1.57 RPS**。

---

## English

**26.3 Speedrun Mods — v0.1.1**

> ⚠️ **WARNING** — These mods are unofficial ports made by the **Chinese 1.16.2+ speedrunning community**
> and are **not officially recognised by speedrun.com**. Runs made with them will **not pass speedrun.com
> verification**. They are **products of vibe coding** and **test builds**: major bugs may exist and may
> affect your runs. **Weigh the trade-offs before using them.**

**Included:** SeedQueue 0.9.0-beta.60, WorldPreview 6.3.0-beta.35, SpeedrunAPI 2.1.0-beta.8,
Atum 1.4.0-beta.4, FastReset 1.6.0-beta.3, StandardSettings 2.3.0-beta.2, and the optional
`SeedQueue-LockedPool.zip` resource pack.

**Fixes in this release**

- **Unrendered areas after changing the preview radius**: the preview section graph was cached for the old
  radius. The change is now detected, the graph rebuilt and the chunk snapshot re-applied.
- **Gold-boots reset from the title screen hung on "Preparing world generation"**: the loading screen was
  only dismissed on the wall join path. It is now dismissed as soon as the world and player exist.
- **Rows/columns settings ignored with the resource pack enabled**: the layout now only takes positions and
  sizes from the pack; the grid still comes from `rows`/`columns`.
- **Incomplete cards after returning to the wall with F6**: the chunk-applied state is now reset whenever
  the section manager is rebuilt.
- **F3 overlay drawn inside the cards**: it is hidden while the wall is open.
- **Leaving the wall kept every previewed world alive**: unlocked previews are discarded and their Sodium
  arenas freed.
- **White blocks / flicker**: Sodium arenas are shared per device while render lists are per manager, so
  several managers evicted each other's arenas. A single manager is kept now, and a frame is only captured
  once Sodium reports the terrain as complete and stable.
- Benchmark reset limit lowered from 10000 to **2000**.

**Install**

1. Drop the six jars into `mods/` — **delete older SeedQueue / WorldPreview versions first**.
2. Optional: put `SeedQueue-LockedPool.zip` into `resourcepacks/` and enable it in game.
3. Requires Minecraft 26.3 + Fabric Loader 0.19.5+; Sodium 0.9.3 recommended.

**Benchmark** (developer PC: 12th Gen Intel(R) Core(TM) i9-12900HX, NVIDIA GeForce RTX 4060 Laptop GPU):
**2.07 RPS at `chunkRadius = 2`**, **1.57 RPS at `chunkRadius = 4`**.
