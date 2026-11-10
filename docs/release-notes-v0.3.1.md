# 26.3 Speedrun Mods — v0.3.1

## 警告 / WARNING

**WARNING（警告）** — 这些模组由中国 1.16.2+ 速通社区自行移植，**未获 speedrun.com 官方承认**；使用它们完成的速通记录**无法通过 speedrun.com 审核**。它们是 vibe coding 的产物、属于测试版本：可能存在重大 bug，并可能影响你的速通成绩。请自行权衡后再使用。

**WARNING** — These mods are unofficial ports made by the Chinese 1.16.2+ speedrunning community and are not officially recognized by speedrun.com. Runs made with them will not pass speedrun.com verification. They are products of vibe coding and test builds: major bugs may exist and may affect your runs. Weigh the trade-offs before using them.

**WARNING（显存 / VRAM）** — 队列中的每个实例都会占用渲染目标与显存，请把队列控制在 6 个以内，2K/4K 窗口下再降低。
/ Every queued instance owns a render target and VRAM; keep the queue at 6 or fewer and lower it on 1440p/4K displays.

**WARNING（窗口分辨率 / Window resolution）** — 卡片预览按当前窗口分辨率渲染，改窗口大小后可能出现黑边或错位。
/ Previews render at the current window resolution; resizing can produce black edges or a shifted image.

## 包含的模组 / Included mods

| 文件 / File | 版本 / Version |
| --- | --- |
| `0.9.0-rc.2-disabled-benchmark.1+26.3-SeedQueue.jar` | 0.9.0-rc.2-disabled-benchmark.1 |
| `6.3.0-rc.1-build.1+26.3-WorldPreview.jar` | 6.3.0-rc.1-build.1 |
| `2.1.0-rc.1-disabled-benchmark.1+26.3-Speedrun-API.jar` | 2.1.0-rc.1-disabled-benchmark.1 |
| `2.3.0-rc.1-build.1+26.3-StandardSettings.jar` | 2.3.0-rc.1-build.1 |
| `1.4.0-rc.1-build.1+26.3-Atum.jar` | 1.4.0-rc.1-build.1 |
| `1.6.0-rc.1-build.1+26.3-FastReset.jar` | 1.6.0-rc.1-build.1 |

## 重大修复：显存泄漏 / Critical fix: VRAM leak

**问题**：每个队列实例都会创建两个**窗口分辨率**的渲染目标（`displayTarget` 与 `renderTarget`，2K 下各约 20 MB），但 `discard()` 只释放了卡片纹理，这两个 GPU 缓冲从未被销毁。连续重置几百次后累计泄漏可达数 GB，最终 Sodium 申请 256 MiB arena 失败并崩溃：

```
GpuOutOfMemoryException: Could not allocate buffer of 268435456
   at ArenaAggregator.getBufferOfSizeAtLeast
```

**修复**：

1. `discard()` 中显式销毁 `displayTarget` 与 `renderTarget`（`destroyBuffers()`）；
2. 销毁动作**切回渲染线程**执行 —— 首次修复时在 prepare 线程调用，244 次全部抛 `Rendersystem called from wrong thread`，等于没释放；
3. 顺手修掉离开墙时未释放的 Sodium preview arena。

**验证**：脚本化 smoke 连续重置 250+ 次、386 次预览采样，`failed to destroy` 警告 **0** 次、`GpuOutOfMemoryException` **0** 次（修复前必然出现），预览区块补齐 `preview packets: 25` **386/386** 全部完整。

/ **Problem**: every queued instance created two window-sized render targets (~20 MB each at 1440p) that `discard()` never freed. After a few hundred resets the leak reached gigabytes and Sodium failed to allocate its 256 MiB arena.

**Fix**: destroy both targets in `discard()`, and do it **on the render thread** — the first attempt ran on the prepare thread, where `destroyBuffers()` threw "Rendersystem called from wrong thread" 244 times and freed nothing.

**Verified**: 250+ consecutive resets with 386 preview samples — zero destroy failures, zero GPU OOM (which reproduced before the fix), and all 386 previews reported a complete 25-chunk snapshot.

## 其他修复 / Other fixes

- `SeedQueue.perspective` 配置项已移除，预览相机恒为第一人称
  / The `seedqueue.perspective` option was removed; the preview camera is always first person
- 游戏内暂停界面（含 F3+Esc 无界面暂停）按 F6 可正常回到墙界面
  / F6 now returns to the wall from the in-game pause screen, including the F3+Esc pause
- 跑分功能因重大 bug 暂时停用（按键与选项保留并附警告）
  / Benchmarking stays disabled because of a major bug (key and option kept with a warning)

## 已知问题 / Known issues

1. 未修复：第三人称下玩家皮肤消失 / Not fixed: the player skin disappears in third-person cards
2. 未修复：视角远景仍为黑色 / Not fixed: the far distance is still black
3. 未修复：实例偶尔出现地形闪烁 / Not fixed: queued instances occasionally show terrain flickering
4. 跑分功能有重大 bug，已暂时停用 / Benchmarking has a major bug and is disabled for now

## 安装 / Install

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议 Sodium 0.9.3）
2. 6 个 jar 放入 `mods/`，先删除旧版本
