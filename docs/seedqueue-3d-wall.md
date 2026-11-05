# SeedQueue 26.3 墙卡片 3D 实时预览（0.8.0）

墙界面每张卡片显示该队列世界用 **26.3 原生渲染管线**（FrameGraph + Sodium 地形）渲染出的真实 3D 画面：
地形、天空、云、雾、水面全部来自正常世界渲染，而不是读回 CPU 的缩略图。

## 一、渲染路径

1. **选卡**：`SeedQueue.frameSwapTick` 在墙打开时挑一个 READY 条目，用
   `WorldPreview.activateExternal(props)` 把它的 `ExternalPreview.level` 交给 `Minecraft.level`
   （和"创建世界预览"完全相同的注入路径），驻留 `seedqueue.previewDwellMs`（默认 2000ms）。
2. **正常帧**：`Minecraft.renderFrame` → `GameRenderer.update` → `extract` → `GameRenderer.render` →
   `LevelRenderer.render` 构建并执行 `FrameGraphBuilder`。
3. **FrameGraph**：`importExternal("main", gameRenderer.mainRenderTarget())` 导入主目标；
   `clear / sky / main` 三个 pass 依次执行（`FrameGraphPassMixin` 可逐 pass 打印）。
4. **GUI 边界复制**：`GameRenderer.render()` 中 `renderLevel()` 之后、`GuiRenderer.render()` 之前，
   `WallCaptureMixin` 把主目标 `copyColorFrom` 到该条目的 `displayTarget`。
   `displayTarget` 只当采样源，绝不同帧再当附件（无 framebuffer feedback）。
5. **墙采样**：`WallScreen` 用
   `graphics.blit(displayTarget.getColorTextureView(), sampler, x0,y0,x1,y1, u0,u1,v0,v1)`
   直接把 GPU 纹理画到卡片里。
6. **换卡**：dwell 结束 → `deactivateExternal` → 下一张。`WorldPreview.keepSodiumBound`
   保证 Sodium 的 `RenderSectionManager`（及其已编译几何）不会在换卡时被销毁重建。

## 二、26.3 上踩到的四个坑

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| ① | 整帧只有清屏色，Sodium 的 `drawChunkLayer` 照常发出 | 墙界面打开时游戏处于"暂停"，`Camera.tick()` 不跑 → `fovModifier=0` → FOV=0 → 投影矩阵 `m00/m11 = Infinity`，所有顶点塌缩 | `WorldPreview.swapIn` 里显式调用 `mainCamera().tick()` |
| ② | `sodium=ready=0`，没有地形几何 | 区块包在预览世界还不是"当前渲染世界"时注入，Sodium `ChunkTracker` 收不到 | 区块包延迟到 swap-in 后由 `ExternalPreview.activateSections()` 注入，并补 `markLightReady` / `rebuildColumn` / `pumpChunkEvents` |
| ③ | 雾色 `(0,0,0)`，地形被 100% 雾染黑 | 预览 `ClientLevel` 不 tick，环境属性可能退化为 0 | `WorldPreview.applyAtmosphereFallback`：只在退化时用天空色/合理雾距回退（正常时用真实雾色） |
| ④ | 卡片整块纯色（像采样了一个纹素），或图像上下颠倒 | 26.3 的 `blit(GpuTextureView, GpuSampler, ...)` 参数是 **(x0,y0,x1,y1,u0,u1,v0,v1)**：角坐标 + u/v 成对；按旧版 `(x,y,w,h,u0,v0,u1,v1)` 传会得到 1 纹素。另外渲染目标的 v 轴与 GUI 纹理相反，需要 `v0=1,v1=0` 才是正立 | 见 `WallScreen` 的 blit 调用 |

另外：

- 合成帧必须显式 `GameRenderer.update(delta)`（`Camera.update`）与 `extract(delta, true)`；
  墙界面里 `advanceGameTime` 可能为 false，`GameRendererMixin` 用 `@ModifyVariable` 强制为 true。
- 预览相机可能落在"重生点在地下"的位置，`activateSections` 会在区块到位后把相机抬到
  `Heightmap.WORLD_SURFACE`，避免卡片显示方块内部或水下。
- Sodium 的绘制在正常主目标上完成；离屏 `TextureTarget` 路线（`-Dseedqueue.offscreenPreview=true`）
  仍保留，但正规路线是"主目标渲染 + GUI 边界复制"。

## 三、验证

冒烟测试（3 个队列世界）：

```powershell
cd 26.3_speedrun_mods_ports
powershell -File .\build.ps1 -Mod worldpreview; powershell -File .\build.ps1 -Mod seedqueue
powershell -File .\tools\run-smoke.ps1 -Seconds 70 -ModsSource .\dist -ExtraJvm ^
  "-Dseedqueue.test=true;-Dseedqueue.wall=true;-Dseedqueue.wallAutoSmoke=true;-Dseedqueue.shotPreview=false;-Dseedqueue.freeze=false;-Dseedqueue.testDiscardTicks=3000"
```

证据：

- 日志：`[preview] handed the preview level to Sodium`、`[preview] activated 25 preview sections`、
  `sodium draw #0 group=OPAQUE`、`[seedqueue] captured wall frame for #0`；
- 截图：`docs/seedqueue-wall-3d.png`（854x480 窗口，两张卡片）、
  `docs/seedqueue-wall-3d-wide.png`（2560x1494 窗口，三张卡片，各自显示不同种子的真实地形）。

## 四、冻结与零加载交接（worldpreview 结合）

墙打开后队列世界继续在后台加载；**加载完成（READY）后实例被冻结**，玩家点卡片直接进入、不经过原版加载画面。

1. **冻结（park）**：`QueueEntry.startGenerating` 完成预生成后调用 `park()`，设置 `parked/frozen`。
   `IntegratedServerParkMixin` 在每个 `IntegratedServer.tickServer` 开头调用 `entry.awaitUnpark()`，
   于是后台服务端线程**阻塞休眠**：世界、区块、实体、服务端对象全部留在内存里，但不再消耗 CPU。
2. **选卡（select）**：点击卡片 → `QueueEntry.play` → `select()` 唤醒服务端线程，并调用
   `Minecraft.doWorldLoad(entry.access, entry.packs, entry.stem, Optional.empty(), true)`。
3. **接管服务端**：`MinecraftDoWorldLoadMixin` 用 MixinExtras `@WrapOperation` 拦截
   `Minecraft.doWorldLoad` 里的 `MinecraftServer.spin(factory)`，把队列里**已在运行**的
   `IntegratedServer` 直接交给客户端（`SeedQueue.joining`）。`doWorldLoad` 随后发现
   `isReady()` 已经为 true，跳过等待，只创建本地内存连接，于是**没有磁盘加载、没有出生点区块准备**。
4. **无加载画面**：点击时如果当前预览的就是这张卡，`playEntry` 保留预览世界并丢掉
   `LevelLoadingScreen`；`handoffTick` 在真实 `ClientLevel` 到达前一直显示预览，
   到达后调用 `WorldPreview.deactivateExternal()` 交还渲染器——画面像素一致，切换不可见。
   （若点的是非当前预览的卡，则回退到原版加载屏幕，通常约 0.5–1 秒。）
5. **所有权**：交接成功后条目的 `access/stem/server/packs` 置空并标记 `transferred`，
   `discard()` 不会再关闭它们；世界文件与存档锁由客户端的集成服务端接管，退出世界时正常保存。
6. **重置复用同一路径**：Atum 的 `consumeForReset` → `playEntry`，所以 F6 重置也会直接进入预生成世界。
7. **SpeedRunIGT 计时器重置**：交接时 `TimerCompat.start(entry)` 调用
   `InGameTimer.start(entry.folder, RANDOM_SEED)`（遵循分类的 autoStart 开关），并设置
   `IS_CHANGING_DIMENSION / CAN_DISCONNECT / IS_SET_SEED`。否则第二个队列世界会继承上一个世界的
   RTA/IGT（交接不经过 IGT 自己的 join 钩子）。日志：`SpeedRunIGT timer started for #1: IGT=0 RTA=0`。
