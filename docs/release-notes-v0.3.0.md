# 26.3 Speedrun Mods — v0.3.0

## 警告 / WARNING

**WARNING** — These mods are unofficial ports made by the Chinese 1.16.2+ speedrunning community and are not officially recognized by speedrun.com. Runs made with them will not pass speedrun.com verification. They are products of vibe coding and test builds: major bugs may exist and may affect your runs. Weigh the trade-offs before using them.

**WARNING（显存 / VRAM）** — 队列中的每个实例都会占用一个渲染目标与显存。队列数量不要超过 6；2K/4K 窗口下请进一步降低，否则有爆显存风险。
/ Every queued instance owns a render target and VRAM. Keep the queue at 6 or fewer, and lower it further on 1440p/4K displays.

**WARNING（窗口分辨率 / Window resolution）** — 卡片预览按当前窗口分辨率渲染，修改窗口尺寸后可能出现黑边或错位；请固定窗口尺寸后再开墙。
/ Card previews are rendered at the current window resolution; resizing the window can produce black edges or a shifted image — set the window size before opening the wall.

## 包含的模组 / Included mods

| 文件 / File | 版本 / Version |
| --- | --- |
| `0.9.0-rc.1-disabled-benchmark.3+26.3-SeedQueue.jar` | 0.9.0-rc.1-disabled-benchmark.3 |
| `6.3.0-rc.1-build.1+26.3-WorldPreview.jar` | 6.3.0-rc.1-build.1 |
| `2.1.0-rc.1-disabled-benchmark.1+26.3-Speedrun-API.jar` | 2.1.0-rc.1-disabled-benchmark.1 |
| `2.3.0-rc.1-build.1+26.3-StandardSettings.jar` | 2.3.0-rc.1-build.1 |
| `1.4.0-rc.1-build.1+26.3-Atum.jar` | 1.4.0-rc.1-build.1 |
| `1.6.0-rc.1-build.1+26.3-FastReset.jar` | 1.6.0-rc.1-build.1 |

## 更新内容（相对 v0.2.2）/ Changes since v0.2.2

### 预览完整性 / Preview completeness

- 修复卡片边缘缺地形：生成循环会随机漏掉一条边（x 或 z），补齐逻辑改为对**外圈四边**强制生成；压测 225 次全部为 `preview packets: 25 (9 filled, expected 25)`
  / Fixed missing terrain at card edges: the generation loop could miss either the x or the z edge, so the refill now force-generates all four edges; 225/225 stress-test samples reported a complete 25-chunk preview
- 补齐仍在服务端冻结（park）之前完成，不阻塞客户端
  / The refill still runs before the queued server is parked, so it never blocks the client

### 退出 / Shutdown

- watchdog 在清理开始**之前**启动，服务端与世界销毁移到后台线程：大规模刷种（1000+ 实例）后关闭窗口，4 秒内结束进程，不再长时间无响应
  / The watchdog now arms before teardown starts and server/world destruction runs on a background thread: after a 1000+ instance session the process ends within 4 seconds instead of freezing

### 跑分 / Benchmark

- 跑分功能有重大 bug，暂时停用；配置页保留按键与选项，并附红色警告
  / Benchmarking is disabled because of a major bug; the key and option remain in the config screen with a red warning

### 界面 / UI

- 配置页顶部新增显存警告，自动换行，不遮挡列表内容
  / The SeedQueue config page shows a wrapped VRAM warning at the top, without covering the list

## 性能（压力测试）/ Performance (stress test)

2K 窗口、队列容量 6，脚本化 smoke 连续重置约 **1200 个实例**：

- 预览补齐 **225/225** 完整，无 `missing preview chunks`；
- **0** 次 `GpuOutOfMemoryException`、**0** 次未捕获异常；
- 实例就绪 **500–650 ms**（峰值 1600 ms）；
- 退出 **≤4 秒**。

## 已知问题 / Known issues

1. 未修复：第三人称下玩家皮肤消失
   / Not fixed: the player skin disappears in third-person cards
2. 未修复：视角远景仍为黑色
   / Not fixed: the far distance is still black
3. 未修复：实例偶尔出现地形闪烁
   / Not fixed: queued instances occasionally show terrain flickering
4. 跑分功能有重大 bug，已暂时停用
   / Benchmarking has a major bug and is disabled for now

## 安装 / Install

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议 Sodium 0.9.3）
2. 6 个 jar 放入 `mods/`，先删除旧版本
