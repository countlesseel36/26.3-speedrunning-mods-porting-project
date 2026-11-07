# 26.3 Speedrun Mods — v0.2.0

> ⚠️ 这些 mod 由社区自行移植，未获 speedrun.com 官方承认；使用它们的速通记录无法通过审核。测试版本，vibe coding 产物。
> ⚠️ Unofficial community ports, **not recognised by speedrun.com**; runs will not pass verification. Test builds, vibe-coded.

## 包含的模组 / Included mods

| 文件 / File | 版本 / Version |
| --- | --- |
| `0.9.0-gamma.40+26.3-SeedQueue.jar` | 0.9.0-gamma.40 |
| `6.3.0-gamma.1+26.3-WorldPreview.jar` | 6.3.0-gamma.1 |
| `2.1.0-gamma.1+26.3-Speedrun-API.jar` | 2.1.0-gamma.1 |
| `2.3.0-gamma.1+26.3-StandardSettings.jar` | 2.3.0-gamma.1 |
| `1.4.0-gamma.1+26.3-Atum.jar` | 1.4.0-gamma.1 |
| `1.6.0-gamma.1+26.3-FastReset.jar` | 1.6.0-gamma.1 |

## 更新内容（相对 v0.1.3）/ Changes since v0.1.3

### 墙界面 / Wall screen

- 预览满幅无缝拼接，去掉卡片底色、标签条与间距
  / Previews are full-bleed and seamless: no card background, label strip or gap
- 卡片状态文字（`generating` / `ready` 与区块计数）默认隐藏，由「显示区块加载进度」控制
  / The card status line (`generating` / `ready` and the chunk counter) is hidden by default and controlled by
  the "Chunk Progress" option
- 打开「显示调试菜单」后左上角显示 `SeedQueue 4/6 | generating 0`、`Heap … MiB | M …%` 与状态行
  / With "Show debug menu" enabled the wall shows `SeedQueue 4/6 | generating 0`, `Heap … MiB | M …%` and a
  status line in the top-left corner
- 预览未就绪时禁止进入世界，进入后清理残留的标题/加载界面
  / Joining is blocked until the preview exists, and leftover title/loading screens are cleared after joining
- 预览渲染距离限制为"生成半径 + 2"，不再渲染大片空区块
  / Preview render distance is limited to "generated radius + 2" instead of the vanilla distance

### 队列与世界生成 / Queue and world generation

- 队列世界继承 Atum 的生成建筑、奖励箱、难度
  / Queued worlds inherit Atum's structures, bonus chest and difficulty
- 世界内不生成新世界，只在墙界面补卡，回墙立即恢复
  / No new worlds are generated in-game; the queue refills on the wall and immediately after returning to it
- 重生/换维度时释放预览绑定与显存；唤醒冻结服务端时重置 tick 计时
  / Preview binding and GPU memory are released on respawn; parked servers get their tick deadline reset
- 修复 prepare 中的世界被删除导致的 `NoSuchFileException`
  / Fixed the `NoSuchFileException` caused by deleting a world folder while it was still being prepared
- Sodium manager 改为逐帧回收，避免 256 MiB arena 分配失败崩溃
  / Sodium managers are destroyed one per tick, avoiding the 256 MiB arena allocation crash
- 「模拟窗口大小」接入预览目标分配，默认 1280×720
  / "Simulated window size" is wired into preview target allocation (default 1280×720)

### 配置界面 / Config screens

- 单页滚动列表 + 分类标题 + 可拖动滚动条
  / A single scrollable list with category headings and a draggable scrollbar
- 选项按类别聚拢并排序
  / Options are grouped and sorted by category
- StandardSettings 非调试项全部中文化
  / Every non-debug StandardSettings option is translated into Chinese
- 键位行可直接改键，支持鼠标按键，未绑定显示「未指定」
  / Key rows are rebindable, mouse buttons included, with unbound keys shown like vanilla
- 悬停显示说明；修复进入闪退、点击被吞、标题颜色透明等问题
  / Hover tooltips; fixed crashes on open, swallowed clicks and transparent heading colours
- StandardSettings 不再每次进世界做全量资源重载
  / StandardSettings no longer reloads all resources on every world join

### 按键 / Keys

- 「返回墙界面」可单独改键（默认 F6）
  / The wall's "back to wall" key is rebindable (F6 by default)
- Atum 检测到 SeedQueue 时禁用重置键与单世界重开
  / Atum disables its reset key and single-world reset while SeedQueue is installed
- 「Quit Wall Screen」允许绑定 Esc；未绑定时 Esc 不再关闭墙
  / "Quit Wall Screen" may be bound to Escape; when unbound, Escape no longer closes the wall
- 鼠标默认：左键进入、右键锁定、中键丢弃并补位
  / Mouse defaults: left = play, right = lock, middle = discard and refill

### 稳定性 / Stability

- 修复退出游戏时崩溃/挂起（退出过程跳过 GPU 清理并显式停止后台服务端）
  / Fixed the crash/hang on exit (GPU cleanup is skipped while closing and background servers are stopped explicitly)
- 修复多次死亡后逐渐卡顿：清理无 manager 支撑的废弃预览世界，避免 `ClientLevel` 泄漏
  / Fixed the gradual stutter after repeated deaths: orphaned preview levels are swept instead of leaking `ClientLevel`s
- 出生点恢复原版行为：不再把 `respawn_radius` 设成 0，死亡重生随机分布；首次进入仍与卡片预览一致（传送对齐）
  / Vanilla respawn behaviour restored: `respawn_radius` is no longer forced to 0, so deaths respawn randomly, while
  the first join still matches the card (aligned by teleport)
- 退出更快：关闭窗口后 watchdog 由 12 秒缩短到 4 秒（`-Dseedqueue.exitWatchdogMs` 可调）
  / Faster exit: the watchdog now halts 4s after shutdown instead of 12s (`-Dseedqueue.exitWatchdogMs`)
- 修复配置页 NPE 闪退与语言文件损坏导致回退英文
  / Fixed config-screen NPE crashes and a corrupted language file that caused an English fallback

## 已知问题 / Known issues

1. 第三人称卡片仍无法显示玩家皮肤
   / Player skin is still missing in third-person cards
2. 预览远景仍是黑色
   / The far distance in previews is still black

## 安装 / Install

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议 Sodium 0.9.3 与 Fabric API）
   / Minecraft 26.3 + Fabric Loader 0.19.5+ (Sodium 0.9.3 and Fabric API recommended)
2. 6 个 jar 放入 `mods/`，先删除旧版本
   / Put the six jars into `mods/`, deleting older versions first
3. 首次启动生成 `config/seedqueue.json` 与 `config/mcsr/` 配置
   / The first launch creates `config/seedqueue.json` and the files under `config/mcsr/`

## 实测 / Benchmark

`chunkRadius = 2` 时 **2.50 RPS**，`chunkRadius = 4` 时 **1.90 RPS**（i9-12900HX + RTX 4060 Laptop）。
/ **2.50 RPS** at `chunkRadius = 2` and **1.90 RPS** at `chunkRadius = 4` on the developer machine.