8. **冻结可观测**：每个 READY 条目都会打印 `entry #N server thread parked (frozen=true)`，
   仅被选中的条目打印 `server thread resumed`；其余条目在玩家进入世界后仍然保持 parked。

已验证日志（`docs/seedqueue-instant-join.png`：点击后直接进入世界，HUD/RTA 已开始计时）：

```
[seedqueue] entry #0 frozen (server parked, world kept loaded)
[seedqueue] joining queued entry #0 (seed ...)
[seedqueue] adopting live integrated server for entry #0
[seedqueue] entry #0 handed to the client (no world load)
Dev joined the game
[seedqueue] hand-off complete after 16 ticks (preview released)
[seedqueue] SpeedRunIGT timer started for #0: IGT=0 RTA=0
```

## 五、预览氛围与窗口缩放

### 黑色迷雾 / 黑色地面

预览用的 `ClientLevel` 从不 tick，环境属性（时间轴）驱动的雾色/天空色可能退化成全 0。
之前 `applyAtmosphereFallback` 放在 `LevelRenderer.render` 的 HEAD，此时
`GameRenderer.renderLevel` **已经**用退化的雾数据构建并上传了雾缓冲，改 `fogData` 已经太晚，
于是整帧被黑色雾覆盖（卡片一片黑）。

修复：把回退提前到 `GameRenderer.extract` 的 RETURN（`worldpreview$afterExtract`），
在渲染层真正读取雾数据之前修正：

- 触发条件：雾色 `r+g+b < 0.01` **或** `renderDistanceEnd <= 1`（全雾化）；
- 雾：颜色 `(0.72, 0.84, 1.0)`，距离 `far = max(64, renderDistance*16)`；
- 天空：`skyColor` 为 0 时补 `(0.47, 0.65, 1.0)`；
- 真实水平（已 tick 的世界）的雾数据不会被覆盖。
- 预览帧还会强制 `lightmapRenderState.skyFactor = 1`、`brightness = 1`、白天色 `skyLightColor`
  （`-Dworldpreview.keepNightLight=true` 关闭）；主世界预览的雾色若偏暗（亮度 < 0.25）也会替换成白天色
  （`-Dworldpreview.keepWorldFog=true` 关闭），下界/末地保留自身氛围。
- 生成阶段改为"先生成全部区块 → `LightEngine.runLightUpdates()` 刷完光照 → 再序列化
  `ClientboundLevelChunkWithLightPacket`"，避免快照里带进未点亮区块；
- 预览相机按**自己所在列**（出生点往南 3 格）的高度放置，并向上找空气，避免埋进山体造成
  "亮天空 + 黑地面"的观感。
- **卡片视角上下滑动（0.9.9 修复）**：`Camera#tick` 会用 0.5 的系数把 `eyeHeight` 平滑到实体眼高，
  预览相机在多个 level 间复用时 `eyeHeight` 还是上一个 level 的值，于是每张卡开始渲染后的十几帧里
  镜头从低处缓缓"升"到站立眼高（实测 camera y 从 96.58 渐变到 97.62，而 player y 恒为 96）。
  现在 `swapIn` 里通过新增的 `CameraAccessor` 直接把 `eyeHeight`/`eyeHeightOld` 设为预览实体的眼高，
  从第一帧起 camera = player + 1.62，不再滑动（`-Dworldpreview.cameraLog=true` 可验证）。
- **卡片加载完成后画面位移（0.9.7 修复）**：`WorldPreview.activateExternal` 原来每次都调用
  `updateSpawn(...)`，把玩家重置到**原始出生点**；外部预览在 `activateSections` 里已经把相机抬到
  地表，于是每轮切回这张卡时相机从地表跳回出生点（加载完成后的"位移"）。现在 `activateExternal`
  只同步 `spawnPos` 字段、不再移动玩家/相机，实测同一张卡多次激活的相机坐标完全一致
  （`-Dseedqueue.cameraLog=true` 可打印）。

还有一个独立的"黑地面"来源：预览 `ClientLevel` 从不 tick，天空变暗系数会解算成夜晚，
地形用黑色光照贴图渲染（整张卡片像蒙了一层黑雾，只有天空有颜色）。修复：预览帧在
`applyAtmosphereFallback` 里把 `lightmapRenderState.skyFactor` 抬到 1、`skyLightColor` 设为白天色、
`ambientColor` 补一个下限，并置 `needsUpdate`；`-Dworldpreview.keepNightLight=true` 可关闭。

验证：`-Dworldpreview.simulateBlackFog=true` 强制把雾改成黑色+0 距离，卡片仍然明亮
（回退生效）；正常运行时日志打印 `fog=(0.700 0.788 0.932) fogEnd=512 sky=(0.440 0.603 0.926)`。

### 窗口缩放

`GameRendererResizeMixin` 注入 `GameRenderer.resize(int,int)` 的 TAIL，调用
`SeedQueue.onWindowResize(width, height)`：把每个队列条目的 `displayTarget`/`renderTarget`
（以及调试用的 scratch 目标）resize 到新窗口大小。日志：`resized preview targets to 1280x720`。

卡片采样改为**按 16:9 居中裁剪**：源纹理宽高比与卡片不一致时只裁掉多余部分
（`u0/u1` 或 `v0/v1` 收缩），不拉伸画面，所以全屏/改变窗口后预览仍然等比。

## 六、轮播渲染与性能（为什么不能所有卡片同时实时）

Sodium 0.9 的 `SodiumWorldRenderer` 是**单例**，内部只有一个 `RenderSectionManager`：

- `setLevel(level)` 会先 `unloadLevel()` → `deleteRendererState()` → `RenderSectionManager.destroy()`，
  销毁已编译的区块几何并 `ChunkBuilder.shutdown()` 掉编译线程池；
- 因此每次换卡都要重建管理器、重启线程池、重新编译该世界的区块——这就是必须"驻留"一段时间
  （dwell）的原因，也是轮播慢的根源。

### Sodium 多管理器补丁（0.9.6）

第一次尝试"按 level 缓存 `RenderSectionManager`"时直接崩溃：Sodium 的 `reload()`
（`LevelRenderer.invalidateCompiledGeometry` 每次切世界都会触发）会 `initRenderer()` →
`deleteRendererState()`，把缓存里的管理器 `destroy()` 掉，再次 `ChunkBuilder.shutdown()` 就抛
`IllegalStateException: Worker threads are not running`。

现在的补丁（都在 SeedQueue 内，通过 `SeedQueueMixinPlugin` 只在装了 Sodium 时应用）：

1. `SodiumLevelCache`：按 `ClientLevel` 缓存 `RenderSectionManager`（最多 6 个），
   丢弃预览世界时排队到渲染线程统一 `destroy()`；
2. `SodiumWorldRendererMixin`：
   - `setLevel` HEAD：先把当前管理器存进缓存并从字段摘下（阻止 `deleteRendererState` 销毁），
     目标 level 有缓存就直接换 `level`/`renderSectionManager` 两个字段并取消原方法；
   - `setLevel` RETURN：新建的管理器写入缓存；
   - `reload` HEAD：**预览 level 直接取消**（切世界触发的 allChanged 不再摧毁缓存），
     真实 level 则移除缓存条目让 Sodium 正常重建；
3. `RenderSectionManagerMixin`：`destroy()` 幂等（第二次直接取消）；
4. `ChunkBuilderMixin`：`shutdown()` 幂等；预览管理器创建时把线程数限制为 **3**
   （通过 ThreadLocal 只在 `setLevel` 创建预览管理器期间生效，真实世界仍是正常线程数）。

