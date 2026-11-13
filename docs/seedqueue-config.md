# SeedQueue 配置项说明 / SeedQueue configuration reference

> 对应 SeedQueue `1.0.0-rc.1+`（26.3 移植版）。配置文件：`config/mcsr/seedqueue.json`。
> 分类与顺序与游戏内 **SeedQueue 配置页** 一致。

## Queue（队列）

| 选项 | 默认 | 说明 |
| --- | --- | --- |
| Max Queued Seeds（最大队列数） | 3 | 队列中保留的实例总数（含锁定）。行×列决定可见数量，此值决定上限。 |
| Max Generating Seeds（最大同时生成数） | 0 | 同时处于生成中的实例数；0 = 不限制。 |
| Max Generating Seeds (Wall)（墙界面同时生成数） | 4 | 墙界面显示时的同一上限。 |
| Max World Generation %（最大生成百分比） | 100 | 每个实例生成到多少百分比暂停。降低可省时间，但卡片边缘可能延迟出图。 |
| Resume On Filled Queue（队列满时继续生成） | OFF | 队列满时让停在百分比上限的实例继续生成到 100%。 |
| Chunk Radius（区块半径） | 4 | 每实例生成 `(2r+1)²` 区块：4→81、2→25、1→9。**直接决定构建耗时**。 |

## Wall（墙界面）

| 选项 | 默认 | 说明 |
| --- | --- | --- |
| Use Wall Screen（使用墙界面） | ON | 关闭后用传统刷种界面。 |
| Rows / 行数 | 1 | 墙的行数。 |
| Columns / 列数 | 3 | 每行卡片数。`行×列` 决定可见卡片数。 |
| Reset Cooldown（重置冷却） | 400 ms | 两次重置之间的最小间隔，防连按重置风暴。 |
| Wait For Preview Setup（等待预览就绪） | OFF | 进入实例前等待预览建立完成。 |
| Bypass Wall Screen（跳过墙界面） | OFF | 直接进入下一个实例，不显示墙。 |
| Smart Switch（智能切换） | OFF | 选中实例未就绪时自动切到另一个已就绪的锁定实例。 |
| Join Transition（进入转场） | ON | 进入实例时播放转场动画。 |
| Sound Volume（音量） | 100% | SeedQueue 自身音效音量。 |
| Simulated window width / height（模拟窗口尺寸） | 0 | 0 = 跟随真实窗口。非零必须与窗口一致，否则卡片被裁剪。 |

## Performance（性能）

| 选项 | 默认 | 说明 |
| --- | --- | --- |
| Wall FPS（墙帧率） | 60 | 墙界面渲染帧率上限。 |
| Preview FPS（预览帧率） | 20 | 单张卡预览的渲染帧率上限。 |
| Preparing Previews（预备预览数） | -1 | 提前准备预览的数量；-1 = 自动。 |
| Concurrent Server Starts（并发服务器启动数） | 2 | 同时启动的集成服务器数量。 |
| Preload Worlds（预加载世界数） | 9 | 提前构建的世界数量。 |
| Wall Chunk Distance（墙区块距离） | 12 | 墙预览渲染视距（区块）。 |
| Wall Data Limit（墙数据上限） | 100 | 墙同时保留的预览数据量，降低可省内存。 |
| Idle Preview FPS（空闲预览帧率） | 15 | 无操作时的预览刷新率。 |
| Freeze Locked Previews（冻结锁定预览） | OFF | 锁定卡片的预览停止刷新，节省 GPU/CPU。 |
| Reduce World List（精简世界列表） | ON | 减少创建世界时的列表开销。 |
| Chunkmap Freezing（区块图冻结） | OFF (-1) | 生成开始 N 毫秒后停止刷新地图快照与相机；-1 = 不冻结（范围 -1…10000）。 |
| Check Allocated Memory（检查分配内存） | ON | 启动时检查 JVM 内存是否足够。 |

## Threading（线程）

