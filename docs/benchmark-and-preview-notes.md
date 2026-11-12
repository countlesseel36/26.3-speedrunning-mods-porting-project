# 跑分与预览排查记录（2026-11-12）

当前构建：SeedQueue `0.9.0-rc.4-build.28+26.3`

## 1. 跑分可用性
- 按 B 启动；跑分中重复按 B 被忽略；
- N / Esc 取消：覆盖层接管输入，改为 tick 内轮询按键；
- 结果面板 15 秒后消失；重置循环由 tick 驱动，不再依赖墙界面。

## 2. 资源实测（RTX 4060 Laptop 8G / i9-12900HX / 32G DDR5-4800）
| 档位 | 并发 | RPS | 稳态 build | 进程 CPU | 整机 CPU | 堆峰值 |
| --- | --- | --- | --- | --- | --- | --- |
| A | 4 | 2.25 | 1.66 s | 55–61% | 60–70% | 1.9 GB |
| B | 8 | 2.54 | 2.7–3.2 s | 74–78% | 81–85% | 2.4 GB |

结论：
- 并发翻倍仅 +13%，单实例反而变慢 → 资源争抢；
- backgroundExecutor 线程数 = clamp(cores-1, 1, max.bg.threads=255) = 23，非瓶颈；
- 显存约 110–140 MB/实例，并发 16 也仅 ~2 GB / 8 GB → 不会爆，监控已移除。

## 3. 子阶段计时（保留）
```
[seedqueue] entry #N ready (seed …, 10 chunks in 2050 ms; server=330ms gen=1540ms capture=180ms)
```
- server：0.2–1.1 s；gen：1.1–2.2 s（60–75%，最大头）；capture：0.15–0.4 s；
- 总耗时统一以 prepareT0 为基准（此前基准不一致，已修）。

## 4. 跑分配置
- 跑分期间强制 maxWorldGenerationPercentage = 80%，取消或跑满后恢复用户设定值。

## 5. 环境变量
| 参数 | 作用 |
| --- | --- |
| -Dseedqueue.benchmarkConcurrency=N | 跑分并发（可超出队列容量） |
| -Dseedqueue.resourceSampleTicks=100 | 资源采样间隔（tick） |
| -Dseedqueue.benchmarkResets=N | 覆盖单次跑分次数 |

## 6. 预览「缺区块」为误判
- 日志 filled 只统计填充循环新建的包，不代表有数据的区块数；
- 实际预览视觉完整，另外 16 个包由生成阶段创建且含数据；
- 据此加的全量填充、FULL 校验、isEmpty 过滤已全部回退，capture 恢复 150–250 ms。

## 7. 当前 QueueEntry 状态
| 项目 | 状态 |
| --- | --- |
| 生成许可模型 | 原始行为（到上限即返回） |
| 抓包填充 | 只补外圈 |
| FULL / 非空校验 | 已移除 |
| 子阶段计时 | 保留 |
| 跑分 80% 覆盖 | 保留 |

## 8. 待办
1. 跑分半径 2→1 的 A/B（生成量降至 36%）；
2. 拆解 gen 内部（worldgen / light / 结构）；
3. 日志字段重命名（filled → filledByFillPass）。