结果（实测）：

- 3 个预览世界各缓存一个管理器、每个 3 条编译线程；切换时日志
  `sodium: reused cached section manager`，**不再出现** `Stopping worker threads`/`Started 10 worker threads`；
- 墙在 250ms dwell 下 **59 fps**、16ms 逐帧轮播下 51 fps（3 卡各 ~17 次/秒刷新）；
- 默认 dwell 从 800ms 降到 **120ms**（3 张卡每张约 2.8 次/秒刷新），
  `-Dseedqueue.previewDwellMs` 可调（最小 10）；`-Dseedqueue.sodiumLog=true` 打印缓存命中日志。

### 逐帧轮播（最终方案）

切换逻辑从 client tick（最多 20 次/秒）移到了**每一帧**：`WorldPreview.frameTickHook` 在
`Minecraft.renderFrame` HEAD、`WorldPreview.swapIn` 之前调用 `SeedQueue.frameSwapRenderTick`，
因此可以做到"每帧换一张卡"。

- 默认 dwell 30ms（≈每 2 帧换卡，3 张卡各约 10 次/秒刷新），`-Dseedqueue.previewDwellMs` 可调；
- 实测墙 **49 fps**，`sodiumCache=3`，多张卡片同时呈现实时画面（截图 docs/seedqueue-wall-3d-wide.png）；
- 每帧只渲染一个世界（主目标），其它卡片保留上一帧的 `displayTarget`。

### 为什么不做"每帧渲染 N 个世界"

试过 `WorldPreview.renderInstance`（离屏渲染到各自 target）逐卡渲染，但 26.3 的 render-pearl
后端**每帧只允许一个 submit**：

```
java.lang.IllegalStateException: Cannot wait on a fence for the current submit
  at MappableRingBuffer.currentBuffer
  at CloudRenderer.prepare
  at LevelRenderer.render
  at WorldPreview.renderInstance
```

第一张卡渲染后，第二张卡再开合成帧就会等待"当前 submit"的 fence。把调用放到
`renderFrame` HEAD（而不是 GUI 阶段）也一样。因此真正同时多视图在当前渲染后端下不可行，
"逐帧轮播 + 每卡独立 target 缓存"达到同样的视觉效果。

| 方案 | 状态 | 说明 |
| --- | --- | --- |
| 缓存管理器 + 快速切换 | ✅ 已实现 | 切换只剩两次字段写 + `setupTerrain`，无重编译 |
| 逐帧轮播（dwell 30ms） | ✅ 默认启用 | 49 fps，每卡约 10 次/秒刷新 |
| 每帧渲染 N 个世界（离屏） | ❌ 不可行 | render-pearl 每帧只允许一个 submit |

配置：`seedqueue.previewDwellMs`（默认 30，最小 10）、`seedqueue.previewGapMs`（默认 50）。

## 七、SpeedrunAPI 配置界面（对齐 1.16.1/1.21.4）

SeedQueue 不再使用自定义的简易设置页，而是注册真正的 SpeedrunAPI 配置
（`fabric.mod.json` 的 `custom.speedrunapi.config` → `SeedQueueSpeedrunConfig`）：

- 配置页由 SpeedrunAPI 生成：标题为"设置"（`speedrunapi.gui.config.settings`，不再显示
  "Speedrun API – Mod Configs"）、分类标题（Queue/Wall/Performance/…）、数值滑条、开关、
  分页（`< >`，顶部显示 `1 / N`）、底部 **Wall Keybindings**、**Done** 按钮
  （截图 `docs/seedqueue-config-screen.png`）；
- **分类/选项名的兜底翻译**：部分配置（如 StandardSettings）的 `@Config.Category` 直接使用原版
  语言键（`options.mouse_settings`、`options.video`、`options.online`），以前黄色分类标题会原样
  显示键名。现在 `SpeedrunConfigScreen` 的解析顺序是：
  `speedrunapi.config.<mod>.category.<category>` → `<category>`（本身就是语言键）→
  `options.<category>` → 驼峰/下划线美化；选项名同理（`...option.<id>` → `<id>` →
  `options.<id>` → 美化），因此任何配置都不会再显示原始键名（截图
  `docs/seedqueue-config-categories.png`，中文下显示"鼠标设置…/在线选项…/视频设置…"）。
- 选项字段与 1.16.1/1.21.4 对齐：`maxCapacity`、`maxConcurrently`、`maxConcurrently_onWall`、
  `maxWorldGenerationPercentage`、`resumeOnFilledQueue`、`useWall`、`rows`、`columns`、
  `resetCooldown`、`waitForPreviewSetup`、`bypassWall`、`smartSwitch`、`soundVolume`、
  `wallFPS`、`previewFPS`、`freezeLockedPreviews`、`reduceLevelList`、`benchmarkResets`、
  线程优先级、调试选项等；运行时实际使用的项在加载/保存时同步进 `QueueConfig` 并写入
  `config/seedqueue.json`；
- **墙界面按键控制**（对齐 1.21.4 KeysScreen）：打开 `WallKeybindingsScreen`
  （截图 `docs/seedqueue-keybindings-screen.png`）。每行 = 动作名 + 主键和弦 + `+ n`（副/组合键）+
  `− n`（屏蔽键）；点击主键后按键即可绑定（按住 Shift/Ctrl/Alt 绑定时会记录为组合键，
  显示成 `Shift + M`），点击 `+ n`/`− n` 后按键可增删该动作的副快捷键/屏蔽键，
  Backspace 清空、Esc 取消；悬停 `+ n`/`− n` 会显示工具提示（"设置副快捷键后，同时按下主副快捷键
  才会触发功能。"）。墙触发逻辑改为：主键按下 + 所有副键按下 + 没有屏蔽键按下才触发
  （`InputConstants.isKeyDown`）。屏幕背景使用标题界面全景图（不再纯黑）。
  绑定持久化在 `config/seedqueue.json` 的 `keyBindings`（int 或 `{primary, secondary, blocking}`）；
- 中文/英文本地化：复制了 1.16.1 的 `assets/seedqueue/lang/*.json`，游戏语言为中文时选项名与分类名
  与 1.16.1 一致；
- SpeedrunAPI 的 Mod Configs 列表改为富条目：模组图标、`名称 版本`、`by 作者`、两行描述
  （截图 `docs/speedrunapi-config-list.png`），不再是一排纯文字按钮。

## 八、墙操作与配置（对齐 1.21.4）

1.21.4 的墙支持键盘操作、锁定、重置和可配置网格；移植版现在具备同样的核心行为：

| 操作 | 默认键 | 行为 |
| --- | --- | --- |
| play | `R` | 立即进入悬停的预生成世界（活服务端交接）|
| lock | `L` | 锁定/解锁悬停卡片；锁定的卡片不会被重置；锁定时播放音效 |
| reset | `E` | 丢弃悬停卡片并在后台补一张新世界 |
| reset all | `T` | 丢弃所有未锁定卡片并补充 |
| focus reset | `F` | 先进入悬停世界，再重置其余未锁定卡片 |
| schedule join | `G` | 锁定并"就绪即进"：该世界生成完成后自动加入 |
| schedule all | `H` | 对所有已锁定卡片设置"就绪即进" |
| reset column | `K` | 重置悬停卡片所在列 |
| reset row | `J` | 重置悬停卡片所在行 |
| start benchmark | `B` | 开始 benchmark（反复丢弃就绪世界并补位，统计重置吞吐）|
| cancel benchmark | `N` | 停止 benchmark |
| quit wall | `Esc` | 关闭墙（不停止后台队列）|

