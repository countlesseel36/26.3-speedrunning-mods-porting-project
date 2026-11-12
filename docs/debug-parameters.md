# 调试参数 / Debug parameters

所有参数均通过 JVM 启动参数传入（Prism：`实例设置 → Java → JVM 参数`），多个参数用空格分隔，例如：

```
-Dseedqueue.benchmarkConcurrency=8 -Dseedqueue.resourceSampleTicks=100 -Dseedqueue.keysLog=true
```

## 一、跑分（Benchmark）

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `seedqueue.benchmarkConcurrency` | int | 队列容量 | 跑分期间并行构建的实例数，可超过队列容量（行×列）。 |
| `seedqueue.benchmarkResets` | int | 配置值 | 覆盖本次跑分的重置次数（1–1000）。 |
| `seedqueue.resourceSampleTicks` | int | 100 | 资源采样间隔（tick），采样内容为堆 / committed / CPU。 |
| `seedqueue.refillIntervalTicks` | int | 10 | 补队列冷却（tick），越小补得越勤。 |
| `seedqueue.maxParkedServers` | int | 8 | 同时驻留的服务端上限，超过会回收最旧的未锁定实例。 |

## 二、按键与输入

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `seedqueue.keysLog` | boolean | 打印墙界面每次按键的 `raw / normalized / action`，以及键位读写迁移结果。 |
| `seedqueue.previewEveryNthFrame` | int | 预览帧每 N 帧交换一次，默认 2；越大越省 GPU。 |

## 三、预览（Preview）

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `seedqueue.fillMissingChunks` | boolean | true | 抓包时补齐外圈缺失区块。 |
| `seedqueue.chunkRadius` | int | 配置值 | 预览半径（区块），越大卡片可见范围越广、构建越慢。 |
| `seedqueue.extendPreviewRadius` | boolean | false | 就绪后继续生成外圈区块并送入预览。 |
| `seedqueue.previewDwellMs` | int | — | 每张卡预览停留时间。 |
| `seedqueue.previewGapMs` | int | — | 两次预览之间的间隔。 |
| `seedqueue.previewRefreshMs` | int | — | 预览刷新周期。 |
| `seedqueue.previewSupersample` | int | — | 超采样倍率（高于 1 会显著增加显存）。 |
| `seedqueue.blankPreview` | boolean | false | 用空白界面替代预览，用于隔离渲染问题。 |
| `seedqueue.holePreview` | boolean | false | 实时预览悬停卡片。 |
| `seedqueue.livePreview` | boolean | false | 实时预览模式。 |
| `seedqueue.manualPreview` | boolean | false | 手动触发预览刷新。 |
| `seedqueue.offscreenPreview` | boolean | false | 离屏渲染预览（排查用）。 |
| `seedqueue.previewDebug` | boolean | false | 预览调试输出。 |

## 四、日志与诊断

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `seedqueue.memLog` | boolean | 定期输出堆 / 显存估算。 |
| `seedqueue.fpsLog` | boolean | 输出帧率统计。 |
| `seedqueue.cameraLog` | boolean | 输出相机 / 视角信息。 |
| `seedqueue.renderLog` | boolean | 输出渲染流程日志。 |
| `seedqueue.timerLog` | boolean | 输出各阶段计时。 |
| `seedqueue.disconnectLog` | boolean | 输出断开连接的调用栈。 |
| `seedqueue.screenLog` | boolean | 输出界面切换记录。 |
| `seedqueue.sodiumLog` / `seedqueue.sodiumStats` | boolean | Sodium 缓存与统计输出。 |
| `seedqueue.levelListLog` | boolean | 输出实例列表状态。 |
| `seedqueue.graphLog` / `seedqueue.graphDump` | boolean | 帧图（frame graph）日志与导出。 |
| `seedqueue.dumpTargets` / `seedqueue.dumpCaptures` / `seedqueue.dumpRaw` | boolean | 导出渲染目标 / 抓帧 / 原始数据。 |
| `seedqueue.exitWatchdog` | boolean | 退出卡死时打印线程栈并在 4 秒后强制结束进程。 |
| `seedqueue.watchdog` | boolean | 主线程卡顿监控。 |

## 五、行为开关（排查用）

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `seedqueue.disable` | boolean | 完全禁用 SeedQueue 功能，便于确认问题是否由本模组引起。 |
| `seedqueue.test` | boolean | 测试模式（跳过若干正式流程）。 |
| `seedqueue.integrate` | boolean | 使用整合服务端流程。 |
| `seedqueue.queueSize` | int | 覆盖队列容量（行×列）。 |
| `seedqueue.freeze` / `seedqueue.freezeCapture` / `seedqueue.freezeDebug` | boolean | 冻结服务端的相关开关与调试输出。 |
| `seedqueue.handoff` | boolean | 预览交接给真实世界的流程开关。 |
| `seedqueue.bypassWall` | boolean | 跳过墙界面直接进入实例。 |
| `seedqueue.directCapture` / `seedqueue.directLevel` / `seedqueue.cpuPreview` | boolean | 渲染路径的替代实现，用于排查黑屏。 |
| `seedqueue.cardReadback` | boolean | 卡片像素回读（会显著变慢）。 |
| `seedqueue.wallClearBackground` / `seedqueue.wallFullscreenTarget` | boolean | 墙界面绘制方式。 |
| `seedqueue.wallDataLimit` | int | 墙界面同时保留的数据量上限。 |
| `seedqueue.renderBudget` | int | 每帧渲染预算。 |

## 六、自动化 / 冒烟测试（CI 或本地回归用，不建议日常开启）

以下参数会模拟输入或自动执行操作，仅用于回归测试：

```
seedqueue.smoke                  seedqueue.selfTest              seedqueue.configSmoke
seedqueue.configSmokeMod         seedqueue.configListSmoke       seedqueue.wallAutoSmoke
seedqueue.wallSmokeJoin          seedqueue.wallSmokeJoinCount    seedqueue.wallSmokeLock
seedqueue.wallSmokeDelete        seedqueue.wallKeySmoke          seedqueue.wallKeysSmoke
seedqueue.wallEscSmoke           seedqueue.wallF6Smoke           seedqueue.f6Smoke
seedqueue.f3Smoke                seedqueue.titleSmoke            seedqueue.joinShot
seedqueue.resizeSmoke            seedqueue.previewSmoke          seedqueue.liveSmoke
seedqueue.handoffSmoke           seedqueue.lockSmoke             seedqueue.lockResetSmoke
seedqueue.playNextLockSmoke      seedqueue.benchmarkSmoke        seedqueue.blankSmoke
seedqueue.frameCaptureTest       seedqueue.frameOverrideTest     seedqueue.offscreenTest
seedqueue.paintTest              seedqueue.previewShotTest       seedqueue.shotPreview
seedqueue.swapTest               seedqueue.spike                 seedqueue.simulateNativeOom
seedqueue.wallShot               seedqueue.wallShotDelay         seedqueue.wallScreenshot
seedqueue.wallScreenshotTicks    seedqueue.wallGrabEvery         seedqueue.wallDump
seedqueue.wall                    seedqueue.build                 seedqueue.testDiscardTicks
seedqueue.sodiumCache             seedqueue.captureDuringGeneration
```

## 七、注意事项

1. 这是**测试版**，调试参数随时可能增删，不做兼容承诺；
2. 冒烟参数会自动按键/自动进入世界，**不要在日常游戏时开启**；
3. 参数写错（例如 `JvmArgs=` 混进参数行）会导致游戏无法启动，报 `Could not find or load main class`；
4. 排查完请把调试参数移除，避免影响正常性能表现。
