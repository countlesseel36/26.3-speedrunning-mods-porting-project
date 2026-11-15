# 26.3 Speedrun Mods — v0.5.3

> [!WARNING]
> **WARNING（警告）** — 本项目为纯AI（LLM）生成移植，无人工代码审查，无法保证与原版逻辑一致，可能存在未知Bug或改变原版游戏机制。仅供娱乐，未经速通社区（MCSR）合法性认证，严禁用于提交任何排行榜成绩。
>
> / This project is a pure AI (LLM) generated port with no human code review; behaviour may differ from vanilla and unknown bugs or gameplay changes are possible. For entertainment only, not certified by the MCSR community, and must not be used to submit any leaderboard run.

## 相对 v0.5.2 的变化 / Changes since v0.5.2

### 修复 / Fixes

- **伤害类型黑名单**：只有 `minecraft:spear` 等已知无法编码的类型改写为 `generic`，其它伤害（火焰等）原样发送，专属音效恢复。/ Damage types use a blacklist: only known-unencodable types are rewritten, so fire and other dedicated sounds are back.
- **手动创建世界不再被队列接管**：新增 `isResetInProgress()` 判断，手动"创建新世界"走 vanilla 流程，不会消费队列实例、也不会卡在 `Preparing for world creation...`。/ Manual world creation is no longer hijacked by the queue.
- **进入世界时连按 Esc 导致标题界面压在世界之上**：切换期间忽略 Esc，并在进入后 5 秒内自动关闭标题/暂停界面。/ Pressing Esc while joining no longer leaves the title screen over the world.
- **M 之后立刻点卡片导致连续 handoff / 卡 `Saving world`**：加入墙点击拦截、`playEntry` 前置条件与冷却、`scheduledJoin` / `pendingPlayNextLock` 过期。/ Rapid M + card clicks no longer cause double hand-offs and a stuck "Saving world".

### 世界文件管理 / World storage

- 队列世界命名改为上游 1.21.4 风格 `SeedQueue-<epochMillis>-<n>-<8hex>`；
- 玩家进入过的世界打上 `seedqueue_played.marker` 并永久保留；
- 未进入的实例在丢弃后立即删除；队列空、关闭游戏、启动游戏时各清理一次；
- 修复历史遗留：曾累积 7593 个无效队列世界目录，现已被清理。/ Fixed 7593 leftover queue world folders.

### 配置界面 / Config UI

- SeedQueue 配置页顶部新增红色提示：仅供尝鲜使用，不保证与 Minecraft 原版行为一致。/ Red warning on top of the SeedQueue config page.

### 版本 / Versions

- SeedQueue `1.0.0-rc.3` → `1.0.0-rc.4`
- SpeedrunAPI `2.1.0-rc.3` → `2.1.0-rc.4`
- WorldPreview `6.3.0-rc.1-build.1` → `6.3.0-rc.1`
- Atum / StandardSettings / FastReset 版本不变。

## 包含的模组 / Included mods

| 文件 / File | 版本 / Version |
| --- | --- |
| `1.0.0-rc.4+26.3-SeedQueue.jar` | 1.0.0-rc.4 |
| `2.1.0-rc.4+26.3-Speedrun-API.jar` | 2.1.0-rc.4 |
| `2.3.0-rc.2+26.3-StandardSettings.jar` | 2.3.0-rc.2 |
| `6.3.0-rc.1+26.3-WorldPreview.jar` | 6.3.0-rc.1 |
| `1.4.0-rc.2+26.3-Atum.jar` | 1.4.0-rc.2 |
| `1.6.0-rc.1+26.3-FastReset.jar` | 1.6.0-rc.1 |

## 已知问题 / Known issues

1. 第三人称下玩家皮肤不显示 / no player skin in third-person previews
2. 远景仍为黑色 / far distance still black
3. 偶尔地形闪烁 / occasional terrain flickering

## 安装 / Install

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议 Sodium 0.9.3）
2. 6 个 jar 放入 mods/，先删除旧版本
