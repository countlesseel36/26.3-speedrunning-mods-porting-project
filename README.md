# 26.3 Speedrun Mods（非官方移植 / Unofficial port）

## 警告 / WARNING

**WARNING（警告）** — 这些模组由中国 1.16.2+ 速通社区自行移植，**未获 speedrun.com 官方承认**；使用它们完成的速通记录**无法通过 speedrun.com 审核**。它们是 vibe coding 的产物、属于测试版本：可能存在重大 bug，并可能影响你的速通成绩。请自行权衡后再使用。 / These mods are unofficial ports made by the Chinese 1.16.2+ speedrunning community and are not officially recognized by speedrun.com. Runs made with them will not pass speedrun.com verification. They are products of vibe coding and test builds: major bugs may exist and may affect your runs. Weigh the trade-offs before using them.


**WARNING（窗口分辨率 / Window resolution）** — 卡片预览按当前窗口分辨率渲染。运行中修改窗口大小或使用与预览尺寸不匹配的分辨率，可能出现黑边或画面错位；建议固定窗口尺寸后再开墙。
/ Card previews are rendered at the current window resolution. Resizing the window while a preview is shown, or using a resolution that does not match, can produce black edges or a shifted image — set the window size before opening the wall.

## 包含的模组 / Included mods

| 模组 / Mod | 版本 / Version | 说明 / Description |
| --- | --- | --- |
| SeedQueue | `0.9.0-rc.3-disabled-benchmark.16+26.3` | 后台生成世界 + 墙界面预览 / Background world generation + wall previews |
| WorldPreview | `6.3.0-rc.1+26.3` | 预览渲染管线 / Preview rendering pipeline |
| SpeedrunAPI | `2.1.0-rc.3+26.3` | 配置界面框架 / Config screen framework |
| StandardSettings | `2.3.0-rc.2+26.3` | 进世界应用预设选项 / Applies preset options on world join |
| Atum | `1.4.0-rc.1-build.1+26.3` | 自动重置 / Auto reset |
| FastReset | `1.6.0-rc.1+26.3` | 秒退到标题 / Instant quit to title |

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

### 跑分结果（开发机实测）/ Benchmark results (developer PC)

开发机：**RTX 4060 Laptop 8 GB / i9-12900HX (16C24T) / 32 GB DDR5-4800**，窗口 2560×1440，`maxWorldGenerationPercentage = 40`。

| 并发 | 重置次数 | 总耗时 | RPS | 稳态 build | 进程 CPU | 整机 CPU | 堆峰值 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 4（默认队列容量） | 61 | 27.1 s | **2.25** | 1.66 s | 55–61% | 60–70% | 1.9 GB |
| 8（跑分并发，超出容量） | 61 | 24.0 s | **2.54** | 2.7–3.2 s | 74–78% | 81–85% | 2.4 GB |

单实例构建耗时构成（子阶段计时）：`server` 0.2–1.1 s、`gen` 1.1–2.2 s（占 60–75%）、`capture` 0.15–0.4 s。

结论：并发翻倍仅 +13%，单实例反而变慢，说明瓶颈是资源争抢而非线程池；后台执行器线程数为 `clamp(核数-1, 1, 255)`，本机 23，远高于并发数。显存约 110–140 MB/实例，8 GB 显卡在并发 16 时约 2 GB，不会爆显存。

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

## 跑分（Benchmark）

按 **B** 开始、**N / Esc** 取消，结果 15 秒后自动消失；跑分期间允许超出队列容量并行构建。

开发机实测（RTX 4060 Laptop 8 GB / i9-12900HX / 32 GB DDR5-4800）：

| 并发 | RPS | 单实例构建 | 进程 CPU | 整机 CPU | 堆峰值 |
| --- | --- | --- | --- | --- | --- |
| 4 | 2.25 | 1.66 s | 55–61% | 60–70% | 1.9 GB |
| 8 | 2.54 | 2.7–3.2 s | 74–78% | 81–85% | 2.4 GB |

