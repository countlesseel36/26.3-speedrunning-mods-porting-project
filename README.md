# 26.3 Speedrun Mods（非官方移植）

> ⚠️ 本项目由中国 1.16.2+ 速通社区**自行移植**，**未获 speedrun.com 官方承认**；使用这些 mod 的速通记录在
> speedrun.com **无法通过审核**。同时这是**测试版本**、**vibe coding 的产物**，可能出现重大 bug，请权衡后使用。

将 1.16.2+ 速通常用的 6 个 Fabric mod 移植到 **Minecraft 26.3**（Fabric Loader 0.19.5+，JDK 25）。

## 包含的模组

| 模组 | 版本 | 说明 |
| --- | --- | --- |
| SeedQueue | `0.9.0-gamma.28+26.3` | 后台批量生成世界 + 墙界面预览，支持瞬间进入 |
| WorldPreview | `6.3.0-gamma.1+26.3` | 世界预览渲染管线（SeedQueue 依赖） |
| SpeedrunAPI | `2.1.0-gamma.1+26.3` | 配置界面框架，提供统一配置页与改键页 |
| StandardSettings | `2.3.0-gamma.1+26.3` | 进世界自动应用预设选项（渲染距离、F3、暂停等） |
| Atum | `1.4.0-gamma.1+26.3` | 自动重置（检测到 SeedQueue 时仅提供建世界设置） |
| FastReset | `1.6.0-gamma.1+26.3` | 秒退到标题（跳过保存） |

可选：`SeedQueue-LockedPool.zip`（锁定池资源包）。

## 主要特性

- **墙界面**：预览满幅无缝拼接，去除卡片底色/标签条与间距；
- **瞬间进入**：从墙点击卡片直接进入已生成的世界，无加载界面；
- **鼠标操作**：左键进入、右键锁定/解锁、中键丢弃并补位；
- **世界继承 Atum 设置**：生成建筑、奖励箱、难度读取 Atum 配置；
- **不干扰游戏内性能**：只开墙时补新世界，游玩时不后台生成；
- **配置页**：单页滚动 + 分类标题 + 悬停说明，Support 改键（键盘/鼠标）；
- **中文化**：SeedQueue 与 StandardSettings 的非调试项均已翻译。

## 已知问题

1. **第三人称卡片仍无法显示玩家皮肤**（预览连接中没有玩家列表，皮肤解析拿不到 `PlayerInfo` 的皮肤）；
2. **预览远景仍有黑边**（预览只渲染已生成区块，范围外是清屏色；尚无法低成本填成天空色）。

## 实测

开发者机器（i9-12900HX + RTX 4060 Laptop）：

| chunkRadius | RPS |
| --- | --- |
| 2 | **2.50** |
| 4 | **1.90** |

## 安装

1. 安装 Fabric Loader 0.19.5+ 与 Fabric API（26.3）；
2. 把 6 个 jar 放进 `mods/`（**先删除旧版本**，同 mod 不能装两份）；
3. 建议同时安装 Sodium 0.9.3；
4. 首次启动生成 `config/seedqueue.json` 与 `config/mcsr/` 下的配置文件。

## 构建

```powershell
cd 26.3_speedrun_mods_ports
powershell -File .\build.ps1 -Mod all
```

产物在 `dist/`，历史构建在 `dist/past-build/<Mod>/`（规则见 `docs/build-archive-convention.md`）。

## 说明

- 源码来自各 mod 原作者（SeedQueue/WorldPreview/SpeedrunAPI/StandardSettings 由 contaria、Atum 由 voidxwalker、FastReset 由 Slackow 等），
  本仓库仅为 **26.3 适配层**与必要修补；
- 许可证沿用上游（各 mod 目录下的 LICENSE）；
- 欢迎提交 issue，但请附上 `logs/latest.log`。

---

## English

Unofficial port of six 1.16.2+ speedrunning mods to **Minecraft 26.3** (Fabric Loader 0.19.5+, JDK 25):
SeedQueue, WorldPreview, SpeedrunAPI, StandardSettings, Atum and FastReset.

**Not endorsed by speedrun.com** — runs using these mods will not pass verification. These are **test builds**.

Highlights: seamless wall previews, instant join from the wall, mouse-driven wall controls (left = play,
right = lock, middle = discard), queued worlds inherit Atum's world settings, the queue only refills while
the wall is open, a single-page scrollable config screen with translated labels and rebindable keys.

**Known issues**: player skin is still missing in third-person cards, and the far distance in previews is
still black (outside the generated chunks).

Benchmark on the developer machine: **2.50 RPS** at `chunkRadius = 2`, **1.90 RPS** at `chunkRadius = 4`.