**存档可见性（0.9.10）**：`LevelListMixin` 现在只过滤**当前队列中**的世界
（`SeedQueue.activeWorldFolders()`），不再按 `SeedQueue-*` 前缀一刀切；玩过并从墙进入的世界会从队列移除，
因此会出现在"单人游戏"列表里。同时 `QueueEntry.play` 会在世界目录写入
`seedqueue_played.marker`，启动时的 `cleanupStaleSaves` 跳过带该标记的世界，避免以后被当成残留存档删掉。
实测：进入世界后 `level list (1): [SeedQueue-...] containsPlayed=true`。

**墙界面（0.9.10）**：卡片标签只显示状态（`ready`/`generating`），移除了实例编号 `#N` 与种子；
`SeedQueue: n / capacity` 状态行只在墙页面（`WallScreen`）显示，不再出现在游戏内 HUD/其他界面
（需 F3 打开调试层时才绘制）。

**锁定区（0.9.8，对齐 1.16.1/1.21.4 的 locked 分组）**：锁定的实例会移到墙底部的
`locked (n)` 横条（小卡片），主网格空出的槽位立即由 `refillQueue` 补一张新世界；
解锁后卡片回到主网格。锁定项不占主队列容量（`refillQueue`/空闲补队只统计未锁定项），
所以可以同时锁住任意多张卡。截图 `docs/seedqueue-locked-strip.png`。

> 修复：`setLevel(null)` 时 Sodium 的 `unloadLevel` 会删掉 `uniformBufferManager` 但不会重新创建，
> 之后渲染主世界会 NPE（`SodiumWorldRenderer.renderLayer`）。现在 `SodiumWorldRendererMixin`
> 对 `target == null` 自行处理：缓存并摘下管理器、保留 `uniformBufferManager`，不再调用 vanilla 的
> `unloadLevel`。

鼠标：左键 play、右键 lock、中键删除并补充一张。按键绑定持久化在 `config/seedqueue.json`
的 `keyBindings` 字段（键名 `play/lock/reset/reset_all/focus_reset/quit`）。

> 26.3 的鼠标按键来自 SDL，编号是 **1=左键、2=中键、3=右键**（不是 GLFW 的 0/1/2）。
> `WallScreen.mouseClicked` 早期按 0/1/2 判断，导致"左键锁卡、右键无反应"；
> 现在统一用 `BUTTON_LEFT/MIDDLE/RIGHT` 三个常量，侧键（SDL 4/5）直接忽略。
> 键位设置界面录鼠标键时也用 `event.button() - 1` 转成 GLFW 索引后再存。

**重置不会卡住渲染线程**：`discard()` 会 `halt` 服务端并等待其线程，如果服务端还在生成区块，
在渲染线程直接调用会冻结整个墙（实测会卡死）。现在重置/删除都把 `discard` 丢到
`QueueExecutors.prepare()` 后台执行，并立即 `refillQueue()` 补位——与 1.21.4 的
`refillAfterReset` 一致。重置冷却 `resetCooldownMs`（默认 400ms）防止连点。

**配置项**（`config/seedqueue.json`，设置界面可改）：`queueSize`、`rows`、`columns`
（墙网格，`queueSize` 会被限制在 `rows*columns` 内）、`resetCooldownMs`、`wallFps`、
`previewFps`、`chunkRadius`、`useWall`、`autoQueue`、`showHud`、`perspective`、
`deleteDiscardedWorlds`、`keyBindings`。

**已补齐（对齐 1.21.4）**：

- `scheduleJoin/scheduleAll`：`QueueEntry.scheduledJoin`，墙 tick 里发现就绪的 scheduled 条目自动
  `playEntry`（实测 G 后 "scheduled join for #0" → 直接进入）；
- benchmark：`B` 开始、`N` 取消，反复丢弃就绪未锁定世界并 `refillQueue`，达到
  `benchmarkResets`（可用 `-Dseedqueue.benchmarkResets` 覆盖）后打印
  `SEEDQUEUE_BENCHMARK n resets in X ms`；
- 按行/列重置：`K`/`J`，按墙网格（`rows/columns`）计算同一行/列的卡片批量重置；
- `smartSwitch`：要进入的世界还没就绪时，自动改进入另一个已就绪的锁定世界；
- `bypassWall`：打开墙时若有已就绪的锁定世界，直接进入而不显示墙（测试可用
  `-Dseedqueue.bypassWall=true`）；
- 音效：复制了 1.21.4 的 `lock_instance.ogg`/`reset_instance.ogg` + `sounds.json`，
  锁定/重置时按 `soundVolume`（0-100）播放。

**0.9.5 又补齐**：

- `wallFPS`：`WallFpsMixin` 注入 `FramerateLimitTracker#getFramerateLimit`，墙打开时帧率上限为
  `wallFps`（默认 60），与 1.21.4 一致；
- `reduceLevelList`：`LevelListMixin` 过滤 `findLevelCandidates` 里的 `SeedQueue-*` 临时世界，
  世界列表不再被队列世界刷屏；
- F3 调试行：`DebugOverlayMixin` 在调试界面底部显示 `SeedQueue: n / capacity`
  （benchmark 时附带计数）；26.3 的 Atum 端口没有 1.21.4 的 DebugHudMixin，因此
  "Resetting a random seed" 那一行无法对接；
- `waitForPreviewSetup`：`playEntry` 在交接前调用 `ensurePreview`，先把目标世界的预览建好并激活，
  这样即使点击的不是当前预览卡也能无缝进入（实测 0.9.5 交接仍 < 1s）；
- `freezeLockedPreviews`：墙轮播跳过已锁定的卡片（保留最后一帧），只有全部锁定才回退渲染；
- 线程优先级：`backgroundExecutorThreadPriority`（prepare 线程）与 `serverThreadPriority`
  （队列服务端线程）按配置设置。

**仍未移植**：`wallDataLimit` 与 `wallChunkDistance` 的墙内限距（我们的预览用的是固定
`chunkRadius` 快照，扩大快照会显著增加生成耗时）、`idlePreviewFPS`/`previewFPS` 的逐卡限帧
（当前用 dwell 控制刷新频率）、`checkMinMemory` 内存提示、模拟窗口尺寸
（`simulatedWidth/Height`）、`reduceLevelList` 之外的队列世界管理。

## 九、开关

| 属性 | 默认 | 说明 |
| --- | --- | --- |
| `seedqueue.wallPreview` | true | 启用墙 3D 预览 |
| `seedqueue.previewDwellMs` | 2000 | 每张卡驻留时长 |
| `seedqueue.previewGapMs` | 150 | 换卡间隔 |
| `seedqueue.wallDump` | false | 把墙帧与卡片纹理写成 PNG（调试） |
| `seedqueue.renderLog` | false | 打印每次成功截帧 |
| `seedqueue.cardReadback` | false | 改用 CPU 读回 + DynamicTexture（对照用） |
| `seedqueue.offscreenPreview` | false | 旧的离屏 TextureTarget 路线 |

## 十、性能优化（0.9.14）

这一轮针对墙轮播的每帧开销做了优化，稳态墙帧率实测 **59fps**（wallFPS 60 上限）：

- **整窗拷贝必须每帧做（0.9.16 修正）**：曾用 `readyChunkCount` 跳过"无变化"的拷贝，但区块数
  在 Sodium 上传完区块后就不再变化，而地形网格还在异步编译——卡片会永远停在只有天空的旧帧。
  现在恢复为每帧 `copyColorFrom`，地形编译完成后卡片就会显示出来。
- **`prepareExternal` 每张卡只做一次**：它内部的 `levelExtractor.allChanged()` 会丢弃全部已编译
  区块；以前每次轮播都调用，等于每 80ms 重建一次地形，是墙掉帧的主因。现在用
  `QueueEntry.sectionsPrepared` 标记，只在预览 level 首次激活时执行。
