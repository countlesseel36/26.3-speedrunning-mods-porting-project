# 26.3 Speedrun Mods — v0.2.2

> ⚠️ 非官方移植，未获 speedrun.com 承认；测试版本，vibe coding 产物。
> ⚠️ Unofficial ports, **not recognised by speedrun.com**; test builds, vibe-coded.

## 包含的模组 / Included mods

| 文件 / File | 版本 / Version |
| --- | --- |
| `0.9.0-gamma.70+26.3-SeedQueue.jar` | 0.9.0-gamma.70 |
| `6.3.0-gamma.2+26.3-WorldPreview.jar` | 6.3.0-gamma.2 |
| `2.1.0-gamma.4+26.3-Speedrun-API.jar` | 2.1.0-gamma.4 |
| `2.3.0-gamma.3+26.3-StandardSettings.jar` | 2.3.0-gamma.3 |
| `1.4.0-gamma.2+26.3-Atum.jar` | 1.4.0-gamma.2 |
| `1.6.0-gamma.1+26.3-FastReset.jar` | 1.6.0-gamma.1 |

## 更新内容（相对 v0.2.1）/ Changes since v0.2.1

### 预览完整性 / Preview completeness

- 修复卡片边缘缺地形：生成时少走了 `|dz| = radius` 两行区块，补齐逻辑在用 `getChunk(..., false)` 时对未加载区块返回 null 被静默跳过；现在只针对这两行**强制生成**，预览世界覆盖完整的 5×5（日志 `preview packets: 25 (9 filled, expected 25)`）
  / Fixed missing terrain at the card edges: generation skipped the two `|dz| = radius` rows and the refill used `getChunk(..., false)`, which silently returned null. Only those two rows are now force-generated, so the preview covers the full 5×5 (`preview packets: 25 (9 filled, expected 25)`)
- 预览补齐发生在服务端被冻结（park）之前，不会阻塞客户端
  / The refill runs before the queued server is parked, so it never blocks the client

### 进入世界 / Joining

- 修复进入世界偶尔弹出 `Invalid move player packet received`：出生点对齐改用连接级传送（会下发位置包并重置客户端移动状态），并抬高 0.1 格避免卡进方块
  / Fixed the occasional `Invalid move player packet received`: spawn alignment now uses the connection teleport (which sends the position packet and resets client movement) and raises the player by 0.1 blocks

### 视角 / Perspective

- 删除 `seedqueue.perspective` 配置项（它从未出现在配置页），预览相机始终为第一人称
  / Removed the `seedqueue.perspective` option (it never showed up in the config screen); the preview camera is always first person

### 工程 / Engineering

- 保留诊断日志：`-Dseedqueue.cameraLog=true` 会输出相机位置、预览区块数与渲染距离
  / Kept the diagnostics: `-Dseedqueue.cameraLog=true` logs camera position, preview chunk count and render distance

## 已知问题 / Known issues

1. 第三人称卡片仍可能不完整、且不显示玩家皮肤
   / Third-person cards can still be incomplete and show no player skin
2. 关闭窗口后仍需 watchdog 约 4 秒结束进程
   / The process still needs the ~4s watchdog after closing the window

## 安装 / Install

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议 Sodium 0.9.3）
   / Minecraft 26.3 + Fabric Loader 0.19.5+ (Sodium 0.9.3 recommended)
2. 6 个 jar 放入 `mods/`，先删除旧版本
   / Put the six jars into `mods/`, deleting older versions first
