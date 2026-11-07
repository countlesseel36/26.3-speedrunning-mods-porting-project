# 26.3 Speedrun Mods（非官方移植 / Unofficial port）

> ⚠️ 本项目由中国 1.16.2+ 速通社区自行移植，未获 speedrun.com 官方承认；使用这些 mod 的速通记录无法通过审核。
> ⚠️ Unofficial port by the Chinese 1.16.2+ community; **not recognised by speedrun.com** — runs made with these
> mods will not pass verification.

> 这是测试版本、vibe coding 的产物，可能出现重大 bug。/ Test builds and products of vibe coding; expect bugs.

将 1.16.2+ 速通常用的 6 个 Fabric mod 移植到 Minecraft 26.3（Fabric Loader 0.19.5+，JDK 25）。
/ Ports of six 1.16.2+ speedrunning Fabric mods to Minecraft 26.3 (Fabric Loader 0.19.5+, JDK 25).

## 包含的模组 / Included mods

| 模组 / Mod | 版本 / Version | 说明 / Description |
| --- | --- | --- |
| SeedQueue | `0.9.0-gamma.28+26.3` | 后台生成世界 + 墙界面预览 / Background world generation + wall previews |
| WorldPreview | `6.3.0-gamma.1+26.3` | 预览渲染管线 / Preview rendering pipeline |
| SpeedrunAPI | `2.1.0-gamma.1+26.3` | 配置界面框架 / Config screen framework |
| StandardSettings | `2.3.0-gamma.1+26.3` | 进世界应用预设选项 / Applies preset options on world join |
| Atum | `1.4.0-gamma.1+26.3` | 自动重置；有 SeedQueue 时只提供建世界设置 / Auto reset; settings only when SeedQueue is present |
| FastReset | `1.6.0-gamma.1+26.3` | 秒退到标题 / Instant quit to title |

可选 / Optional: `SeedQueue-LockedPool.zip`（锁定池资源包 / locked-pool resource pack）

## 特性 / Features

- 墙界面预览满幅无缝拼接，去掉卡片底色、标签条与间距
  / Full-bleed seamless wall previews: no card background, label strip or gap
- 点击卡片瞬间进入已生成世界，无加载界面
  / Clicking a card joins the generated world instantly, without a loading screen
- 鼠标操作：左键进入、右键锁定/解锁、中键丢弃并补位
  / Mouse controls: left = play, right = lock/unlock, middle = discard and refill
- 队列世界继承 Atum 的生成建筑、奖励箱与难度
  / Queued worlds inherit Atum's structures, bonus chest and difficulty
- 只在开墙时补新世界，游玩中不后台生成
  / The queue only refills while the wall is open; nothing generates in-game
- 配置页单页滚动、分类标题、悬停说明，键盘与鼠标均可改键
  / Single scrollable config page with headings, tooltips and rebindable keyboard/mouse keys
- SeedQueue 与 StandardSettings 的非调试项已全部中文化
  / All non-debug SeedQueue and StandardSettings options are translated into Chinese

## 已知问题 / Known issues

1. 第三人称卡片仍无法显示玩家皮肤（预览连接无玩家列表，拿不到真实 `PlayerInfo`）
   / Player skin is still missing in third-person cards (the preview connection has no player list, so the real
   `PlayerInfo` skin cannot be resolved)
2. 预览远景仍是黑色（超出已生成区块的部分只有清屏色）
   / The far distance in previews is still black (outside the generated chunks only the clear colour is shown)

## 实测 / Benchmark

开发者机器（i9-12900HX + RTX 4060 Laptop）/ Developer machine:

| chunkRadius | RPS |
| --- | --- |
| 2 | **2.50** |
| 4 | **1.90** |

## 安装 / Install

1. 安装 Fabric Loader 0.19.5+ 与 Fabric API（26.3）/ Install Fabric Loader 0.19.5+ and Fabric API for 26.3
2. 把 6 个 jar 放入 `mods/`，先删除旧版本 / Put the six jars into `mods/`, deleting older versions first
3. 建议同时安装 Sodium 0.9.3 / Sodium 0.9.3 is recommended
4. 首次启动生成 `config/seedqueue.json` 与 `config/mcsr/` 配置
   / The first launch creates `config/seedqueue.json` and the files under `config/mcsr/`

## 构建 / Build

```powershell
cd 26.3_speedrun_mods_ports
powershell -File .\build.ps1 -Mod all
```

产物在 `dist/`，历史构建在 `dist/past-build/<Mod>/`（规则见 `docs/build-archive-convention.md`）。
/ Artifacts go to `dist/`; older builds are kept in `dist/past-build/<Mod>/` (see `docs/build-archive-convention.md`).

## 说明 / Notes

- 源码来自各 mod 原作者，本仓库只为 26.3 适配层与必要修补
  / Sources belong to the original authors; this repository only holds the 26.3 adaptation layer and fixes
- 许可证沿用上游 / Licences follow upstream
- 提交 issue 请附 `logs/latest.log` / Attach `logs/latest.log` when reporting issues
