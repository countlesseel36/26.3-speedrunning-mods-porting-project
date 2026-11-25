# 26.3 Speedrun Mods — v0.6.1

> [!WARNING]
> 本项目为 AI（LLM）生成移植，无人工代码审查，无法保证与原版逻辑一致，可能存在未知 bug。
> 仅限娱乐，未经 MCSR 社区认证，请勿用于提交排行榜。
>
> This project is a pure AI (LLM) generated port with no human code review. For entertainment only,
> not certified by the MCSR community, and must not be used to submit any leaderboard run.

## 相对 v0.6.0 的变化 / Changes since v0.6.0

### 修复 / Fixes

- **从墙上进入世界后，背包同类物品偶尔不合并**：进入世界前不再把客户端每帧切换为预览对象，
  避免容器同步包作用到预览玩家上；重新登录后恢复正常的现象应不再出现。
  Entering a world no longer keeps swapping `minecraft.player/level` to the preview objects,
  which could route container sync packets to the preview player.
- **Atum 修改固定种子后不重建队列**：选项界面保存种子现在会通知 SeedQueue 重建，已生成的卡片不再停留在旧种子。
  Saving a seed in Atum options now notifies SeedQueue to rebuild the queue.

### 其他 / Other

- 内存：进入世界后释放预览世界与隐藏卡片预览；断开世界后释放 `LevelExtractor` / `Camera` /
  `ParticleEngine` / Sodium 对旧 `ClientLevel` 的引用，便于 GC 回收。
- 新增每 60 秒 `heap trend` 日志（`-Dseedqueue.heapLog=false` 可关闭）。
- 退出看门狗识别空闲线程池：正常关机不再刷 WARN 与线程堆栈。
- 新增容器/背包同步诊断：`-Dseedqueue.inventoryDebug=true`（记录点击、服务端结果、客户端同步包），
  `-Dseedqueue.inventoryResync=false` 可关闭加入世界后的强制全量同步用于复现。
- 日志格式修复：heap pressure 百分比与 MB 现在会正确输出。

### 版本 / Versions

| 模组 / Mod | 版本 / Version |
| --- | --- |
| SeedQueue | `1.1.0-rc.2+26.3` |
| WorldPreview | `6.4.0-rc.2+26.3` |
| SpeedrunAPI | `2.2.0-rc.1+26.3` |
| StandardSettings | `2.4.0-rc.1+26.3` |
| Atum | `1.5.0-rc.2+26.3` |
| FastReset | `1.7.0-rc.1+26.3` |

## 包含的模组 / Included mods

| 文件 / File | 模组 / Mod |
| --- | --- |
| `1.1.0-rc.2+26.3-SeedQueue.jar` | SeedQueue |
| `6.4.0-rc.2+26.3-WorldPreview.jar` | WorldPreview |
| `2.2.0-rc.1+26.3-Speedrun-API.jar` | SpeedrunAPI |
| `2.4.0-rc.1+26.3-StandardSettings.jar` | StandardSettings |
| `1.5.0-rc.2+26.3-Atum.jar` | Atum |
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
