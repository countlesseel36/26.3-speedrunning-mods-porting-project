# SeedQueue 配置项说明 / SeedQueue configuration reference

> 对应版本：SeedQueue `1.0.0-beta.x`（26.3 移植版）。配置文件位于 `config/mcsr/seedqueue.json`，
> 也可在游戏内 **SeedQueue 配置页** 修改。带「跑分」标记的项主要影响 Benchmark。

## Queue（队列）

| 选项 | 默认 | 作用 |
| --- | --- | --- |
| Chunk Radius / 区块半径 | 4 | 每个实例生成并展示的区块半径。`r` 对应 `(2r+1)²` 个区块：4→81、2→25、1→9。**直接决定生成耗时**。 |
| Max Queued Seeds / 种子队列上限 | 6 | 队列中保留的实例总数（含已锁定）。超过后不再补充新实例。 |
| Max Generating Seeds / 最大同时生成数 | 6 | 同时处于「生成中」状态的实例数上限。超过的实例排队等待，减少 CPU 争抢。 |
| Max Generating Seeds (Wall) / 墙界面最大同时生成数 | 6 | 在墙界面显示时的同一上限；通常与上一项相同。 |
| Max World Generation % / 最大世界生成百分比 | 100 | 每个实例生成到多少百分比就暂停。降低可减少单实例构建时间，但卡片边缘可能延迟出图。 |
| Resume On Filled Queue / 队列满时继续生成 | OFF | 队列已满时，让停在百分比上限的实例继续生成到 100%。 |
| Bypass Wall Screen / 跳过墙界面 | OFF | 进入实例时不显示墙界面，直接进入下一个实例。 |
| Columns / 列数 | 3 | 墙界面每行卡片数。 |
| Rows / 行数 | 1 | 墙界面行数。`行 × 列` 决定可见卡片数与队列容量上限。 |
| Join Transition / 进入转场 | ON | 从墙进入实例时播放转场动画（关闭可略微加快体感）。 |
| Reset Cooldown / 重置冷却 | 400 ms | 两次重置操作之间的最小间隔，防止连按导致重置风暴。 |

## Wall（墙界面）

| 选项 | 默认 | 作用 |
| --- | --- | --- |
| Use Wall Screen / 使用墙界面 | ON | 关闭后不显示墙，改用传统刷种界面。 |
| Auto Queue / 自动补充队列 | ON | 队列有空位时自动生成新实例。 |
| Show HUD / 显示 HUD | ON | 在墙界面显示种子编号、状态等提示信息。 |
| Wall FPS / 墙帧率 | 60 | 墙界面的渲染帧率上限。 |
| Wall Chunk Distance / 墙区块距离 | 12 | 墙界面渲染预览时使用的视距（区块）。 |
| Wall Data Limit / 墙数据上限 | 100 | 墙界面同时保留的预览数据量上限，降低可省内存。 |
| Wall Worker Priority / 墙工作线程优先级 | 4 | 墙预览工作线程的优先级（1–10，越高越抢占 CPU）。 |
| Wall Worker Threads / 墙工作线程数 | Auto | 墙预览线程数量，Auto 表示由核心数推导。 |

## Performance（性能）

| 选项 | 默认 | 作用 |
| --- | --- | --- |
| Simulated window height / 模拟窗口高度 | 0 | 0 = 跟随真实窗口。非零会强制预览分辨率，**必须与窗口一致**，否则卡片会被裁剪。 |
| Simulated window width / 模拟窗口宽度 | 0 | 同上。 |
| Smart Switch / 智能切换 | OFF | 选中的实例尚未就绪时，自动切换到另一个已就绪的锁定实例。 |
| Sound Volume / 音量 | 100% | SeedQueue 自身音效（重置、锁定等）的音量。 |
| Wait For Preview Setup / 等待预览就绪 | OFF | 进入实例前等待预览完全建立，减少进场后的空白。 |
| Freeze Locked Previews / 冻结锁定预览 | OFF | 锁定卡片的预览停止刷新，节省 GPU/CPU。 |
| Idle Preview FPS / 空闲预览帧率 | 15 | 无操作时预览的刷新率。 |
| Background Previews / 后台预览数 | 0 | 后台同时渲染的预览数量（0 = 仅当前可见卡片）。 |
| Preview FPS / 预览帧率 | 30 | 预览渲染的目标帧率上限。 |
| Preload Worlds / 预加载世界数 | 9 | 提前构建的世界数量上限，影响首次进入速度与内存。 |

## Threading（线程）

