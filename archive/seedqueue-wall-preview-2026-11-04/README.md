# SeedQueue 26.3 墙卡片出生点画面 — 尝试归档

- 归档日期：2026-11-04
- 目标：让 SeedQueue 墙界面的每张卡片显示对应队列实例的出生点画面，视角跟随设置中的第一/第三人称。
- 最终版本：SeedQueue `0.7.0+26.3`，依赖 WorldPreview `6.3.1-beta.4+26.3`。
- 测试实例：`26.3_speedrun_mods_ports/minecraft`（MC 26.3 + Fabric Loader 0.19.5 + Sodium 0.9.3-alpha.1，Atum / FastReset / SpeedrunAPI / StandardSettings / WorldPreview）。

## 最终方案（0.7.0）：生成过程中截取一帧 + 生成完成后冻结实例

每张卡片显示实例世界**在生成过程中**的一帧画面，整帧完整显示在卡片里；实例生成完成后被冻结，直到玩家点击进入。

### 生成过程中的画面

`QueueEntry.startGenerating`：

- 从出生点所在区块向外（ring 0、1、2…）生成区块；
- 每完成一个区块，就把它序列化成 `ClientboundLevelChunkWithLightPacket` 存入 `chunkPackets` 快照。

`SeedQueue.captureGenerationPreview`：

1. 当快照达到 21 个区块（此时仍在生成中）时触发；
2. 用快照构建 `ExternalPreview.fromPackets(...)`（不经过服务器线程等待，不阻塞渲染线程）；
3. 用 `SpawnViewRenderer` 渲染一帧（320x138，与卡片比例一致，完整显示）；
4. 上传为该条目的 `DynamicTexture`，之后不再重渲染——卡片画面就此冻结。

如果实例在截帧前就变成 `READY`，`freezeReadyEntries` 会在冻结前用剩余快照补一帧（`ExternalPreview.build` 兜底）。

### 生成完成后冻结

`SeedQueue.freezeReadyEntries` 在 `READY` 后约 800ms：

- 若卡片仍无图，先补一帧；
- 调用 `QueueEntry.releaseForScreenshot()`：保存并停止后台服务器、关闭 stem 与存储锁——实例在磁盘上冻结；
- 标记 `frozen`。

卡片画面保持冻结；玩家点击条目后从磁盘加载进入（`openWorld(folder)`），已在冒烟测试中验证：`joining queued entry #0` → 成功进入丛林世界。

### 可选：真实游戏画面截图（旧方案）

`-Dseedqueue.shotPreview=true` 仍保留“加入世界后截取真实游戏画面”的流程；其缩略图改为**整帧等比缩放 + 黑边（letterbox）**，不再裁剪角落。

## 里程碑

| 版本 | 方案 | 结果 |
| --- | --- | --- |
| 0.5.0 | CPU 光线步进渲染（贴图采样） | 卡片有画面，但为周期刷新、非真实帧 |
| 0.6.0 | 生成完成后加入世界 → 截取真实游戏画面 → 居中裁剪缩略图 | 真实画面，但需加入世界、裁掉部分内容 |
| 0.7.0 | **生成过程中从区块快照截取一帧 + 生成完成后冻结** | 当前方案 |

## 目录内容

```
jars/
  test_0.7.0+26.3-SeedQueue.jar             最终构建
  test_6.3.1-beta.4+26.3-WorldPreview.jar   配套 WorldPreview
docs/
  seedqueue-live-preview.md                 功能说明（0.7.0）
screenshots/
  wall-preview-generation.png               0.7.0：生成过程中的一帧（3 卡）
  wall-preview-shots.png                    0.6.0：真实游戏画面缩略图
  wall-preview-third-person-shots.png       0.6.0：第三人称截图
  wall-preview-*.png                        0.5.0 CPU 渲染迭代
src/seedqueue/
  SeedQueue.java                            生成截帧 / 冻结 / 截图状态机
  QueueEntry.java                           区块快照、releaseForScreenshot、DynamicTexture
  SpawnViewRenderer.java                    CPU 渲染器（生成帧 & 回退）
  BlockTextureColors.java                   方块贴图采样
  WallScreen.java                           墙渲染（卡片、锁、ESC）
  ...
src/resources/fabric.mod.json               版本 0.7.0+26.3
```

