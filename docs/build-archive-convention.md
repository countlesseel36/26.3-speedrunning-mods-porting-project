# 构建归档约定

- `dist/` 根目录**只放每个模组的最新构建**（6 个 jar + `SeedQueue-LockedPool.zip`）。
- 旧构建按下述结构归档，便于随时回退：

```
dist/
├─ 0.9.0-rc.28+26.3-SeedQueue.jar     ← 当前构建
├─ 2.1.0-beta.45+26.3-Speedrun-API.jar
├─ ...
└─ past-build/
   ├─ SeedQueue/          ← 历史构建，按文件名排序即为版本顺序
   ├─ SpeedrunAPI/
   ├─ StandardSettings/
   ├─ WorldPreview/
   ├─ Atum/
   └─ FastReset/
```

## 规则

1. 每次构建成功后，把被替换下的旧 jar **移动到** `dist/past-build/<Mod>/`，不要删除。
2. 每个模组在 `past-build` 中**至少保留最近 20 个版本**；不足 20 个时全部保留。
3. 只有超过 20 个且确认无回退需求时，才可以清理最早的那些（清理前先在提交信息里注明）。
4. 归档文件不做任何重命名，版本号即文件名，便于按名字判断顺序。

## 快速回退

```powershell
$mods = "D:\prism_launcher\PrismLauncher\instance\26.3 test\minecraft\mods"
Copy-Item "dist\past-build\SeedQueue\0.9.0-rc.27+26.3-SeedQueue.jar" $mods -Force
```

注意：回退时先把 `mods` 里同模组的其它 jar 删掉，避免出现重复 mod id。

## 回退时的 -old 规则

回退到某个较早版本时，**被放弃的是较新的那些版本**，因此给它们加 `-old` 后缀，回退目标本身保持原始文件名。

示例：

| 情况 | 处理 |
|---|---|
| 已有 `rc.28`、`rc.29`，回退到 `rc.28` | `rc.29` 重命名为 **`rc.29-old`**，`rc.28` 不变 |
| 回退后继续开发 | 新版本号**继续沿用的下一个号**（如 `rc.30`），不要复用 `rc.29` |

改名的对象包括：`dist/` 根目录里被替换下来的 jar、`past-build/<Mod>/` 里对应的历史 jar，以及 `mods` 目录中的旧文件（后者直接删除即可）。

## 构建日志

> 规则：**每次构建/回退后都在本表追加一行**，记录版本、改动摘要、所在位置。新条目写在表格末尾（按时间顺序）。

| 日期 | 模组 | 版本 | 改动摘要 | 位置 |
| --- | --- | --- | --- | --- |
| 2026-11-07 | SeedQueue | `0.9.0-rc.28+26.3` | 未绑定键位显示「未指定」（原版 `key.keyboard.unknown`），版本号不变 | `dist/` |
| 2026-11-07 | SeedQueue | `0.9.0-rc.28-old+26.3` | 同一版本的旧行为（未绑定显示 `?`），用于回退对比 | `dist/past-build/SeedQueue/` |
| 2026-11-07 | SeedQueue | `0.9.0-rc.29-old`、`rc.30-old`、`rc.31-old` | 被放弃的鼠标绑定实验版本（回退到 rc.28 后加 `-old`） | `dist/past-build/SeedQueue/` |
| 2026-11-07 | SpeedrunAPI | `2.1.0-beta.45+26.3` | StandardSettings 键位支持绑定鼠标键（0 基修正） | `dist/` |
| 2026-11-07 | StandardSettings | `2.3.0-beta.18+26.3` | 进世界重活优化、异步写 standardoptions、toggleAll 逐项容错 | `dist/` |
| 2026-11-07 | WorldPreview | `6.3.0-beta.46+26.3` | 预览玩家附加真实 PlayerInfo（皮肤实验）、数据包队列上限 | `dist/` |
| 2026-11-07 | Atum | `1.4.0-beta.7+26.3` | 检测到 SeedQueue 时禁用重置键与单世界重开 | `dist/` |
| 2026-11-07 | FastReset | `1.6.0-beta.4+26.3` | 仅在"退出到标题"按钮期间跳过保存 | `dist/` |

## 同一版本号重复归档

同一个版本号可能被构建多次（例如微调后再构建一次），归档时**不能覆盖**已有文件，而是按顺序加序号：

| 第几次归档 | 文件名示例 |
| --- | --- |
| 第 1 次 | `0.9.0-rc.28+26.3-SeedQueue.jar` |
| 第 2 次 | `0.9.0-rc.28-1+26.3-SeedQueue.jar` |
| 第 3 次 | `0.9.0-rc.28-2+26.3-SeedQueue.jar` |

