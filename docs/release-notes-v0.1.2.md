# 26.3 Speedrun Mods — v0.1.2

> ⚠️ 这些 mod 由中国 1.16.2+ 速通社区自行移植，**未获得 speedrun.com 的官方承认**；若使用这些 mod 进行速通，
> 你将在 speedrun.com **无法通过审核**。这些 mod 是 **vibe coding 的产物**，且为**测试版本**，可能会出现重大 bug，
> 可能会影响你的速通，**请权衡利弊后使用**。

## 包含的模组 / Included mods

| 文件 File | 版本 Version |
| --- | --- |
| `0.9.0-beta.70+26.3-SeedQueue.jar` | 0.9.0-beta.70 |
| `6.3.0-beta.41+26.3-WorldPreview.jar` | 6.3.0-beta.41 |
| `2.1.0-beta.12+26.3-Speedrun-API.jar` | 2.1.0-beta.12 |
| `2.3.0-beta.3+26.3-StandardSettings.jar` | 2.3.0-beta.3 |
| `1.4.0-beta.4+26.3-Atum.jar` | 1.4.0-beta.4 |
| `1.6.0-beta.3+26.3-FastReset.jar` | 1.6.0-beta.3 |

## 本版修复 / Fixes

- **墙界面卡片显示不完整（第三人称黑带）**：根因是预览渲染使用全局相机类型，第三人称背对镜头时相机退到玩家身后、看向从未生成的区域。现在打开墙时会重置相机类型，关闭时恢复，所有人称下卡片都能完整渲染；
- **设置页选项名称显示为键名**：SpeedrunAPI 现在会循环剥离 `key_` / `key.` 前缀并按末段翻译，调试类按键也显示为可读名称；
- **`autoSuggestions` / `backgroundForChatOnly` 缺少翻译**：已补进 StandardSettings 的语言文件；
- **组合键 / 屏蔽键**：SDL 输入路径读不到修饰键状态，导致 `Shift+M` 这类绑定静默失效。该功能整体移除，每个动作只保留单键；"下一个锁定实例"默认为 `M`；
- **改键时游戏仍在运行**：`WallKeybindingsScreen.isPauseScreen()` 改为 `true`，打开改键界面即暂停；
- **死亡重生掉帧**：检测到客户端切换 level 后暂停预览轮换约 4 秒（`-Dseedqueue.respawnPauseTicks`，默认 80），避免与客户端自身的重建撞在一起。

## 已知问题 / Known issues

1. **第三人称预览卡片里的玩家没有皮肤**。预览世界使用独立的连接，没有玩家列表，皮肤渲染器按 UUID 查不到皮肤；尝试过的两种方案（复用真实 profile、`RemotePlayer`、给 `getSkin()` 加 mixin 直接返回真实皮肤）均未成功，已全部回退；
2. **"下一个锁定实例"（默认 `M`）行为待确认**。功能与按键分支均已接入，按下后日志应出现 `[seedqueue] play next lock: ...`；若没有任何日志或提示 `no locked ready entry`，需要在墙上先锁定一张已生成完成的卡片，并把日志反馈给作者；
3. **预览世界不 tick**，因此远景以外的地形依赖预览范围（`chunkRadius`，默认 4）。调大能看得更远，但生成更慢、占用更高；

## 安装 / Install

1. 把 6 个 jar 放进 `mods/`（**先删掉旧版本**，同名 mod 不能装两份）；
2. 需要 Minecraft 26.3 + Fabric Loader 0.19.5+，建议 Sodium 0.9.3；
3. 首次启动生成 `config/seedqueue.json` 与 `config/mcsr/seedqueue.json`。

## 实测 / Benchmark

开发者机器（12th Gen Intel(R) Core(TM) i9-12900HX，NVIDIA GeForce RTX 4060 Laptop GPU）：
`chunkRadius = 2` 时 **2.07 RPS**，`chunkRadius = 4` 时 **1.57 RPS**。

---

## English

**26.3 Speedrun Mods — v0.1.2**

> ⚠️ **WARNING** — Unofficial ports by the Chinese 1.16.2+ speedrunning community, **not officially
> recognised by speedrun.com**. Runs using them will **not pass verification**. These are **test builds**
> and **products of vibe coding** — weigh the trade-offs before using them.

**Fixes**

- **Incomplete cards / black band in third person**: previews render with the global camera type, so the
  third-person-back camera looked at never-generated chunks. The camera type is now reset while the wall
  is open and restored when it closes.
- **Config screens showed raw translation keys**: SpeedrunAPI now strips repeated `key_` / `key.` prefixes
  and falls back to the trailing name, so debug keybinds read normally.
- Added the missing `autoSuggestions` / `backgroundForChatOnly` translations.
- **Combination and blocking keys removed**: the SDL input path could not report modifier state, which
  made `Shift+M`-style bindings silently dead. Actions now use a single key; "play next lock" is `M`.
- **Rebinding screen now pauses the game** (`isPauseScreen()` returns true).
- **Respawn frame drop**: the preview rotation pauses for ~4 s after the client switches level
  (`-Dseedqueue.respawnPauseTicks`, default 80).

**Known issues**

1. **No player skin in third-person cards** — the preview connection has no player list, so the skin
   renderer finds nothing by UUID. Reusing the real profile, switching to a `RemotePlayer` and a
   `getSkin()` mixin were all tried and reverted.
2. **"Play next lock" (default `M`) needs confirmation** — the action and its key branch exist; pressing
   it should log `[seedqueue] play next lock: ...`. Lock a fully generated card first and report the log.
3. Previews only cover `chunkRadius` chunks (default 4); larger radii look further but generate slower.

**Install**: drop the six jars into `mods/` (delete older versions first); Minecraft 26.3 + Fabric Loader
0.19.5+, Sodium 0.9.3 recommended.

**Benchmark** (developer PC: i9-12900HX + RTX 4060 Laptop): **2.07 RPS at `chunkRadius = 2`**,
**1.57 RPS at `chunkRadius = 4`**.