## 关键验证记录

- 生成截帧与冻结日志：
  ```
  [seedqueue] entry #1 ready (seed ..., 25 chunks in 1490 ms)
  [seedqueue] generation preview captured for #1 (22 chunks in snapshot)
  [seedqueue] entry #1 released for screenshot
  [seedqueue] fallback preview captured for #2
  [seedqueue] entry #2 released for screenshot
  ```
- 冻结后点击进入：`[seedqueue] joining queued entry #0` → 进入世界（截图确认丛林地形、HUD 正常）；
- 卡片为 320x138 整帧图像（`seedqueue_capture_*.png`）；
- 运行期间无 crash-report（此前一次 `subList(0,25)` 越界已修复为完整快照拷贝）。

## 早期失败并放弃的 GPU 路线

1. `Screenshot.takeScreenshot(MainTarget)`（GUI 阶段）：恒为纯黑。
2. `Screenshot.grab` 在“墙隐藏 + 预览激活”帧：纯黑；临时 `BlankScreen` 帧能截到 HUD 但世界区域黑。
3. Mixin 覆盖 `GameRenderer.mainRenderTarget()` 返回自建 `TextureTarget`：日志确认 `override==main==TextureTarget`，但 RETURN 处 dump 仍黑。
4. 反射替换 `GameRenderer.mainRenderTarget` 字段：黑。
5. `RenderTarget.copyColorFrom(...)`、`blitAndBlendToTexture(...)` 到卡片目标：黑。
6. 墙卡片直接采样 `TextureTarget.getColorTextureView()`：稳定复现为黑。
7. 抓帧后延迟 1–2 帧 grab：拿到墙界面本身或黑帧。
8. Sodium 日志显示 `drawChunkLayer` OPAQUE/TRANSLUCENT 确实发出（相机 32.5,80,-15.5），但像素不可观测。
9. `LevelRenderer.render` RETURN 与 `Minecraft.renderFrame` 头/尾抓帧均验证过，结论一致。

结论：26.3 的 frame graph 提交/目标绑定使预览帧无法读回，因此改用“区块快照 + CPU 渲染一帧”的方案。

## 过程中修复的其他问题

- `-Dseedqueue.testDiscardTicks` 之前从未生效，测试实例 3 秒后被清空；已接入。
- `grabHoldTicks` / `captureHold` / `wallShotPending` 缺少递减逻辑；已补齐。
- 抓帧时 `previewGrabEntry` 未赋值导致 `consumeGrab` 空转；已修复。
- `WallScreen.keyPressed` 显式处理 ESC（`KeyEvent.isEscape()` 依据 `input()==41` 扫描码），验证返回 TitleScreen。
- 截图时自动暂停（`pauseOnLostFocus`）、HUD/聊天遮挡：截图流程内临时关闭/隐藏。
- CPU 渲染器上方向向量取反导致画面上下颠倒；已修正。

## 已知限制

- 生成帧来自 CPU 预览渲染（非 GPU 游戏帧）：无实体、光影、天气；画面为方块级。
- 为了拿到帧，实例生成完成后会停止后台服务器（冻结），点击进入时从磁盘加载，比预热的服务器略慢。
- 快照构建要求至少 21 个区块；极快生成时可能走 `READY` 兜底路径。

## 复现方式

```powershell
cd 26.3_speedrun_mods_ports
powershell -File .\build.ps1 -Mod seedqueue
powershell -File .\tools\run-smoke.ps1 -Seconds 60 -ModsSource .\dist -ExtraJvm `
  "-Dseedqueue.test=true;-Dseedqueue.integrate=false;-Dseedqueue.testDiscardTicks=20000;-Dseedqueue.wallAutoSmoke=true;-Dseedqueue.dumpCaptures=true;-Dseedqueue.wallGrabEvery=40"
```

产物：`minecraft/screenshots/*.png`、`minecraft/seedqueue_capture_*.png`、`minecraft/logs/latest.log`。
