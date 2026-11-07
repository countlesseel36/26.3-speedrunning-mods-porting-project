# 26.3 Speedrun Mods (unofficial port)

> ⚠️ This project is an **unofficial port** by the Chinese 1.16.2+ speedrunning community and is **not
> recognised by speedrun.com**; runs made with these mods **will not pass verification**. These are **test
> builds** and **products of vibe coding** — they may contain serious bugs. Use them at your own risk.

Ports of the six Fabric mods commonly used for 1.16.2+ speedrunning to **Minecraft 26.3**
(Fabric Loader 0.19.5+, JDK 25).

## Included mods

| Mod | Version | Description |
| --- | --- | --- |
| SeedQueue | `0.9.0-gamma.28+26.3` | Generates worlds in the background and previews them on a wall; instant join |
| WorldPreview | `6.3.0-gamma.1+26.3` | Preview rendering pipeline (required by SeedQueue) |
| SpeedrunAPI | `2.1.0-gamma.1+26.3` | Config-screen framework shared by the mods |
| StandardSettings | `2.3.0-gamma.1+26.3` | Applies preset options on world join (render distance, F3, pause, ...) |
| Atum | `1.4.0-gamma.1+26.3` | Auto reset; when SeedQueue is installed it only provides world settings |
| FastReset | `1.6.0-gamma.1+26.3` | Instant quit-to-title (skips saving) |

Optional: `SeedQueue-LockedPool.zip` (resource pack for the locked pool).

## Features

- **Wall screen**: full-bleed previews tiled seamlessly, no card background, label strip or gap;
- **Instant join**: clicking a card enters the already generated world without a loading screen;
- **Mouse controls**: left = play, right = lock/unlock, middle = discard and refill;
- **Worlds inherit Atum settings**: structures, bonus chest and difficulty are read from Atum;
- **No in-game generation cost**: new worlds are only queued while the wall is open;
- **Config page**: single scrollable page with category headings and hover descriptions, keys are rebindable
  (keyboard and mouse);
- **Chinese localisation**: every non-debug SeedQueue and StandardSettings option is translated.

## Known issues

1. **Player skin is still missing in third-person cards** — the preview connection has no player list, so the
   skin manager cannot resolve the real `PlayerInfo` skin;
2. **The far distance in previews is still black** — everything outside the generated chunks is background
   clear colour, and filling it with sky colour is not cheaply possible yet.

## Benchmark

Developer machine (i9-12900HX + RTX 4060 Laptop):

| chunkRadius | RPS |
| --- | --- |
| 2 | **2.50** |
| 4 | **1.90** |

## Install

1. Install Fabric Loader 0.19.5+ and Fabric API for 26.3;
2. Put the six jars into `mods/` (**delete older versions first** — two copies of the same mod will not load);
3. Sodium 0.9.3 is recommended;
4. The first launch creates `config/seedqueue.json` and the files under `config/mcsr/`.

## Build

```powershell
cd 26.3_speedrun_mods_ports
powershell -File .\build.ps1 -Mod all
```

Artifacts are written to `dist/`; older builds are kept in `dist/past-build/<Mod>/`
(see `docs/build-archive-convention.md` for the rules).

## Notes

- The sources come from the original authors (SeedQueue/WorldPreview/SpeedrunAPI/StandardSettings by contaria,
  Atum by voidxwalker, FastReset by Slackow and others). This repository only contains the **26.3 adaptation
  layer** and the fixes required for it;
- Licences follow upstream (see the LICENSE file in each mod directory);
- Issues are welcome, but please attach `logs/latest.log`.

---

## 中文

将 1.16.2+ 速通常用的 6 个 Fabric mod 移植到 **Minecraft 26.3**（Fabric Loader 0.19.5+，JDK 25）。

> ⚠️ 本项目由中国 1.16.2+ 速通社区**自行移植**，**未获 speedrun.com 官方承认**；使用这些 mod 的速通记录在
> speedrun.com **无法通过审核**。同时这是**测试版本**、**vibe coding 的产物**，可能出现重大 bug，请权衡后使用。

**包含的模组**：SeedQueue `0.9.0-gamma.28`、WorldPreview `6.3.0-gamma.1`、SpeedrunAPI `2.1.0-gamma.1`、
StandardSettings `2.3.0-gamma.1`、Atum `1.4.0-gamma.1`、FastReset `1.6.0-gamma.1`（均为 `+26.3`），
可选资源包 `SeedQueue-LockedPool.zip`。

**主要特性**：墙界面预览满幅无缝拼接；点击卡片瞬间进入已生成世界；鼠标操作（左键进入、右键锁定、中键丢弃补位）；
队列世界继承 Atum 的建筑/奖励箱/难度设置；只在开墙时补新世界；配置页单页滚动、分类标题、悬停说明、可选改键（键盘与鼠标）；
非调试项全部中文化。

**已知问题**：第三人称卡片无法显示玩家皮肤；预览远景为黑色。

**实测**：`chunkRadius = 2` 时 2.50 RPS，`chunkRadius = 4` 时 1.90 RPS。

**安装**：Fabric Loader 0.19.5+ 与 Fabric API（26.3），6 个 jar 放入 `mods/`（先删旧版），建议 Sodium 0.9.3。

**构建**：`powershell -File .\\build.ps1 -Mod all`，产物在 `dist/`，历史构建在 `dist/past-build/<Mod>/`。
