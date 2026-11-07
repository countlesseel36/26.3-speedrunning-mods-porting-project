# SeedQueue 0.9.0-beta 开发记录（归档）

> 归档范围：`0.9.0-beta.70` ~ `0.9.0-beta.103+26.3`（共 34 个构建）。
> 该版本线已于 `0.9.0-gamma.1+26.3` 起进入 gamma 重构线，beta jar 仅作留存，不再更新。

## 版本线一览

| 版本 | 主要改动 |
| --- | --- |
| beta.70–72 | 墙界面预览稳定化；SeedQueue 配置页选项说明 tooltip；`showChunkProgress` 选项加入 |
| beta.73 | 卡片状态文字（generating/ready）改由 `showChunkProgress` 控制 |
| beta.74 | 移除「显示高级选项」开关，所有选项恒显示；`showChunkProgress` 移入 advanced |
| beta.75 | 移除「SeedQueue 区块加载」；墙调试信息（`SeedQueue x/x \| generating n`、`Heap`、状态行） |
| beta.76 | 墙界面无缝化：去卡片底色/标签条/布局间距，预览满幅绘制 |
| beta.77 | 队列世界继承 Atum 的 `structures` / `bonusChest` |
| beta.78 | 队列世界继承 Atum 的 `difficulty` |
| beta.79 | 「模拟窗口大小」接入预览目标分配 |
| beta.80 | 修复 zh_cn 语言文件（此前被写成乱码导致整包回退英文） |
| beta.81–85 | 重生卡顿治理：解除预览绑定、释放预览显存、世界内不补世界、回墙立即恢复补卡 |
| beta.86–87 | 唤醒被冻结服务端时重置 `nextTickTimeNanos`（86 因 `@Shadow` 打错类导致启动崩溃，87 改反射修复） |
| beta.88 | 删除 `previewPauseTicks` 系列暂停逻辑（补卡规则改为“只在墙上补”） |
| beta.89 | 延迟删除仍在 prepare 的世界文件夹，消除 `NoSuchFileException` |
| beta.90 | Sodium manager 改为按帧回收（同帧批量销毁会触发 256 MiB arena 分配失败崩溃） |
| beta.91 | 预览分辨率默认 1280×720（含旧配置 0 → 1280 迁移） |
| beta.92 | 进入世界后清除残留 `BlankScreen`（曾吞掉 ESC） |
| beta.93–95 | ESC 诊断：`KeyEvent.key()` 与 `InputConstants.KEY_*` 编码不一致，按实测值处理 |
| beta.96–97 | 「返回墙界面」可改键（默认 F6）；改键页 ESC = 解除绑定 |
| beta.98–99 | 预览未就绪禁止进入世界；进入后清除残留 TitleScreen / ProgressScreen |
| beta.100 | 预览渲染距离限制为 `previewRadius + 2`，不再按原版渲染距离渲染空区块 |
| beta.101 | （已回退）卡片黑边按天空色填充 |
| beta.102 | 低风险修复：`refillQueue` 改用 `wallShown`、进入门槛统一 |
| beta.103 | 优化：卡片矩形池化、回收节流 1~2/帧、预览渲染距离状态收敛、日志降噪 |

## beta 线遗留问题（转入 gamma 线继续跟进）

1. 第三人称卡片仍无法显示玩家皮肤（预览连接无玩家列表）；
2. 预览远景仍为黑色（`chunkRadius` 较小时更明显，beta.100 只限制了渲染范围，未做背景填充）；
3. `ESCAPE = 58` 的判定待实测确认（若为 F1，需要重新标定）；
4. 改键写回依赖反射，失败时会打 warn；
5. 首次运行 `exit watchdog` 偶发（关闭世界时仍慢）。

## 归档文件

- `jars/`：`0.9.0-beta.70` ~ `0.9.0-beta.103+26.3` 全部 SeedQueue 构建；
- 本文件：`BETA-DEV-RECORD.md`。