| 选项 | 默认 | 说明 |
| --- | --- | --- |
| SeedQueue Thread Priority（SeedQueue 线程优先级） | 5 | 调度线程优先级（1–5）。 |
| Server Thread Priority（服务器线程优先级） | 5 | 集成服务器线程优先级。 |
| Background Worker Threads（后台线程数） | 0 (Auto) | 区块生成等工作线程数；0 = 由核心数推导。 |
| Background Worker Priority（后台线程优先级） | 5 | 后台线程优先级。 |
| Wall Worker Threads（墙线程数） | 0 (Auto) | 墙预览工作线程数。 |
| Wall Worker Priority（墙线程优先级） | 4 | 墙预览线程优先级。 |
| Chunk Update Threads（区块更新线程数） | 0 (Auto) | 区块更新线程数。 |
| Chunk Update Priority（区块更新优先级） | 3 | 区块更新线程优先级。 |

## Benchmark（跑分）

| 选项 | 默认 | 说明 |
| --- | --- | --- |
| Benchmark Resets（跑分重置次数） | 1000 | 一次跑分执行的重置次数（1–1000）。按 **B** 开始，**N / Esc** 取消。 |
| Benchmark Concurrency（跑分并发） | 0 | 跑分期间并行构建的实例数；0 = 跟随队列容量。可超过 `行×列`。 |
| Benchmark Sample Ticks（资源采样间隔） | 100 | 跑分期间资源采样间隔（tick）；0 = 关闭，100 ≈ 5 秒。 |
| Max Generating Count（最大同时生成数） | 0 | 同时生成中的实例数上限；0 = 不限制。实测 2/3 与不限制差异仅 4–5%。 |
| Benchmark Chunk Radius（跑分区块半径） | 2 | **仅跑分期间生效**的半径（1–10）。越小生成越快，卡片可见范围越小。 |

## Debug（调试）

| 选项 | 默认 | 说明 |
| --- | --- | --- |
| Chunk Progress（区块进度） | OFF | 显示每个实例的区块生成进度条。 |
| Show Debug Menu（显示调试菜单） | OFF | 在墙界面叠加 FPS 图形与 Profiler。 |
| Watchdog（看门狗） | OFF | 主线程卡顿时打印线程栈，便于定位卡死。 |
| Show Chunk Maps（显示区块地图） | OFF | 在卡片上叠加区块地图（隐藏项）。 |

## 说明

1. 原「Miscellaneous」大类已移除：`Benchmark Resets` 归入 Benchmark，`Check Allocated Memory` / `Chunkmap Freezing` 归入 Performance，`Chunk Progress` / `Show Debug Menu` / `Watchdog` 归入 Debug。
2. `showAdvancedSettings` 为隐藏开关，控制是否显示实验性选项。
3. 所有选项也可用 `-Dseedqueue.*` 启动参数覆盖（见 `docs/debug-parameters.md`）。

## 与性能的关系（实测摘要）

开发机：RTX 4060 Laptop 8 GB / i9-12900HX / 32 GB DDR5-4800，Chunk Radius 2、100% 生成。

| 配置 | 单实例构建 | RPS |
| --- | --- | --- |
| 并发 4（队列容量） | 1.66 s | 2.25 |
| 并发 8（跑分超容量） | 2.7–3.2 s | 2.54 |
| `maxGeneratingCount=2` | 1.45–2.21 s | 2.42 |
| `maxGeneratingCount=3` | 1.37–2.68 s | 2.44 |
| Benchmark Chunk Radius 1（9 区块） | 1.35–1.90 s | 2.74 |

结论：

1. **`gen` 占构建时间 70% 以上**，是唯一值得优化的环节；
2. 并发相关参数只有个位数百分比差异，且 CPU 已接近饱和；
3. 最有效的单项调整是 **Chunk Radius**（2→1 生成量降至 36%），或 `Benchmark Chunk Radius`（仅跑分生效）。