并发翻倍仅 +13%，单实例反而变慢 → 瓶颈是资源争抢，不是线程池；显存约 110–140 MB/实例，不会爆，故已移除所有显存警告。

## 开发技术难点 / Engineering challenges

- **多整合服务端共存**：SeedQueue 会同时运行多个 IntegratedServer，每个都有独立的注册表（registry）。跨服务端的物品/伤害类型引用无法编码，曾导致 `damage_event` 发包失败直接把玩家踢下线；解决方式是在发送前按当前服务端注册表校验并丢弃非法包。
  / **Multiple integrated servers**: SeedQueue runs several IntegratedServers, each with its own registries. Cross-server holders cannot be encoded, which made `damage_event` fail and disconnect the player; packets are now validated against the current server's registry and dropped when invalid.

- **渲染目标与显存**：每个队列实例需要一张窗口分辨率的渲染目标（2K 下约 20 MB）。最初这些目标在丢弃实例时从未释放，累计数 GB 后 Sodium 申请 256 MiB arena 失败而崩溃；且 `destroyBuffers()` 必须在渲染线程调用（在准备线程调用会抛 "Rendersystem called from wrong thread"，等于没释放）。
  / **Render targets and VRAM**: every instance needs a window-sized render target (~20 MB at 1440p). They were never freed on discard, so after a few hundred resets Sodium could not allocate its 256 MiB arena. `destroyBuffers()` also has to run on the render thread — calling it from the prepare thread throws and frees nothing.

- **预览区块完整性**：预览世界由生成阶段抓取的区块包构建，而生成循环会漏掉某一条边（有时是 x，有时是 z），导致卡片边缘出现黑带。修复是在服务端冻结前对**外圈四边**强制补齐，并校验快照数量。
  / **Preview completeness**: the preview level is built from chunk packets captured while generating, and the loop could miss one edge (sometimes x, sometimes z), which showed as a black band. The fix force-generates all four edges before the server is parked and verifies the snapshot size.

- **26.3 渲染管线（frame graph）**：把预览直接渲染进小尺寸卡片目标会绕过帧图绑定，表现为卡片全黑并崩溃；`copyColorFrom` 又不做缩放，缩尺寸只会裁剪。最终只能保持目标为窗口尺寸。
  / **26.3 frame graph**: rendering previews straight into a smaller card target bypasses the frame-graph binding (black cards + crash), while `copyColorFrom` does not scale (it crops). The target therefore has to stay window-sized.

- **SDL3 换库的输入差异**：26.3 改用 SDL3 后，键位名称与键码不再一一对应。曾把键位按数字序列化，鼠标键（负值）被当成键盘键重建，导致游戏内所有鼠标绑定失效；现在仍按名称存取，并对配置做校验。
  / **SDL3 input differences**: key names no longer map one-to-one to codes. Serialising keys numerically recreated mouse keys (negative codes) as keyboard keys and broke every mouse binding; storage stays name-based with validation.

- **退出流程**：Sodium/WorldPreview 内部有未关闭的非守护线程池，进程无法自然结束；现在 watchdog 在清理前启动、清理放到后台线程，4 秒内结束进程。
  / **Shutdown**: Sodium/WorldPreview keep non-daemon pools alive, so the process never ends on its own. The watchdog now arms before teardown, which runs on a background thread and ends the process within 4 seconds.

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

## 致谢 / Credits

- 感谢 **ttkuilei** 的实测与反馈：改键持久化、鼠标绑定失效、配置页分组与顺序、显存泄漏与掉线等问题均来自其测试报告。
  / Thanks to **ttkuilei** for testing and reporting: the key persistence, broken mouse bindings, config grouping/ordering, VRAM leak and disconnect issues all came from their reports.

## 说明 / Notes

- 源码来自各 mod 原作者，本仓库只为 26.3 适配层与必要修补
  / Sources belong to the original authors; this repository only holds the 26.3 adaptation layer and fixes
- 许可证沿用上游 / Licences follow upstream
- 提交 issue 请附 `logs/latest.log` / Attach `logs/latest.log` when reporting issues