- **区块快照只应用一次**：`ExternalPreview.activateSections` 首次应用全部 packet + 重建列，
  后续轮播只 `pumpChunkEvents()`，不再每帧重发 9–25 个区块包、重建 24 个 section。
- **缓存 Sodium 反射句柄**：`setupTerrain` 以前每帧遍历 `getMethods()` 找方法并反复
  `getMethod`；现在 `setupTerrain/scheduleRebuildForChunk/markLightReady/refreshTerrain` 和
  `sectionUpdateTracker` 字段都只解析一次。`beforeExternalRender` 也不再每帧
  `scheduleTerrainUpdate`。
- **可见区块扫描降本**：`populateVisibleSections` 的半径从 8 降到 4，并用 `MutableBlockPos`
  复用坐标对象（原来每帧约 1445 次 `new BlockPos`）；每帧区块查询从 1445 降到 405。
- **零散分配**：墙的 unlocked/locked 列表、轮播 ready 列表改为复用；卡片状态标签改为
  `switch` 常量（不再 `name().toLowerCase()`）；`locked (n)` 标题按数量缓存；
  `System.getProperty/Integer.getInteger` 热路径改为静态字段；`thirdPerson` 只在值变化时
  `System.setProperty`。
- 新增 `-Dseedqueue.fpsLog=true` 用于打印真实墙帧率（每 2 秒），
  `-Dseedqueue.wallOpenDelayMs=<ms>` 用于延迟开墙（基准测试时避开世界生成）。

## 十一、零加载进入世界（0.9.15）

从墙点进实例时不再出现标题界面/加载界面：

- `playEntry` 置 `suppressLoadingScreens`，`Gui#setScreen` 混入在此时拦截
  `LevelLoadingScreen`、`ProgressScreen`、`TitleScreen`（`doWorldLoad` 先
  `disconnectWithProgressScreen` 再 `LevelLoadingScreen`，两条路径都被拦掉）；
- 墙不再在点击瞬间关闭：一直保留（显示最后几帧卡片）直到 `Minecraft.setLevel` 装上新的
  client level **且** `minecraft.player` 已创建，才关闭墙进入游戏；中途 `Minecraft.tick` 跳过
  `MultiPlayerGameMode#tick`（此时 player 还是 null，原版靠 ReceivingLevelScreen 暂停 tick 规避）；
- 交接完成时调用 `WorldPreview.rebindRealLevel`：把 Sodium / levelExtractor 强制绑回真实 level
  （`keepSodiumBound=false`、`realLevelBound=false`、`SodiumCompat.restore` + 地形刷新），
  避免渲染仍绑定在预览 level 上；
- 新增 `SodiumRenderLayerMixin` + `UniformBufferAccessor`：当 `level == null` / uniform manager
  为空 / 本帧尚未 `update` 时跳过该层地形绘制，修掉过渡帧（交接、退出世界）可能触发的
  "Global terrain uniforms have not been updated" 崩溃；
- 20 秒超时兜底：交接未完成时恢复屏幕并关闭墙，避免卡在墙上。

实测日志（`-Dseedqueue.screenLog=true`）：

```
[seedqueue] joining queued entry #0
[seedqueue] suppressed screen ProgressScreen
[seedqueue] suppressed screen LevelLoadingScreen
[seedqueue] adopting live integrated server for entry #0
[seedqueue] client level installed, waiting for the player entity
[preview] rebind to real level requested (level=minecraft:overworld)
```

随后直接进入世界（截图 `docs/seedqueue-seamless-join.png`），没有标题/加载画面。

## 十二、队列生成控制（0.9.17，P0 补齐）

按 1.21.4 的 `waitForGenerationPermit` / 每 tick permit 调度移植：

- **Max Generating Seeds / (Wall)**：`QueueConfig.concurrency(wall)`（`maxConcurrently` 为 0 时等于队列容量，
  墙界面用 `maxConcurrently_onWall`）。每 tick `applyGenerationPermits` 发 permit，
  `QueueEntry.waitForGenerationPermit` 在区块之间等待；`canStartMoreEntries` 用 `startQueued` 占位限制
  PREPARED 世界的启动。实测 `maxConcurrently=1` 时严格按 #0 → #1 → #2 顺序生成。
- **Max World Generation %**：生成到该百分比即停止，用已生成的区块完成快照并置为 READY（卡片可预览/
  锁定/进入，剩余区块进入后按需生成）。实测 40% 时为 `ready (10 chunks)`（总 25）；锁定/计划加入/
  进入会 forceFinish 继续到 100%。
- **Resume On Filled Queue**：队列满时未完成的世界忽略百分比限制继续生成到 100%。实测 40% 限制 +
  开启后为 `ready (25 chunks)`。
- **Preview FPS**：每张卡按 `previewFps` 节流（每卡两次渲染间隔 ≥ 1000/previewFps，dwell =
  1000/(previewFps × 卡片数)），区块构建不受影响；`previewFps=20`、3 张卡时墙帧率约 55fps，
  每张卡约 20fps。
- **Play Next Lock**：新增 `PLAY_NEXT_LOCK`，默认 **Shift + M**，进入 `lockedAt` 最早的 READY 锁定实例；
  锁定动作记录 `lockedAt`。实测日志 `play next lock: entry #0` → `joining queued entry #0`。

注意：`config/seedqueue.json` 与 SpeedrunAPI 的 `config/mcsr/seedqueue.json` 都保存这些选项；
运行时以 SpeedrunAPI 配置（游戏内“设置”页）为准，直接改文件测试时两个都要改。

## 十三、P1 补齐（0.9.18 / SpeedrunAPI 2.1-beta.5）

- **Background Previews（preparing 分组）**：未 READY 的世界不再占主网格槽位，而是显示在底部
  `preparing (n)` 缩略图条里（显示状态与 `chunksGenerated/chunksTotal`）；主网格只放 READY 卡片。
  显示数量由 `preparingPreviews` 控制（-1 = 自动 rows×columns，0 = 关闭）。截图
  `docs/seedqueue-wall-preparing-debug.png`。
- **Show Advanced Settings 过滤**：`SeedQueueSpeedrunConfig.shouldShowOption/shouldShowCategory`
  配合 `SpeedrunConfigScreen` 的过滤，关闭时隐藏 threading/debug 及高级选项（配置页 8 → 6 页），
  但 `showAdvancedSettings` 开关本身始终可见；打开后恢复 8 页。截图
  `docs/seedqueue-config-advanced-off.png` / `-on.png`。
- **Debug Menu**：墙上右上角绘制 FPS 曲线、`cards`、堆内存与 Sodium 缓存数量；`F3` 打印悬停实例的
  状态/种子/区块/预览信息，`Shift+F3` 打印对应 `SeedQueue server N` 线程堆栈（`showDebugMenu`）。
- **Watchdog**：`useWatchdog` 时启动 `SeedQueue Watchdog` 守护线程，每 10 秒打印 Render thread
  堆栈与所有 SeedQueue 服务器线程状态（实测日志正常输出）。
- **线程数/优先级全部接线**：
  `backgroundExecutorThreads` / `wallExecutorThreads` 控制两个场景的 prepare 线程池大小
  （0 = 自动），`backgroundExecutorThreadPriority` / `wallExecutorThreadPriority` 为池线程优先级；
  `seedQueueThreadPriority` 设置 `SeedQueue server N` 服务器线程创建优先级；
  `chunkUpdateThreads` 传给 `QueueConfig.chunkWorkers()` 并作为预览 Sodium chunk builder 的线程上限，
  `chunkUpdateThreadPriority` 在 `ChunkBuilder` 构造后设置其工作线程优先级。

## 十四、P2 资源包生态（0.9.19）

墙的布局与装饰改为资源包驱动：

