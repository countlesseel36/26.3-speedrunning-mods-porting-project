# SeedQueue 调试参数（-Dseedqueue.*）

> 适用版本：`1.1.0-beta.8+26.3`。截图回读管线（`PreviewCapture` / `PreviewPipeline` / `SpawnViewRenderer`）已移除，
> 卡片预览统一走「外部预览世界 + 帧交换」。原先的自测开关（`*Smoke`、`wallShot*`、`dumpRaw` 等）已删除。

```
java -Dseedqueue.memLog=true -Dseedqueue.queueSize=6 ...
```

## 队列与生成

| 参数 | 作用 |
| --- | --- |
| `-Dseedqueue.queueSize` | 覆盖队列实例数 |
| `-Dseedqueue.chunkRadius` | 覆盖预生成半径 |
| `-Dseedqueue.maxGeneratingCount` | 同时生成上限，0 = 不限 |
| `-Dseedqueue.maxParkedServers` | 驻留服务端上限（默认 8） |
| `-Dseedqueue.refillIntervalTicks` | 补位间隔（tick） |
| `-Dseedqueue.fillMissingChunks` | 生成后补抓缺失区块包 |
| `-Dseedqueue.captureDuringGeneration` | 生成中提前构建预览 |
| `-Dseedqueue.freeze` | 冻结预览相关 |
| `-Dseedqueue.freezeCapture` | 冻结时继续抓帧 |
| `-Dseedqueue.freezeDebug` | 冻结调试输出 |
| `-Dseedqueue.extendPreviewRadius` | 临时扩大预览半径 |
| `-Dseedqueue.integrate` | 与 Atum 集成 |
| `-Dseedqueue.disable` | 整体禁用 SeedQueue |
| `-Dseedqueue.wall` | 仅启用墙 |

## 墙与预览

| 参数 | 作用 |
| --- | --- |
| `-Dseedqueue.bypassWall` | 跳过墙直接进入 |
| `-Dseedqueue.wallPreview` | 墙面实时预览（默认 true） |
| `-Dseedqueue.livePreview` | 连续预览循环 |
| `-Dseedqueue.manualPreview` | 关闭自动预览循环 |
| `-Dseedqueue.previewDwellMs` | 卡片预览停留时间 |
| `-Dseedqueue.previewGapMs` | 两次预览最小间隔 |
| `-Dseedqueue.previewRefreshMs` | 预览刷新间隔 |
| `-Dseedqueue.wallOpenDelayMs` | 开墙延迟 |
| `-Dseedqueue.wallClearBackground` | 墙黑底 |
| `-Dseedqueue.wallFullscreenTarget` | 全屏渲染目标 |
| `-Dseedqueue.wallDataLimit` | 墙数据传输上限 |

## 跑分

| 参数 | 作用 |
| --- | --- |
| `-Dseedqueue.benchmarkResets` | 跑分重置次数 |
| `-Dseedqueue.benchmarkConcurrency` | 跑分并发实例数 |
| `-Dseedqueue.resourceSampleTicks` | 资源采样间隔 |

## 渲染与图形

| 参数 | 作用 |
| --- | --- |
| `-Dseedqueue.sodiumStats` | Sodium 统计 |
| `-Dseedqueue.sodiumLog` | Sodium 缓存日志 |
| `-Dseedqueue.graphLog` | 帧图日志 |
| `-Dseedqueue.graphDump` | 导出帧图 |
| `-Dseedqueue.screenLog` | 界面切换日志 |
| `-Dseedqueue.levelListLog` | 世界列表日志 |
| `-Dseedqueue.openWallTrace` | 开墙调用栈 |

## 诊断

| 参数 | 作用 |
| --- | --- |
| `-Dseedqueue.memLog` | 每分钟内存/队列统计 |
| `-Dseedqueue.fpsLog` | 帧率日志 |
| `-Dseedqueue.spike` | 长帧记录 |
| `-Dseedqueue.cameraLog` | 相机日志 |
| `-Dseedqueue.timerLog` | 计时器日志 |
| `-Dseedqueue.keysLog` | 键位解析日志 |

## 生命周期

| 参数 | 作用 |
| --- | --- |
| `-Dseedqueue.exitWatchdog` | 退出残留检测 |
| `-Dseedqueue.exitWatchdogMs` | 看门狗等待 |
| `-Dseedqueue.watchdog` | 卡死看门狗 |

## 说明

- 与 `config/seedqueue.json` 同名的开关会覆盖配置值；
- 诊断类开关只在排障时开启。

## SeedQueue 背包同步诊断

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `-Dseedqueue.inventoryDebug=true` | false | 记录服务端点击/槽位比较、客户端容器同步包、点击后结果 |
| `-Dseedqueue.inventoryResync=false` | true | **关闭**加入世界后与开容器时的强制全量同步，用于复现“同类物品不合并”问题 |