- 序号加在版本号之后、`+26.3` 之前；
- 已带 `-old` 的构建同样适用：`0.9.0-rc.28-old-1+26.3-SeedQueue.jar`；
- 序号从 **1** 开始，按归档时间递增；`dist/` 根目录始终只保留未加序号的最新构建。
| 2026-11-07 | SpeedrunAPI | `2.1.0-rc.1+26.3` | 开发代号由 beta 切换为 rc（内容同 beta.45） | `dist/` |
| 2026-11-07 | StandardSettings | `2.3.0-rc.1+26.3` | 开发代号切换为 rc（内容同 beta.18） | `dist/` |
| 2026-11-07 | WorldPreview | `6.3.0-rc.1+26.3` | 开发代号切换为 rc（内容同 beta.46） | `dist/` |
| 2026-11-07 | Atum | `1.4.0-rc.1+26.3` | 开发代号切换为 rc（内容同 beta.7） | `dist/` |
| 2026-11-07 | FastReset | `1.6.0-rc.1+26.3` | 开发代号切换为 rc（内容同 beta.4） | `dist/` |
| 2026-11-07 | SeedQueue | `0.9.0-rc.29+26.3` | 修复多次死亡后的卡顿：清理 `PREVIEW_LEVELS` 中无 manager 的废弃 `ClientLevel`（换世界时 sweep） | `dist/` |
| 2026-11-07 | SeedQueue | `0.9.0-rc.30`–`rc.34` | 退出线程诊断、出生点对齐的多次尝试（期间发现对齐未执行/传送类型错误） | `dist/past-build/SeedQueue/` |
| 2026-11-07 | SeedQueue | `0.9.0-rc.35`–`rc.36` | 对齐改为缓存 BlockPos，摆脱预览对象已释放的问题 | `dist/past-build/SeedQueue/` |
| 2026-11-07 | SeedQueue | `0.9.0-rc.37` | 传送服务端玩家（修正 ClientLevel→ServerLevel 强转），对齐生效 | `dist/past-build/SeedQueue/` |
| 2026-11-07 | SeedQueue | `0.9.0-rc.38`–`rc.39` | 退出挂起诊断：打印非守护线程与 18 帧栈 | `dist/past-build/SeedQueue/` |
| 2026-11-07 | SeedQueue | `0.9.0-rc.40+26.3` | watchdog 12s→4s（`-Dseedqueue.exitWatchdogMs`）；当前构建 | `dist/` |
| 2026-11-08 | SeedQueue | `0.9.0-rc.55+26.3` | 队列/跑分上限、警告文案、分类标题本地化；v0.2.1 发布构建 | `dist/` |
| 2026-11-08 | SpeedrunAPI | `2.1.0-rc.4+26.3` | 全局开关置顶、分类标题翻译；v0.2.1 发布构建 | `dist/` |
| 2026-11-09 | SeedQueue | `0.9.0-rc.79-disabled-benchmark.1+26.3` | 跑分功能暂时禁用（按键/选项保留 + 红色警告）；配置页顶部新增显存警告并自动换行 | `dist/` |
| 2026-11-09 | SpeedrunAPI | `2.1.0-rc.6-disabled-benchmark.1+26.3` | 顶部警告自动换行（word wrap），列表几何不变 | `dist/` |

## 版本号格式（rc 阶段）

- 带禁用功能标记：`<主版本>-rc.<序号>-disabled-benchmark.<N>+26.3`
- 其他模组：`<主版本>-rc.1-build.1+26.3`

当前统一为 rc.1 / build.1；后续功能改动再推进编号。
| 2026-11-09 | SeedQueue | `0.9.0-rc.1-disabled-benchmark.2+26.3` | 外圈四边强制补区块（压测 189/189、后续 225/225 通过） | `dist/` |
| 2026-11-09 | SeedQueue | `0.9.0-rc.1-disabled-benchmark.3+26.3` | 退出修复：watchdog 提前启动 + 后台清理；v0.3.0 发布构建 | `dist/` |
| 2026-11-10 | SeedQueue | `0.9.0-rc.2-disabled-benchmark.1+26.3` | 显存泄漏重大修复（渲染目标释放 + 渲染线程销毁）；暂停界面 F6 回墙；v0.3.1 发布构建 | `dist/` |
| 2026-11-10 | 归档 / archive | — | 将 `dist/` 中 rc.1-disabled-benchmark.1–.10 等 10 个历史 SeedQueue 构建移入 `dist/past-build/SeedQueue/`；根目录只保留 6 个当前发布 jar | `dist/past-build/` |
| 2026-11-12 | SeedQueue / SpeedrunAPI / StandardSettings | `0.9.0-rc.2-disabled-benchmark.16` / `2.1.0-rc.1-disabled-benchmark.23` / `2.3.0-rc.1-build.6` | v0.3.2 发布构建：分组/排序修正、鼠标键修复、显存泄漏修复 | `dist/` |

## 归档结构（当前）

