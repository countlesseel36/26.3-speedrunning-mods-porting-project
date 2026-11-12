# 26.3 Speedrun Mods — v0.4.0

## 警告 / WARNING

**WARNING（测试版本）** — 社区自行移植，未获 speedrun.com 官方承认；vibe coding 产物，可能存在重大 bug。
/ Unofficial community ports, **not recognised by speedrun.com**; test builds, vibe-coded, major bugs may exist.

## 包含的模组 / Included mods

| 文件 / File | 版本 / Version |
| --- | --- |
| ```0.9.0-rc.5+26.3-SeedQueue.jar``` | 0.9.0-rc.5 |
| ```6.3.0-rc.1+26.3-WorldPreview.jar``` | 6.3.0-rc.1 |
| ```2.1.0-rc.3+26.3-Speedrun-API.jar``` | 2.1.0-rc.3 |
| ```2.3.0-rc.2+26.3-StandardSettings.jar``` | 2.3.0-rc.2 |
| ```1.4.0-rc.2+26.3-Atum.jar``` | 1.4.0-rc.2 |
| ```1.6.0-rc.1+26.3-FastReset.jar``` | 1.6.0-rc.1 |

> 发布命名不再带 build 号；此前 build 号不为 1 的模组，rc 号进一位。
> Release filenames no longer carry a build number; mods whose build was not 1 moved up one rc.

## 跑分功能回归 / Benchmark is back

- 按 **B** 开始跑分，**N / Esc** 取消，结果显示 15 秒后自动消失；
- 跑分期间允许超出队列容量并行构建；跑分结束后恢复；
- 结果面板显示 `N resets in X s (Y RPS)`；
- 修复：重复按 B 会重启计数、N/Esc 无法中断、重置循环只跑一次。
/ Press **B** to start, **N / Esc** to cancel, the result panel disappears after 15 s. The queue may exceed its configured size while benchmarking. Fixed: repeated B restarts, N/Esc not interrupting, the reset loop stopping after one run.

## 对配置的影响（以开发者 PC 实测）/ Configuration impact (measured on the developer's PC)

开发机：**RTX 4060 Laptop 8 GB / i9-12900HX (16C24T) / 32 GB DDR5-4800**

| 并发 | RPS | 单实例构建 | 进程 CPU | 整机 CPU | 堆峰值 |
| --- | --- | --- | --- | --- | --- |
| 4（默认，受 2×2 队列容量限制） | 2.25 | 1.66 s | 55–61% | 60–70% | 1.9 GB |
| 8（跑分并发，超出容量） | 2.54 | 2.7–3.2 s | 74–78% | 81–85% | 2.4 GB |

要点：

- 并发翻倍只提升约 **13%**，单实例构建时间反而变长 → 属于资源争抢，不是线程池不足；
- Minecraft 后台线程数 = `clamp(cores-1, 1, max.bg.threads=255)`，本机为 **23**，远高于并发数；
- 显存估算约 **110–140 MB/实例**（2 张窗口尺寸渲染目标 + 预览顶点数据），并发 16 也只有约 2 GB / 8 GB，**不会爆显存**；
- 因此已**移除所有显存警告**（配置页红字、悬停提示中的相关文案）；
- 追求更高 RPS 应降低单实例成本（缩小预览半径、调高生成百分比上限），而不是继续加并发。
/ Doubling concurrency gains only ~13% while per-instance build time grows, i.e. resource contention rather than a thread-pool limit. Background executor = clamp(cores-1,1,255) = 23 here. VRAM is ~110–140 MB per instance, so 16 instances stay around 2 GB of 8 GB — VRAM warnings were removed. To raise RPS, reduce per-instance cost instead of raising concurrency.

## 其他改动 / Other changes

- 生成百分比：跑分期间强制 80%，取消或跑满后恢复玩家设定值；
- 修复改键页键位显示错乱（统一按原版 `InputConstants` 语义存取与显示）；
- 修复 `Play Next Locked World`（M）：支持未就绪的锁定实例、连续按键去抖、世界内直接切换且跳过保存；
- 修复锁定实例在刷种若干轮后消失；
- 配置页：新增置顶「全局设置」大类，调试渲染开关归入「调试」，中英文顺序统一。

## 已知问题 / Known issues

1. 第三人称下玩家皮肤不显示 / no player skin in third-person previews
2. 远景仍为黑色 / far distance still black
3. 偶尔地形闪烁 / occasional terrain flickering

## 安装 / Install

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议 Sodium 0.9.3）
2. 6 个 jar 放入 `mods/`，先删除旧版本
