# 26.3 Speedrun Mods — v0.6.0

> [!WARNING]
> 本项目为 AI（LLM）生成移植，无人工代码审查，无法保证与原版逻辑一致，可能存在未知 bug。
> 仅限娱乐，未经 MCSR 社区认证，请勿用于提交排行榜。
>
> This project is a pure AI (LLM) generated port with no human code review. For entertainment only,
> not certified by the MCSR community, and must not be used to submit any leaderboard run.

## 相对 v0.5.6 的变化 / Changes since v0.5.6

### 重构 / Refactor

- **SeedQueue 完全模块化**：按 `queue / preview / wall / render / config` 分包，并将 2620 行的 `SeedQueue` 拆成 15 个管理器
  （队列、预览、墙、会话、生成、抓帧、Tick、生命周期、诊断、基准、条目运维、渲染、设置、条目注册表、世界数据）。
  `QueueEntry`（1085 行）拆出 `EntryCapture` / `EntryGeneration` / `EntryWorldData`。
- **WorldPreview 拆分**：`WorldPreview`（1362 → 569 行）拆出 `TargetManager` / `TerrainManager` / `PreviewLifecycleManager` /
  `LevelBindingManager` / `RenderHooksManager`；`ExternalPreview`（774 → 493 行）拆出 `PreviewSectionHelper` /
  `PreviewSpawnHelper` / `PreviewSkinHelper`。
- **Atum 拆分**：`Atum`（647 → 241 行）拆出 `ResetController` / `WorldCreationHelper` / `AtumSettings`。
- **SpeedrunAPI**：配置界面的分组逻辑抽成 `OptionGrouping`。
- **变量语义化**：SeedQueue 中约 4600 个 `varN` 全部改为语义命名，剩余 0 个；主要文件补充类注释与方法注释。
- **死代码清理**：删除仅写不读的字段、无引用方法、临时诊断代码与 Atum 的 smoke 测试状态机。

### 修复 / Fixes

- **第三人称卡片显示默认皮肤**：墙上预览构建时客户端未连接服务器，`applyRealSkin` 直接返回；现在在无 `PlayerInfo` 时用真实 profile 构造一个，皮肤可正常解析。
- **Atum 固定种子不作用于队列**：队列实例现在读取 Atum 的种子（`-Dseedqueue.fixedSeed` 优先），不再一律随机。
- **`liveEntry` 从未赋值**：进入世界时正确赋值，返回墙/原版建世界时清空；驻留与关服清理逻辑随之生效。
- **关服卡死/看门狗强杀**：关机时改为先 `discard()`（置 DISCARDED、唤醒 park 中的服务端线程）再 `halt`，避免 4 秒后 `Runtime.halt(0)` 导致区块未写完。
- **空 catch 与反射失败可观测**：32 处空 `catch` 改为 debug 日志；Sodium 反射钩子启动时输出 `[preview] sodium hooks resolved/missing`。
- **`logMemory` 移除调用线程 `Thread.sleep(20)`**，避免开启内存日志时的卡顿。

### 版本 / Versions

| 模组 / Mod | 版本 / Version |
| --- | --- |
| SeedQueue | `1.1.0-rc.1+26.3` |
| WorldPreview | `6.4.0-rc.1+26.3` |
| SpeedrunAPI | `2.2.0-rc.1+26.3` |
| StandardSettings | `2.4.0-rc.1+26.3` |
| Atum | `1.5.0-rc.1+26.3` |
| FastReset | `1.7.0-rc.1+26.3` |

## 包含的模组 / Included mods

| 文件 / File | 模组 / Mod |
| --- | --- |
| `1.1.0-rc.1+26.3-SeedQueue.jar` | SeedQueue |
| `6.4.0-rc.1+26.3-WorldPreview.jar` | WorldPreview |
| `2.2.0-rc.1+26.3-Speedrun-API.jar` | SpeedrunAPI |
| `2.4.0-rc.1+26.3-StandardSettings.jar` | StandardSettings |
| `1.5.0-rc.1+26.3-Atum.jar` | Atum |
| `1.7.0-rc.1+26.3-FastReset.jar` | FastReset |

可选 / Optional：`SeedQueue-LockedPool.zip`（锁定池资源包 / locked-pool resource pack）。

## 已知问题 / Known issues

1. 远景仍为黑色 / far distance still black
2. 偶发地形闪烁 / occasional terrain flickering
3. 26.x 水面透明度存在原版回归（MC-206561）/ vanilla water transparency regression
4. 长时间游玩仍可能出现 GC 引起的偶发卡顿 / occasional GC-induced stutter in long sessions

## 安装 / Install

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议 Sodium 0.9.3）
2. 6 个 jar 放入 `mods/`，先删除旧版本
3. 可选：`SeedQueue-LockedPool.zip` 放入 `resourcepacks/` 并在游戏内启用
