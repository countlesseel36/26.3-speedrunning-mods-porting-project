# 26.3 Speedrun Mods — v0.2.1

> ⚠️ 非官方移植，未获 speedrun.com 承认；测试版本，vibe coding 产物。
> ⚠️ Unofficial ports, **not recognised by speedrun.com**; test builds, vibe-coded.

## 包含的模组 / Included mods

| 文件 / File | 版本 / Version |
| --- | --- |
| `0.9.0-gamma.55+26.3-SeedQueue.jar` | 0.9.0-gamma.55 |
| `6.3.0-gamma.1+26.3-WorldPreview.jar` | 6.3.0-gamma.1 |
| `2.1.0-gamma.4+26.3-Speedrun-API.jar` | 2.1.0-gamma.4 |
| `2.3.0-gamma.3+26.3-StandardSettings.jar` | 2.3.0-gamma.3 |
| `1.4.0-gamma.2+26.3-Atum.jar` | 1.4.0-gamma.2 |
| `1.6.0-gamma.1+26.3-FastReset.jar` | 1.6.0-gamma.1 |

## 更新内容（相对 v0.2.0）/ Changes since v0.2.0

### 稳定性 / Stability

- 修复多次死亡后的逐渐卡顿：换世界时清理无 manager 支撑的废弃预览世界，避免 `ClientLevel` 泄漏
  / Fixed the gradual stutter after repeated deaths: orphaned preview levels are swept instead of leaking `ClientLevel`s
- 移除 StandardSettings 的 mipmap / 各向异性 / 纹理过滤自动应用：这条同步资源重载路径是进世界崩溃的来源
  / Removed StandardSettings' automatic mipmap/anisotropy/texture-filtering apply — that synchronous resource reload was the world-join crash
- 墙界面遍历前对队列列表做快照，避免渲染线程 `ConcurrentModificationException`
  / The wall snapshots the queue list before iterating, avoiding a render-thread `ConcurrentModificationException`
- 配置页裁剪区改为 try/finally，异常不再让 scissor 状态泄漏到后续界面
  / The config screen's scissor is closed in a finally block, so an exception can no longer leak clipping state
- 退出 watchdog 由 12 秒缩短为 4 秒（`-Dseedqueue.exitWatchdogMs`）
  / Exit watchdog shortened from 12s to 4s (`-Dseedqueue.exitWatchdogMs`)

### 出生点 / Spawn

- 不再把 `respawn_radius` 设为 0：死亡重生恢复原版随机分布；首次进入仍与卡片预览一致（服务端传送对齐）
  / `respawn_radius` is no longer forced to 0: deaths respawn randomly like vanilla, while the first join still matches the card (server-side teleport alignment)

### 性能 / Performance

- 卡片纹理复用：同尺寸帧不再每帧新建 `DynamicTexture`
  / Card textures are reused instead of allocating a new `DynamicTexture` every frame
- 预览隔帧渲染（`-Dseedqueue.previewEveryNthFrame`，默认 2）
  / Previews render every other frame (`-Dseedqueue.previewEveryNthFrame`, default 2)
- 跳过没有新预览帧时的整帧拷贝
  / The full-frame copy is skipped when no new preview frame was rendered
- 补卡检查由每 tick 改为每 10 tick（`-Dseedqueue.refillIntervalTicks`）
  / Refill checks run every 10 ticks (`-Dseedqueue.refillIntervalTicks`)
- 预览 chunk builder 线程上限默认 2（`-Dseedqueue.previewWorkers`）
  / Preview chunk builder threads capped at 2 (`-Dseedqueue.previewWorkers`)
- 配置保存改为异步，不再阻塞客户端线程
  / Config saving is asynchronous and no longer blocks the client thread
- `-Dseedqueue.memLog=true` 不再触发强制 full GC
  / `-Dseedqueue.memLog=true` no longer forces a full GC
- 删除遗留的"截图落盘再读回"抓图路径，改用 `Screenshot.takeScreenshot` 直接抓帧缓冲
  / Removed the legacy screenshot-to-disk capture path in favour of `Screenshot.takeScreenshot`

### 界面 / UI

- 区块进度文字改为叠在卡片内部左下角（带半透明底衬），不再落在卡片之间的黑色背景
  / Chunk progress text is drawn inside the card's bottom-left corner (with a translucent backing) instead of on the background between cards
- 配置页分类标题接入语言文件，英文界面下不再显示中文
  / Category headings come from the language files, so the English UI no longer shows Chinese headings
- 「模拟窗口宽度 / 高度」不再是隐藏项，默认 0（跟随真实窗口）
  / "Simulated window width / height" are no longer hidden; default 0 (follow the real window)
- 队列上限提升到 10，跑分次数上限调整为 1000
  / Queue capacity raised to 10, benchmark reset cap set to 1000
- 「全部切换」「使用 StandardSettings」移到配置页最上方
  / "Toggle all" and "use StandardSettings" moved to the top of the config page
- 新增红色警告：模拟分辨率必须与窗口一致、队列超过 6 有爆显存风险
  / New red warnings: the simulated resolution must match the window, and queues beyond 6 risk running out of VRAM

## 已知问题 / Known issues

1. 第三人称卡片仍无法显示玩家皮肤
   / Player skin is still missing in third-person cards
2. 预览远景仍是黑色
   / The far distance in previews is still black
3. 关闭窗口后仍需 watchdog 在 4 秒后结束进程（Sodium/WorldPreview 内有非守护线程池未关闭）
   / The process still needs the 4s watchdog after closing the window (an unnamed non-daemon pool inside Sodium/WorldPreview is never shut down)

## 安装 / Install

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议 Sodium 0.9.3 与 Fabric API）
   / Minecraft 26.3 + Fabric Loader 0.19.5+ (Sodium 0.9.3 and Fabric API recommended)
2. 6 个 jar 放入 `mods/`，先删除旧版本
   / Put the six jars into `mods/`, deleting older versions first
