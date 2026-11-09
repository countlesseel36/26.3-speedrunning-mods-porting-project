# 26.3 Speedrun Mods（非官方移植 / Unofficial port）

## 警告 / WARNING

**WARNING（警告）** — 这些模组由中国 1.16.2+ 速通社区自行移植，**未获 speedrun.com 官方承认**；使用它们完成的速通记录**无法通过 speedrun.com 审核**。它们是 vibe coding 的产物、属于测试版本：可能存在重大 bug，并可能影响你的速通成绩。请自行权衡后再使用。

**WARNING** — These mods are unofficial ports made by the Chinese 1.16.2+ speedrunning community and are not officially recognized by speedrun.com. Runs made with them will not pass speedrun.com verification. They are products of vibe coding and test builds: major bugs may exist and may affect your runs. Weigh the trade-offs before using them.

**WARNING（显存 / VRAM）** — 队列中的每个实例都会占用一个渲染目标与显存。队列数量不要超过 6；在 2K/4K 窗口下请进一步降低，否则有爆显存（GpuOutOfMemoryException）导致游戏崩溃的风险。
/ Every queued instance owns a render target and VRAM. Keep the queue at 6 or fewer, and lower it further on 1440p/4K displays — otherwise the game may crash with a GPU out-of-memory error.

**WARNING（窗口分辨率 / Window resolution）** — 卡片预览按当前窗口分辨率渲染。运行中修改窗口大小或使用与预览尺寸不匹配的分辨率，可能出现黑边或画面错位；建议固定窗口尺寸后再开墙。
/ Card previews are rendered at the current window resolution. Resizing the window while a preview is shown, or using a resolution that does not match, can produce black edges or a shifted image — set the window size before opening the wall.

## 包含的模组 / Included mods

| 模组 / Mod | 版本 / Version | 说明 / Description |
| --- | --- | --- |
| SeedQueue | `0.9.0-rc.1-disabled-benchmark.3+26.3` | 后台生成世界 + 墙界面预览 / Background world generation + wall previews |
| WorldPreview | `6.3.0-rc.1-build.1+26.3` | 预览渲染管线 / Preview rendering pipeline |
| SpeedrunAPI | `2.1.0-rc.1-disabled-benchmark.1+26.3` | 配置界面框架 / Config screen framework |
| StandardSettings | `2.3.0-rc.1-build.1+26.3` | 进世界应用预设选项 / Applies preset options on world join |
| Atum | `1.4.0-rc.1-build.1+26.3` | 自动重置 / Auto reset |
| FastReset | `1.6.0-rc.1-build.1+26.3` | 秒退到标题 / Instant quit to title |

可选 / Optional: `SeedQueue-LockedPool.zip`（锁定池资源包 / locked-pool resource pack）

## 现有功能 / Features

- 墙界面预览满幅无缝拼接，进度文字绘制在卡片内部
  / Seamless full-bleed wall previews with the progress text drawn inside the card
- 点击卡片瞬间进入已生成世界，落点与卡片预览一致
  / Clicking a card joins the generated world instantly, landing exactly where the card showed
- 鼠标操作：左键进入、右键锁定/解锁、中键丢弃并补位
  / Mouse controls: left = play, right = lock/unlock, middle = discard and refill
- 队列世界继承 Atum 的生成建筑、奖励箱与难度
  / Queued worlds inherit Atum's structures, bonus chest and difficulty
- 只在开墙时补新世界，游玩中不后台生成
  / The queue only refills while the wall is open; nothing generates in-game
- 配置页单页滚动、分类标题、悬停说明，键盘与鼠标均可改键，界面中英双语
  / Single scrollable config page with headings, tooltips, rebindable keyboard/mouse keys, Chinese and English UI
- 队列与显存保护：驻留服务端数量硬性受队列容量限制，超出即回收
  / Queue and VRAM protection: parked servers are hard-capped by the queue size and recycled beyond it
- 退出流程：清理在后台线程执行，watchdog 保证 4 秒内结束进程
  / Shutdown: teardown runs on a background thread and a watchdog ends the process within 4 seconds

## 性能优化（压力测试）/ Performance & stress test

在 2K 窗口、队列容量 6 的设置下，用脚本化 smoke 流程连续重置约 **1200 个实例**：

- **225/225** 次预览区块补齐完整（`preview packets: 25 (9 filled, expected 25)`），无缺块；
- **0** 次 `GpuOutOfMemoryException`、**0** 次未捕获异常；
- 实例就绪耗时约 **500–650 ms**，峰值约 1600 ms；
- 关闭窗口后 **4 秒内**进程结束，无长时间无响应。

/ With a 2K window and queue size 6, a scripted smoke run reset about **1200 instances**: all 225 sampled previews filled completely, no GPU out-of-memory errors and no uncaught exceptions; instances became ready in ~500–650 ms (peak 1600 ms) and the process exited within 4 seconds.

建议的 JVM 参数 / Suggested JVM flags:

```
-Xms4G -Xmx4G -XX:+UseG1GC -XX:MaxGCPauseMillis=30 -XX:+ParallelRefProcEnabled
```

## 已知问题 / Known issues

1. 未修复：第三人称下玩家皮肤消失
   / Not fixed: the player skin disappears in third-person cards
2. 未修复：视角远景仍为黑色
   / Not fixed: the far distance is still black
3. 未修复：实例偶尔出现地形闪烁
   / Not fixed: queued instances occasionally show terrain flickering
4. 跑分功能有重大 bug，已暂时停用（按键与选项保留并附警告）
   / Benchmarking has a major bug and is disabled for now (the key and option remain, with a warning)

## 安装 / Install

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议 Sodium 0.9.3）
   / Minecraft 26.3 + Fabric Loader 0.19.5+ (Sodium 0.9.3 recommended)
2. 把 6 个 jar 放入 `mods/`，先删除旧版本
   / Put the six jars into `mods/`, deleting older versions first
3. 首次启动生成 `config/seedqueue.json` 与 `config/mcsr/` 配置
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