- **custom_layout.json**（`assets/seedqueue/wall/custom_layout.json`）：
  `main` / `preparing`（数组）/ `locked` 分组，网格（`rows`/`columns`/`x`/`y`/`width`/`height`/
  `padding`）或 `positions` 坐标列表；数值 0..1 按屏幕比例、整数按像素；支持
  `instance_background`、`cosmetic`、`replaceLockedInstances`。解析在 `WallLayout`，
  无资源包时回退为 rows×columns 网格（自动 16:9 卡片高度）。
- **移除底部锁定条**：默认布局没有 locked 分组，锁定卡片留在主网格；只有资源包定义了
  `locked` 分组时锁定实例才移出主网格，且只有 `replaceLockedInstances: true` 才立即补位，
  否则等下一次 Reset All。截图 `docs/seedqueue-wall-layout-default.png`（默认，无锁定条）与
  `docs/seedqueue-wall-layout-custom.png`（main 2×2 + preparing + locked 右侧列）。
- **贴图**（`assets/seedqueue/textures/gui/wall/`）：`background.png`、`overlay.png`、
  `instance_background.png`、`lock.png` / `lock-1.png` …；锁贴图支持
  `lock.png.mcmeta` 的 `seedqueue.weight` / `defaultWeight` 权重随机；`.mcmeta` 有
  `animation` 时按竖向帧条 + `frametime` 播放（`WallTextures`）。**默认背景为纯黑**
  （0.9.20 起），只有资源包提供 `background.png` 时才绘制；空位保持深色，锁贴图默认使用 jar
  内置的 `lock.png`。
- **音效触发点**：`play_instance`、`lock_instance`、`reset_instance`、`reset_all`、
  `reset_column`、`reset_row`、`start_benchmark`、`finish_benchmark`、`open_wall` 都已接线
  （`sounds.json` 事件已声明；音频文件由资源包提供，仓库只自带 lock/reset 两个）。
- **Chunkmap overlay**：`showChunkMaps` 开启后，在游戏内 HUD 左上角绘制最新 READY 队列世界的
  32×20 区块地图（半透明，`HudMixin`）。截图 `docs/seedqueue-chunkmap-overlay.png`。

## 十五、1.21.4 额外选项接线（0.9.21）

- **Concurrent Server Starts（startConcurrently）**：`canStartMoreEntries` 统计 `startQueued/STARTING`
  数量，超过该值的世界先不启动，避免多个整合服务器同时抢 CPU。
- **Preload Worlds（prewarmWorlds）**：在标题界面只提前启动最多该数量的世界（0 = 只等墙打开再启动），
  打开墙后不再限制。
- **Wall Preview Distance（wallChunkDistance）**：所有预览构建改用 `previewRadius(entry)`，取
  `min(wallChunkDistance, chunkRadius + 2)`（至少 2），替代写死的 2。
- **Wall Data Limit（wallDataLimit）**：`ExternalPreview.activateSections` 每次最多应用该数量的区块包，
  分批完成快照，避免首帧卡顿（通过系统属性 `seedqueue.wallDataLimit` 传给 WorldPreview，避免反向依赖）。
- **Idle Preview FPS（idlePreviewFPS）**：`frameSwapTick` 里未悬停的 READY 卡片使用该帧率（0 = 用
  `previewFps`），悬停卡片保持正常帧率。
- **Join Transition（joinTransition）**：进入实例后墙整体向左滑出（320ms），关掉则不播放滑动。
- **Chunkmap Freezing（chunkMapFreezing）**：生成期间每 250ms 刷新一次区块地图，超过设定毫秒后停止刷新
  （叠加层保留最后快照）；`-1` = 不冻结。HUD 叠加层改为显示最新有地图的世界（包括生成中的）。
- **Check Allocated Memory（checkMinMemory）**：启动时若 `-Xmx` 小于 `1800 + 队列容量 × 180` MB，
  日志给出提高内存或减少队列的警告。

## 十六、资源包：锁定实例独立池（SeedQueue-LockedPool）

随包提供 `dist/SeedQueue-LockedPool.zip`（对应源码目录 `dist/SeedQueue-LockedPool/`），把锁定实例放回
底部的独立池子，行为等价于旧版内置锁定条：

- `assets/seedqueue/wall/custom_layout.json`：
  - `main`：1×3 网格（上方主区域）；
  - `locked`：1×4 网格（底部独立池，`instance_background: true`）；
  - `replaceLockedInstances: true`：锁定后立即把主区域空位补满（与旧内置行为一致）。
- `assets/seedqueue/textures/gui/wall/lock.png`：锁定图标（高度与预览对齐）。
- `pack.mcmeta`：26.3 资源包格式 97（`min_format: [97,0]` / `max_format: [97,1]`）。

安装：把 zip 放进实例的 `resourcepacks/`，在“选项 → 资源包”里启用即可；也可以直接放**文件夹**
`resourcepacks/SeedQueue-LockedPool/`。启用后墙的主区域仍是 3 张卡，锁定实例移到下方 4 格池子；
关闭资源包即恢复“锁定卡片留在主网格”的默认行为。截图
`docs/seedqueue-locked-pool-pack.png`。

## 十七、预览只渲染半边 / 黑边（0.9.26 / WorldPreview 6.3.1-beta.17）

**根因**：预览只生成 `chunkRadius`（旧默认 **2**）→ 5×5 区块（约 40 格）。相机站在出生点看向任意方向，
40 格处就是世界边缘：地形只占屏幕一部分，边缘之外是天空穹顶的**下半球**（暗色），于是表现为
"只渲染半边 + 一圈黑边"。

**修复**：

- `chunkRadius` 默认从 **2 改为 4**（9×9 = 81 区块，约 64 格），设置页 "Queue Settings" 里新增
  **Chunk Radius** 滑条（2–6，逐格滑动），修改后新建的队列世界立即生效；预览一次性生成的区域足以覆盖视野到
  地平线，卡片显示完整地形（实测 3 个实例 5–7 秒 READY）；`previewRadius` 取
  `min(wallChunkDistance, chunkRadius)`，不会再出现"视图半径大于已生成半径"的空区块。
- 预览帧把 `skyRenderState.shouldRenderDarkDisc` 设为 `false`（`SkyRendererMixin` 在渲染前再兜底一次）；
- 预览帧把 `skyColor` 调亮到 `(0.62, 0.76, 1.0)`，避免地平线下方的暗带；
- READY 之后仍会按 ring 继续生成额外地形（见下一节），进一步填满边缘。

截图 `docs/seedqueue-preview-no-black-band.png`（默认 `chunkRadius=4`，3 张卡均为完整地形）。

## 十八、预览出现后继续生成地形（0.9.24）

`QueueEntry` 在 READY（卡片已经显示预览）之后继续向后生成地形：

- 以 `chunkRadius` 为基础，逐个 ring 生成，默认最多再生成 2 圈（`-Dseedqueue.extendPreviewRadius`，
  上限 `(16-radius)/2`），每圈结束 `Thread.sleep(20)`，并持续检查 `selected`/`DISCARDED` 以便玩家
  选中时立即停下；
- 每个新生成区块构造 `ClientboundLevelChunkWithLightPacket` 放进 `pendingPackets`；
- 墙在渲染该卡片时把 `pendingPackets` 交给 `ExternalPreview.applyNewPackets`：扩大 view radius、
  应用区块包、标光、重建列并 `refreshTerrain`，卡片会随着地形生成逐步变完整；
- 生成完成后才 `park()`（未选中时），选中则跳过 park 直接交给整合服务器。

日志：`entry #N generated X extra chunks beyond preview radius`。截图
`docs/seedqueue-preview-no-black-band.png`（右侧卡片即包含额外生成的区块）。

## 十九、F6 打开墙 / 从已加入的世界返回墙（0.9.0-beta.29 合并 F7）

