# 26.3 Speedrun Mods（非官方移植 / Unofficial port）

## 警告 / WARNING

**WARNING（警告）** — 这些模组由中国 1.16.2+ 速通社区自行移植，**未获 speedrun.com 官方承认**；使用它们完成的速通记录**无法通过 speedrun.com 审核**。它们是 vibe coding 的产物、属于测试版本：可能存在重大 bug，并可能影响你的速通成绩。请自行权衡后再使用。 / These mods are unofficial ports made by the Chinese 1.16.2+ speedrunning community and are not officially recognized by speedrun.com. Runs made with them will not pass speedrun.com verification. They are products of vibe coding and test builds: major bugs may exist and may affect your runs. Weigh the trade-offs before using them.


**WARNING（窗口分辨率 / Window resolution）** — 卡片预览按当前窗口分辨率渲染。运行中修改窗口大小或使用与预览尺寸不匹配的分辨率，可能出现黑边或画面错位；建议固定窗口尺寸后再开墙。
/ Card previews are rendered at the current window resolution. Resizing the window while a preview is shown, or using a resolution that does not match, can produce black edges or a shifted image — set the window size before opening the wall.

## 包含的模组 / Included mods

| 模组 / Mod | 版本 / Version | 说明 / Description |
| --- | --- | --- |
| SeedQueue | `1.0.0-rc.2+26.3` | 后台生成世界 + 墙界面预览 / Background world generation + wall previews |
| WorldPreview | `6.3.0-rc.1+26.3` | 预览渲染管线 / Preview rendering pipeline |
| SpeedrunAPI | `2.1.0-rc.3+26.3` | 配置界面框架 / Config screen framework |
| StandardSettings | `2.3.0-rc.2+26.3` | 进世界应用预设选项 / Applies preset options on world join |
| Atum | `1.4.0-rc.2+26.3` | 自动重置 / Auto reset |
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
- 队列与内存保护：驻留服务端数量硬性受队列容量限制，超出即回收
  / Queue and memory protection: parked servers are hard-capped by the queue size and recycled beyond it
- 退出流程：清理在后台线程执行，watchdog 保证 4 秒内结束进程
  / Shutdown: teardown runs on a background thread and a watchdog ends the process within 4 seconds

## 性能优化（压力测试）/ Performance & stress test

### 测试环境 / Test setup

开发机 / Developer PC：**RTX 4060 Laptop 8 GB / i9-12900HX (16C24T) / 32 GB DDR5-4800**，窗口 2560×1472。
下列数据均在 SeedQueue 跑分（B 开始 / N 或 Esc 取消）中测得。
/ Measured with SeedQueue's benchmark (B to start, N or Esc to cancel).

### 结果 / Results

| 配置 / Config | RPS | 单实例构建 / Build | 进程 CPU | 整机 CPU | 堆峰值 / Heap |
| --- | --- | --- | --- | --- | --- |
| 并发 4（队列容量）/ concurrency 4 | 2.25 | 1.66 s | 55–61% | 60–70% | 1.9 GB |
| 并发 8（跑分超容量）/ concurrency 8 | 2.54 | 2.7–3.2 s | 74–78% | 81–85% | 2.4 GB |
| `maxGeneratingCount=2` | 2.42 | 1.45–2.21 s | 69–72% | 82–87% | 2.9 GB |
| `maxGeneratingCount=3` | 2.44 | 1.37–2.68 s | 66–72% | 77–88% | 3.0 GB |
| 跑分半径 1（9 区块）/ benchmark radius 1 | **2.74** | 1.35–1.90 s | 53–74% | 83–100% | 2.4 GB |

### 为提高 RPS 做过的尝试 / RPS improvement attempts

| 尝试 / Attempt | 做法 / How | 结果 / Result |
| --- | --- | --- |
| 提高并发 / raise concurrency | 跑分期间允许超出队列容量（4 → 8）/ allow over-capacity during benchmark | +13%（2.25 → 2.54），单实例构建反而变慢 / build time grows |
| 限制同时生成 / cap generating | `maxGeneratingCount` = 2 / 3 | +4–5%，堆峰值升至 3.0 GB / slightly faster, higher heap |
| 缩小跑分半径 / smaller benchmark radius | `Benchmark Chunk Radius` = 1（25 → 9 区块）/ 25 → 9 chunks | **+19%（2.74）**，`gen` 仅从 ~2.0 s 降到 ~1.6 s / only a 20% cut |
| 生成分步化 / stepped generation | 按时间预算把生成拆到多个服务器任务 / split generation across server tasks | **失败**：跨 tick 区块被卸载，地形消失 / failed: chunks unloaded between ticks |
| 分步 + 区块驻留 / stepped + chunk tickets | 分步期间加 FORCED ticket / pin chunks with FORCED tickets | **失败**：地形仍消失 / failed: terrain still vanished |
| 预览帧缓存 / preview frame cache | 复用卡片渲染目标（仿 1.16.1 framebuffer pool）/ reuse card render targets | **失败**：无 `clear()`、无引用计数，出现残影或已销毁纹理 / failed: no clear(), no refcount |

### 瓶颈结论 / Bottleneck conclusions

1. **RPS ≈ 并发 ÷ 单实例构建时间**，而单实例构建里约 70% 是**不可并行的固定成本**（世界创建、出生点预生成、光照）/ about 70% of a build is non-parallelisable fixed cost (world creation, spawn pre-generation, lighting).
2. 提高并发或加限流只有个位数到十几个百分点的收益，整机 CPU 已达 80–100% / concurrency tweaks gain single-digit to low-double-digit percentages; system CPU is already 80–100%.
3. **缩小半径的实测收益是 +19%，不是理论上的 −64% 构建时间**——半径只裁掉外圈请求，固定成本不变 / shrinking the radius measured +19%, not the theoretical 64% cut, because fixed costs dominate.
4. 想继续提速必须削减固定成本（出生点预生成范围、WorldLoader 开销），而不是调并发或半径 / further gains require cutting fixed costs, not tuning concurrency or radius.
5. 上述失败项均已回退，当前行为稳定 / all failed experiments were reverted.

### 已排除的非瓶颈 / Ruled out

- 显存 / VRAM：约 110–140 MB/实例，8 GB 卡并发 16 时约 2 GB / ~110–140 MB per instance, ~2 GB at concurrency 16 on 8 GB;
- 堆内存 / heap：峰值 3.0 GB / 6.1 GB，GC 未成限制 / peak 3.0 GB of 6.1 GB;
- 线程池 / thread pool：`clamp(核数−1, 1, 255)` = 23（本机）/ 23 here, far above concurrency;
- 磁盘与网络 / disk & network：队列世界全在内存 / queued worlds live in memory.
## 已知问题 / Known issues

1. 未修复：第三人称下玩家皮肤消失
   / Not fixed: the player skin disappears in third-person cards
2. 未修复：视角远景仍为黑色
   / Not fixed: the far distance is still black
3. 未修复：实例偶尔出现地形闪烁
   / Not fixed: queued instances occasionally show terrain flickering

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

## 调试参数 / Debug parameters

所有 `-Dseedqueue.*` 启动参数（跑分、预览、日志、行为开关、冒烟测试）按分类整理在：

**[docs/debug-parameters.md](docs/debug-parameters.md)**

各配置项（队列 / 墙 / 性能 / 线程 / 杂项）的说明见 **[docs/seedqueue-config.md](docs/seedqueue-config.md)**。

所有参数均为测试用途，随时可能增删，不做兼容承诺；排查完请移除。
/ All `-Dseedqueue.*` flags are test-only, may change without notice; remove them after debugging.

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
