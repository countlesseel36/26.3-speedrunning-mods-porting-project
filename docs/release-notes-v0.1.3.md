# 26.3 Speedrun Mods — v0.1.3

> ⚠️ 这些 mod 由中国 1.16.2+ 速通社区自行移植，**未获得 speedrun.com 的官方承认**；若使用这些 mod 进行速通，
> 你将在 speedrun.com **无法通过审核**。这些 mod 是 **vibe coding 的产物**，且为**测试版本**，可能会出现重大 bug，
> 可能会影响你的速通，**请权衡利弊后使用**。

## 包含的模组 / Included mods

| 文件 File | 版本 Version |
| --- | --- |
| `0.9.0-beta.80+26.3-SeedQueue.jar` | 0.9.0-beta.80 |
| `6.3.0-beta.42+26.3-WorldPreview.jar` | 6.3.0-beta.42 |
| `2.1.0-beta.19+26.3-Speedrun-API.jar` | 2.1.0-beta.19 |
| `2.3.0-beta.13+26.3-StandardSettings.jar` | 2.3.0-beta.13 |
| `1.4.0-beta.4+26.3-Atum.jar` | 1.4.0-beta.4 |
| `1.6.0-beta.3+26.3-FastReset.jar` | 1.6.0-beta.3 |

## 本次修改 / Changes

- **墙界面无缝化**：去掉卡片底色、标签底条与布局间距，预览按卡片矩形满幅绘制，多张卡片的画面直接邻接；
- **卡片文字默认隐藏**：`generating` / `ready` 与区块计数改由新选项「显示区块加载进度」控制（默认关）；
- **配置页精简**：移除「显示高级选项」开关，所有选项恒显示；移除「SeedQueue 区块加载」；「显示区块加载进度」移入调试分组；
- **墙调试信息**：打开「显示调试菜单」后，左上角显示 `SeedQueue 4/6 | generating 0`、`Heap … MiB | M …%` 与状态行；
- **队列世界继承 Atum 设置**：生成建筑、奖励箱、难度都读取 Atum 配置，不再使用 MC 默认值；
- **模拟窗口大小**：`simulatedWidth/Height` 接入预览目标分配（0 = 跟随真实窗口）；
- **单键绑定**：移除组合键与屏蔽键（SDL 输入读不到修饰键状态，Shift+M 类绑定静默失效），每个动作只保留单键，默认 `M`；
- **改键界面暂停**：`WallKeybindingsScreen.isPauseScreen()` 返回 true；
- **StandardSettings 配置页可改键**：补上按键捕获流程（点击行 → 按新键 → 写回）；
- **配置项悬停说明**：SeedQueue 每个选项都有中英文说明 tooltip；
- **中文化补齐**：StandardSettings `zh_cn` 现有 225 条键（原版选项名、键位名、音量分组等），并按类别聚拢排序（键位/声音/视频/聊天/鼠标/皮肤/调试/辅助功能）；
- **诊断开关**：`-Dspeedrunapi.logMissingNames=true` 输出每个选项实际绘制的文字与其 id，便于补翻译。

## 已知问题 / Known issues

1. **仍未能正确显示第三人称下玩家皮肤**——预览世界使用独立连接、没有玩家列表，皮肤渲染器按 UUID 查不到；已尝试复用真实 profile、改用 `RemotePlayer`、给 `getSkin()` 加 mixin 三种方案，均失败并回退；
2. **预览中远景为黑色的雾**——预览世界不 tick，远景雾色/天空过渡未正确解析，加载区域外仍呈现黑色。

## 安装 / Install

1. 把 6 个 jar 放进 `mods/`（**先删掉旧版本**，同名 mod 不能装两份）；
2. 需要 Minecraft 26.3 + Fabric Loader 0.19.5+，建议 Sodium 0.9.3；
3. 首次启动生成 `config/seedqueue.json` 与 `config/mcsr/seedqueue.json`。

## 实测 / Benchmark

开发者机器（12th Gen Intel(R) Core(TM) i9-12900HX，NVIDIA GeForce RTX 4060 Laptop GPU）：
`chunkRadius = 2` 时 **2.50 RPS**，`chunkRadius = 4` 时 **1.90 RPS**。

---

## English

**26.3 Speedrun Mods — v0.1.3**

> ⚠️ **WARNING** — Unofficial ports by the Chinese 1.16.2+ speedrunning community, **not officially
> recognised by speedrun.com**. Runs using them will **not pass verification**. These are **test builds**
> and **products of vibe coding** — weigh the trade-offs before using them.

**Changes**

- **Seamless wall**: card backgrounds, label strips and layout gaps are gone; previews fill their slots so
  the cards tile edge to edge.
- **Card text hidden by default**: `generating` / `ready` and the chunk counter are controlled by the new
  "Chunk Progress" option.
- **Cleaner config screen**: the "show advanced settings" toggle and the "SeedQueue chunk map" option were
  removed, every option is always listed, and "Chunk Progress" moved into the debug group.
- **Wall debug HUD**: with "Show debug menu" enabled the wall shows `SeedQueue 4/6 | generating 0`,
  `Heap … MiB | M …%` and a status line in the top-left corner.
- **Queued worlds inherit Atum settings**: structures, bonus chest and difficulty now come from Atum
  instead of vanilla defaults.
- **Simulated window size** is wired into preview target allocation (0 = follow the real window).
- **Single-key bindings**: combination and blocking keys were removed (SDL input cannot report modifiers,
  which silently killed Shift+M style bindings); each action has one key, "play next lock" is `M`.
- **The rebinding screen pauses the game**.
- **Key rebinding works in the StandardSettings config page** (click a row, press the new key).
- **Hover tooltips** for every SeedQueue option.
- **Complete Chinese translations** for StandardSettings (225 keys) and category bucketing
  (controls / sound / video / chat / mouse / skin / debug / accessibility).
- **Diagnostics**: `-Dspeedrunapi.logMissingNames=true` logs every rendered option label with its id.

**Known issues**

1. **Player skin is still missing in third-person cards** — the preview connection has no player list, so
   the skin renderer finds nothing by UUID. Three approaches were tried and reverted.
2. **Distant terrain fades to black fog in previews** — the preview level is never ticked, so the far fog
   colour is not resolved and everything outside the loaded area still turns black.

**Benchmark** (developer PC: i9-12900HX + RTX 4060 Laptop): **2.50 RPS at `chunkRadius = 2`**,
**1.90 RPS at `chunkRadius = 4`**.