墙不再占用独立按键：**F7 已移除**，原来 F7 的功能并进 Atum 的重置键 **F6**。
`KeyboardHandlerMixin` 在 `keyPress` HEAD 统一处理 F6，分三种情况：

1. **在从墙加入的世界里**：`SeedQueue.returnToWallFromJoinedWorld` 接管，解除预览相机/假实体、
   清掉 `Atum.shouldReset`、`minecraft.disconnect(new TitleScreen(), false)`（不走 `PauseScreen`，
   那是之前崩溃的路径），然后立刻显示墙；日志 `[seedqueue] returning to the wall from a joined world`；
2. **墙已经开着**：按键交回 Atum，F6 仍然是"重置/开始跑"，不会被墙吞掉；
3. **其他情况**（标题界面、普通方式加载的世界）：调用 `SeedQueue.openWall` 打开墙，
   行为与原来的 F7 完全一致；日志 `[seedqueue] wall opened from key with N entries`。

冒烟：`-Dseedqueue.wallSmokeJoin=true -Dseedqueue.f6Smoke=true`（加入后 80 tick 模拟 F6 回墙），
`-Dseedqueue.wallKeySmoke=true`（游戏内模拟 F6 开墙）。


## 二十、随机崩溃修复（0.9.27）

崩溃报告里的三类 Sodium 生命周期异常：

1. `setupTerrain` → `NullPointerException: uniformBufferManager is null`；
2. `scheduleRebuildForChunk` → `NullPointerException: renderSectionManager is null`；
3. `ChunkBuilder.shutdown` → `IllegalStateException: Worker threads are not running`。

根因是 `SodiumLevelCache` 在**真实世界**和预览世界之间复用 manager：

- Sodium 卸载真实世界时会删除 `UniformBufferManager`，而缓存分支直接 `ci.cancel()` 跳过
  `loadLevel/initRenderer`，下次渲染就 NPE；
- 切换世界瞬间，旧的 `ClientLevel` 仍在发区块光照更新，而 `renderSectionManager` 已被置空；
- 被替换的真实 manager 显式 `destroy()` 后字段仍指向它，下一次切换会二次销毁。

修复（`SodiumWorldRendererMixin`）：

- 只缓存**预览**等级；真实等级交给 Sodium 正常的 `unloadLevel/loadLevel`，保持 uniform 状态一致；
- 复用缓存 manager 时若 `uniformBufferManager == null`，重新 `new UniformBufferManager(level, renderDistance)`；
- 替换真实 manager 时 `destroy()` 后立即把字段置空；
- 防御性 guard：`setupTerrain` / `renderLayer` / `scheduleRebuildForChunk` 在状态为空时直接返回。

验证：墙→加入世界→F6 回墙（60s）与 Atum 连续重置（90s）均无崩溃。

## 二十一、跑分模式（0.9.28）

- 墙界面按 **B**（Start Benchmark）开始，按 **N** 取消；`benchmarkResets` 可配置 **1–10000** 次；
- 开始后墙会不断丢弃 READY 的未锁定世界并补位，按实际吞吐计时；
- 墙上叠加面板显示：

  ```
  SeedQueue Benchmark
  N / total resets   elapsed   RPS
  [========------------------]
  ```

  第一张 READY 卡片出现前显示 "waiting for ready worlds..."（计时从第一次重置开始）；
- 完成后显示 "SeedQueue Benchmark finished: N resets in X.XXs (Y.YY RPS)"，保留 12 秒；
- 关闭墙（Esc）会取消跑分。

截图 `docs/seedqueue-benchmark-overlay.png`。

## 二十二、成本

- 每张卡在一次 dwell 内持续渲染整窗世界（仅墙打开时），换卡频率约 `1000/previewDwellMs` 次/秒；
- 卡片纹理为整窗大小（854x480）的 `TextureTarget`，复用不重建；
- Sodium 的几何在换到不同世界时会重建（每个队列世界一份），这是 26.3 单例渲染器的限制。

## 二十三、跑分时 JVM 原生崩溃（0.9.0-beta.29）

现象：墙上跑分（300 次）跑到一半整个 JVM 直接消失，`hs_err_pid*.log` 里是

```
#  EXCEPTION_ACCESS_VIOLATION (0xc0000005), writing address 0x0000000000000000
#  Problematic frame:
#  J 39424 c2 ...sodium...BlockRenderer.bufferQuad(...)V
Current thread: JavaThread "Chunk Render Task Executor #7"
```

即崩溃发生在 Sodium 的 **chunk 构建工作线程** 里，而且不是 Java 异常能接住的东西。顺着崩溃 PC
反汇编看，出事指令在往 `[r8]` 写顶点数据而 `r8 == 0`；调用链是
`BlockRenderer.bufferQuad → ChunkMeshBufferBuilder.push → CompactChunkVertex 编码器`，
`push` 里唯一可能给出 0 地址的就是 `this.buffer.address()`，也就是 `MemoryUtil.nmemRealloc(...)`
返回了 **NULL**（原生分配失败）。Sodium 把这个 0 直接包成 `MemorySegment.ofAddress(0)`，
随后第一次写顶点就落到空页上。

环境佐证（同一份日志）：

- `-Xmx4096m`，崩溃时 `used 3905325K / 4096M`，一直在做 full GC；
- 墙在跑分期间 **每秒重建约 3–6 次 Sodium 渲染状态**（`Stopping worker threads` /
  `Started 10 worker threads` 成对出现 273 次 / 45s），每次都是一整套 worker 线程 +
  chunk 构建缓冲 + 区块图；
- 每个 chunk 构建任务里 Sodium 会给 3 个 render pass × 8 个朝向重新 `realloc` 约 24MB 顶点缓冲，
  10 个 worker 并发就是几百 MB/秒的原生分配抖动。

修复分三层：

1. **原生分配失败不再变成 AV**（`mixin/sodium/ChunkMeshBufferBuilderMixin`）：
   `@Redirect` `ChunkMeshBufferBuilder.reallocate` 里的 `MemoryUtil.nmemRealloc`，返回 0 时记一条
   `[seedqueue] sodium could not allocate N bytes of native vertex memory` 并抛
   `NativeAllocationException`。Sodium 的 `ChunkJobTyped#execute` 本来就 `catch (Throwable)` 并打印
   "Chunk build failed"，再配合 `ChunkJobResultMixin` / `RenderSectionManagerMixin` 把这个结果丢掉，
   最坏情况只是**这一个 section 没编译出来**，游戏继续跑。
2. **预览的 chunk builder 线程上限真正生效**（`SodiumWorldRendererMixin`）：
   `chunkUpdateThreads` → `QueueConfig.chunkWorkers()` 的 `setThreadLimit` 原本写在
   `if (!CACHE) return;` 之后，而 `sodiumCache` 默认关闭，等于从未执行——每次换卡都是满配
   10 线程（24 核的最优值）。现在提前到 `setLevel` 开头，永远生效。
3. **预览的区块图按预览半径收缩**（`RenderSectionManagerMixin` + `QueueConfig.previewRenderDistance()`）：
   Sodium 的 `RenderSectionManager` 按「玩家渲染距离」建图；玩家 32 区块时 `C: x/101400`
   （= 65×65×24 个 `RenderSection`），而预览只有 spawn 附近几个区块的数据。现在预览 manager 的
   `renderDistance` 压到 `chunkRadius + 4`（默认 8，日志里 `D: 8`），并且只保留 spawn 周围
   `±previewChunkRadius()` 列的 section。

验证（`-Dseedqueue.benchmarkSmoke=true`）：

```
[seedqueue] benchmark started (120 resets)
[seedqueue] SEEDQUEUE_BENCHMARK 120 resets in 130102 ms      # 约 0.92 RPS
```