```
dist/
├─ 0.9.0-rc.3-disabled-benchmark.7+26.3-SeedQueue.jar     ← 各模组当前构建
├─ 2.1.0-rc.2-disabled-benchmark.1+26.3-Speedrun-API.jar
├─ 2.3.0-rc.2-build.1+26.3-StandardSettings.jar
├─ 6.3.0-rc.1-build.1+26.3-WorldPreview.jar
├─ 1.4.0-rc.2-build.1+26.3-Atum.jar
├─ 1.6.0-rc.1-build.1+26.3-FastReset.jar
└─ past-build/
   ├─ SeedQueue/        118 个历史构建
   │  └─ beta/           34 个 0.9.0-beta.* 构建（原 archive/seedqueue-beta-0.9.0）
   ├─ SpeedrunAPI/       31 (+ beta 34)
   ├─ StandardSettings/  12 (+ beta 16)
   ├─ WorldPreview/       2 (+ beta 6)
   ├─ Atum/               3 (+ beta 4)
   └─ FastReset/          1 (+ beta 2)
```

- 非 beta 历史构建放在 `past-build/<Mod>/`；
- beta 阶段构建统一放在 `past-build/<Mod>/beta/`，便于与 rc 阶段区分；
| 2026-11-12 | 归档 / archive | — | 所有旧 build 与 beta 版本归档：SeedQueue 118(+beta34)、SpeedrunAPI 31(+beta34)、StandardSettings 12(+beta16)、WorldPreview 2(+beta6)、Atum 3(+beta4)、FastReset 1(+beta2) | `dist/past-build/` |
| 2026-11-12 | SeedQueue 回滚 / rollback | `0.9.0-rc.3-disabled-benchmark.2+26.3` | 回滚到 benchmark.2：`.7`–`.11` 归档到 `past-build/SeedQueue/`，根目录与实例保持 `.2`，版本号不变 | `dist/past-build/SeedQueue/` |

## 版本命名（自 2026-11-12 起生效）

下一次**代码改动**起，不再沿用当前的 `rc.3-disabled-benchmark.2` 线，统一进入 **rc.4 线**，并保留 `disabled-benchmark` 后缀语义：

| 模组 | 下一次构建的版本 |
| --- | --- |
| SeedQueue | `0.9.0-rc.4-disabled-benchmark.1+26.3` |
| SpeedrunAPI | `2.1.0-rc.3-disabled-benchmark.1+26.3` |
| StandardSettings | `2.3.0-rc.3-build.1+26.3` |
| Atum | `1.4.0-rc.3-build.1+26.3` |
| WorldPreview | 保持不变：`6.3.0-rc.1-build.1+26.3` |
| FastReset | 保持不变：`1.6.0-rc.1-build.1+26.3` |

规则：

- 主版本线递增（rc.N → rc.N+1）时，构建号从 `.1` 重新开始；
- 仍处于停用状态的实验功能继续使用 `-disabled-benchmark` 后缀，并在 release 说明中注明；
- 只有对应模组**真的发生改动**时才提升它的版本，未改动的模组保持原版本号随包发布；
- 每次构建完成追加一行到本文件的构建日志表。
| 2026-11-12 | SeedQueue | `0.9.0-rc.4-build.10+26.3` | 跑分分段计时与现状分析，优化方案记录于 `docs/benchmark-analysis.md`（暂不实施） | `dist/` |
| 2026-11-12 | SeedQueue | `0.9.0-rc.4-build.28+26.3` | 跑分实测、子阶段计时、预览误判回退；记录于 `docs/benchmark-and-preview-notes.md`（未提交） | `dist/` |

## 版本号策略（2026-11-12 更新）

- **本地构建**：一律带 build 号，例如 `0.9.0-rc.5-build.1+26.3`、`0.9.0-rc.5-build.2+26.3`；
- **发布时**：文件名去掉 build 号，并对 build 号不为 1 的模组把 rc 号进一位，例如
  `0.9.0-rc.5-build.3` → 发布为 `0.9.0-rc.6`；
- 发布后继续本地修改，从 `0.9.0-rc.5-build.1`（或下一个 rc 线的 build.1）重新计数；
- 发布产物与本地构建分开归档：发布版本留在 `dist/`（或 release），本地中间构建进 `dist/past-build/<Mod>/`。

## SeedQueue 版本线变更（2026-11-13）

SeedQueue 改用 **`1.0.0-beta.x`** 版本线，放弃 `0.9.0-rc.N(-build.M)`：

- 本地构建：`1.0.0-beta.1+26.3`、`1.0.0-beta.2+26.3` …；
- 发布：同样去掉 build/序号约定，按 `1.0.0-beta.x` 命名；
- 其它模组仍沿用各自版本线，不受影响。

## 版本号规则补充（2026-11-13）

- **已发布版本线上的 bug 修复不升 rc**，改用 build 号：`1.0.0-rc.1` → 修复版 `1.0.0-rc.1-build.1`、`-build.2` …；
- 只有**下一次正式发布**才把 build 号去掉并决定是否升 rc；
- 因此在 v0.5.0（rc.1）之后的修复构建命名为 `1.0.0-rc.1-build.N`，不再使用 `rc.2` / `rc.3`。

## 更新日志写法（2026-11-13 起）

每次发布只写**相对于上一个 release 的变化**，不重复历史内容。