| 选项 | 默认 | 作用 |
| --- | --- | --- |
| Reduce World List / 精简世界列表 | ON | 减少创建世界时的列表开销（跳过不必要的世界列表刷新）。 |
| Concurrent Server Starts / 并发服务器启动数 | 2 | 同时启动的集成服务器数量，过高会争抢 CPU。 |
| Background Worker Priority / 后台工作线程优先级 | 5 | 区块生成等工作线程优先级（1–10）。 |
| Background Worker Threads / 后台工作线程数 | Auto | 后台线程池大小，Auto 由核心数推导（本机 24 线程 → 23）。 |
| Sodium Update Priority / Sodium 更新优先级 | 3 | Sodium 区块重建线程优先级。 |
| Sodium Update Threads / Sodium 更新线程数 | Auto | Sodium 区块重建线程数。 |
| SeedQueue Thread Priority / SeedQueue 线程优先级 | 5 | SeedQueue 自身调度线程的优先级。 |
| Server Thread Priority / 服务器线程优先级 | 5 | 集成服务器线程优先级。 |

## Miscellaneous（杂项）

| 选项 | 默认 | 作用 |
| --- | --- | --- |
| Benchmark Resets / 跑分重置次数 | 61 | 一次跑分执行的重置次数；可按 B 开始、N / Esc 取消。 |
| Check Allocated Memory / 检查分配内存 | ON | 启动时检查 JVM 内存是否足够，不足会给出提示。 |
| Chunkmap Freezing / 区块图冻结 | OFF | 生成过程中冻结小地图快照，减少每帧更新开销。 |
| Chunk Progress / 区块进度 | OFF | 显示每个实例的区块生成进度条。 |
| Show Debug Menu / 显示调试菜单 | OFF | 显示 SeedQueue 调试信息（线程、队列状态等）。 |
| Watchdog / 看门狗 | OFF | 主线程卡顿时打印线程栈，便于定位卡死。 |

## Debug（调试）

| 选项 | 默认 | 作用 |
| --- | --- | --- |
| Show Chunk Maps / 显示区块地图 | OFF | 在卡片上叠加区块地图（调试用）。 |

## Benchmark（跑分）

| 选项 | 默认 | 作用 |
| --- | --- | --- |
| Benchmark Resets / 跑分重置次数 | 1000 | 一次跑分执行的重置次数（1–1000）。按 B 开始，N 或 Esc 取消。 |
| Benchmark Concurrency / 跑分并发 | 0 | 跑分期间并行构建的实例数；0 = 跟随队列容量（行×列）。 |
| Benchmark Sample Ticks / 资源采样间隔 | 100 | 跑分期间资源采样间隔（tick）；0 = 关闭采样，100 约等于 5 秒一次。 |
| Max Generating Count / 最大同时生成数 | 0 | 同时处于「生成中」的实例数上限；0 = 不限制。降低可减少 CPU 争抢，但可能降低吞吐（实测 2/3 与不限制差异仅 4–5%）。 |
| Benchmark Chunk Radius / 跑分区块半径 | 2 | **仅跑分期间生效**的区块半径（1–10）。越小生成越快，卡片可见范围越小；日常刷种不受影响。 |

> 历史变更：原「Miscellaneous」大类已移除。`Benchmark Resets` 归入本大类；`Check Allocated Memory`、`Chunkmap Freezing` 归入 Performance；`Chunk Progress`、`Show Debug Menu`、`Watchdog` 归入 Debug。`Chunkmap Freezing` 的滑块范围已由 500000 收紧到 10000（-1 = OFF）。
## 与性能的关系（实测摘要）

开发机（RTX 4060 Laptop / i9-12900HX / 32 GB DDR5-4800）实测：

| 配置 | 单实例构建 | RPS |
| --- | --- | --- |
| Chunk Radius 2，100% 生成，并发 4 | 1.66 s | 2.25 |
| Chunk Radius 2，100% 生成，并发 8 | 2.7–3.2 s | 2.54 |
| Chunk Radius 2，`maxGeneratingCount=2` | 1.45–2.21 s | 2.42 |
| Chunk Radius 2，`maxGeneratingCount=3` | 1.37–2.68 s | 2.44 |

结论：

1. **`gen` 占构建时间 70% 以上**，是唯一值得优化的环节；
2. 提高并发或限制同时生成数只能带来个位数百分比的差异；
3. 最有效的单项调整是 **降低 Chunk Radius**（2→1 生成量降到 36%）；
4. `Max World Generation %` 降低可减少构建时间，但会让卡片边缘延迟出图。

/ See the tables above for each option. TL;DR: per-instance chunk generation dominates build time, so `Chunk Radius` is the most effective knob; concurrency limits only yield single-digit differences.