跑完后墙仍在 44–58 fps 正常轮播，日志里没有 `Chunk build failed`，也没有新的 `hs_err_pid*.log`。


失败路径可以直接复现验证：`-Dseedqueue.simulateNativeOom=300` 会让第 300 次原生顶点分配失败一次，
日志里会看到

```
[Chunk Render Task Executor #8/ERROR]: [seedqueue] sodium could not allocate 2621440 bytes of native vertex memory; failing the chunk build instead of writing to address 0
[Chunk Render Task Executor #8/ERROR]: Chunk build failed
me.contaria.seedqueue.NativeAllocationException: Sodium native vertex allocation failed (out of native memory)
```

游戏照常跑完跑分（`SEEDQUEUE_BENCHMARK 30 resets in 31903 ms`）。实现上除了 `reallocate` 的守卫，还需要
`ChunkJobResultMixin`（`unwrap()` 对这类异常返回 `null`）和 `RenderSectionManagerMixin`（收集结果时跳过
`null`），否则 Sodium 会在渲染线程 `unwrap()` 时把这个异常重新抛出来，变成普通的崩溃报告。

> 如果仍然看到 `[seedqueue] sodium could not allocate ...`，说明原生内存还是不够：这是环境问题
> 而不是崩溃。可以调大 `-Xmx`（跑分建议 6–8G）、调小 `chunkUpdateThreads`、或把
> `wallChunkDistance` / `chunkRadius` 降一档。

## 二十四、加入世界后"还是预览画面/帧率低/退出卡死"（0.9.0-beta.29）

现象（0.9.0-beta.28 及更早）：连进几个世界后，某一次进入的画面是**预览的三米后视角**，远处地形和雾
明显不对，帧率很低；退出游戏时窗口消失但进程不退，日志最后是

```
[Render thread/ERROR]: Shutdown failure!
java.lang.IllegalStateException: Cannot close a surface while it is acquired
```

日志里的关键序列（15:10:05 那次）：

```
joining queued entry #1
[preview] clearing preview ...                       ← 旧预览关掉
[preview] handed the preview level to Sodium          ← 同一帧又激活了一张卡的预览
[preview] preview rendered: sodiumVisible=20         ← 之后每帧都在渲染预览
[preview] position packet at (71.5, 63.0, -539.5)
```

也就是说，`playEntry` 把世界交给客户端后，**墙在同一帧又武装了一张卡的预览**，之后这个预览一直占着
`Minecraft.level` / 相机（第三人称预览视角），退出时它的 render pass 还占着 GPU surface，
`Minecraft.close()` 于是抛异常、进程退不掉。

修复：

1. 新增 `joinInProgress`：`playEntry` 开头置位，`frameSwapTick` / `previewCycleTick` 在它置位时直接返回；
   交接完成、超时、失败、关墙四条路径都会清掉它 —— 加入期间墙绝不可能再武装预览；
2. 交接完成时额外 `stopPreviewCycle()`，把同一帧已经武装的预览也放掉；
3. 兜底：检测到"已经在世界里、但还有预览在跑"时，释放预览、归还相机/移除假实体、
   并把 `Minecraft.level` 恢复成 `minecraft.player.level()`（真正加入的 client level），
   日志 `releasing a leftover preview in a joined world` / `restoring the joined client level`；
4. 交接时 `releasePreviewCameraAndEntities`（无条件重绑渲染相机 + 移除预览加的两个假 `LocalPlayer`）
   和 `keepSodiumBound = false` + `rebindRealLevel`，保证 Sodium 渲染的是真实世界。

另外：`SodiumLevelCache` 的集合换成 `ConcurrentHashMap`（后台线程和渲染线程同时访问），
退出兜底 12 秒后 `halt`（`-Dseedqueue.exitWatchdog=false` 可关）。

## 二十五、预览视角 = 玩家真实出生点主视角（WorldPreview 6.3.0-beta.20）

墙卡片的预览相机改成和玩家进世界时完全一致的第一人称出生视角：

- 站位用世界真正的 respawn 点（`overworld.getRespawnData().pos()`），不再固定抬高到
  `Heightmap.WORLD_SURFACE`；只有出生点本身被方块堵住（脚/头位置非空气）时才上抬，
  行为与服务端把玩家推上地表一致；
- 朝向用 respawn 数据里的 `yaw` / `pitch`（原来写死 `yaw=0, pitch=10°`，看起来总是微微低头）；
- 相机高度就是玩家眼高（feet + 1.62），第一人称不再创建任何相机锚点实体。

日志（每次激活预览打印一次）：

```
[preview] spawn camera: spawn=BlockPos{x=0, y=71, z=0} feet=71.0 eye=72.62 yaw=0.0 pitch=0.0 (thirdPerson=false)
[preview] spawn camera: spawn=BlockPos{x=-32, y=68, z=144} feet=69.0 ...  ← 出生点被方块占据，按服务端规则上抬一格
```

第三人称（`Preview Perspective = Third person`）仍可用：那种情况仍创建 3 格外、抬高 0.2 的相机锚点。

## 二十七、F6 回墙后的状态复位（0.9.0-beta.37）

现象：从世界里按 F6 回到墙后，所有卡片渲染错乱（一半旧画面一半新画面、地形浮空、卡片错位）。

根因：离开世界时 `stopPreviewCycle` 只停掉了预览循环，但**墙的渲染状态还留着上一局世界的东西**：
`WorldPreview` 的 main target override、每张卡的 `displayTarget`/`renderTarget`（可能还是旧窗口尺寸）、
`frameSwapEntry`/`frameSwapIndex`/`nextFrameSwapAt` 等。重新开墙时直接复用了这些状态，于是卡片纹理
是"旧帧 + 新帧"拼起来的。

修复：`returnToWallFromJoinedWorld` 在开墙前调用 `resetWallPreviewState`：

- `WorldPreview.setTargetOverride(null)` + `restoreMainTarget`，保证主渲染目标没有被别的东西换掉；
- 清空 `frameSwapEntry` / `frameSwapIndex` / `frameSwapDwellUntil` / `nextFrameSwapAt` / `activePreviewEntry`；
- 把每个条目的 `displayTarget`、`renderTarget` 置空（下次按当前窗口尺寸重建），并重置 `previewActive`、
  `lastPreviewAt`。

日志：`[seedqueue] wall preview state reset (N entries)`。


## 二十六、F6 语义与卡片相机的真正位置（0.9.0-beta.31 / WorldPreview 6.3.0-beta.21）

### F6

- **只有在世界里游玩时** F6 才被 SeedQueue 接管：直接回到墙界面，不再经过 `PauseScreen`；
  回墙后连续 60 tick 重新断言 `WallScreen`（`wallScreenHoldTicks`），把原版 "Saving world" 屏盖掉，
  所以看不到保存动画；
- **其他任何情况**（标题界面、墙界面、加载界面）F6 完全交回 Atum，行为和没有 SeedQueue 时一致，
  直接进入一个新世界；墙不再有自己的按键（F7 早已移除）。

### 卡片相机

墙卡片的画面默认由 CPU 渲染器 `SpawnViewRenderer` 生成（`WallScreen` 里 `previewTexture != null` 时
直接 blit 它），它之前和"玩家出生视角"有两处偏差：

1. 固定 `pitch -= 12°`，永远微微低头；
2. `clearEyeHeight` 要求"眼睛所在方块 **以及 +z 方向 2 格** 都是空气"，出生点旁边只要有山或树，
   相机就被抬高 1~10 格 —— 这就是相机位置不在出生点的直接原因。

现在改为：朝向取世界的真实 respawn `yaw/pitch`；只有**眼睛自身**在方块里时才上抬；
眼高固定 `feet + 1.62`。第三人称（`Preview Perspective = Third person`）仍是 3.5 格外、额外 -10°。




