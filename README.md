# 26.3 Speedrun Mods Ports（Minecraft 26.3 速通模组移植）

**中文** | [English below](#english)

MCSR（速通）相关 Fabric Mod 的 **Minecraft 26.3** 移植工程：SeedQueue、WorldPreview、SpeedrunAPI、
Atum、FastReset、StandardSettings 六个 mod，以及一个把锁定实例放进独立池的配套资源包。

全部用本机 JDK 25 直接对着本地 26.3 jar + Fabric Loader 0.19.5 编译（无 Gradle、无外网），
并用仓库里的 `minecraft/` 实例实机验证。

*Ports of the MCSR (speedrunning) Fabric mods to **Minecraft 26.3**: SeedQueue, WorldPreview, SpeedrunAPI,
Atum, FastReset, StandardSettings, plus a resource pack that moves locked instances into their own pool.
Everything is compiled on the local machine against the 26.3 jar + Fabric Loader 0.19.5 with plain javac
(no Gradle, no network) and verified in the bundled `minecraft/` test instance.*

> ⚠️ **警告 / WARNING**
>
> 这些 mod 由中国 1.16.2+ 速通社区自行移植，**未获得 speedrun.com 的官方承认**；若使用这些 mod 进行速通，
> 你将在 speedrun.com **无法通过审核**。这些 mod 是 **vibe coding 的产物**，且为**测试版本**，可能会出现重大 bug，
> 可能会影响你的速通，**请权衡利弊后使用**。
>
> These mods are unofficial ports made by the Chinese 1.16.2+ speedrunning community and are **not
> officially recognised by speedrun.com**. Runs made with them will **not pass speedrun.com verification**.
> They are **products of vibe coding** and **test builds**: major bugs may exist and may affect your runs.
> **Weigh the trade-offs before using them.**

## 交付清单 / Deliverables

| 模块 Module | 原版本 Upstream | 26.3 产物 Artifact | 状态 Status |
| --- | --- | --- | --- |
| `seedqueue` | 0.14.2+1.21.4 | `0.9.0-beta.60+26.3-SeedQueue.jar` | ✅ 实机验证 tested |
| `worldpreview` | 6.3.1-beta.3+1.21.4 | `6.3.0-beta.35+26.3-WorldPreview.jar` | ✅ 实机验证 tested |
| `speedrunapi` | 2.1-beta.1+1.21.4 | `2.1.0-beta.8+26.3-Speedrun-API.jar` | ✅ 重写完成 rewritten |
| `atum` | 1.3.4+1.21.9-1.21.10 | `1.4.0-beta.4+26.3-Atum.jar` | ✅ 实机验证 tested |
| `fast-reset` | 1.5.1+1.21.9-1.21.10 | `1.6.0-beta.3+26.3-FastReset.jar` | ✅ 实机验证 tested |
| `standardsettings` | 2.3.1+26.1-26.1.2 | `2.3.0-beta.2+26.3-StandardSettings.jar` | ✅ 实机验证 tested |
| 资源包 Resource pack | — | `SeedQueue-LockedPool.zip` | ✅ 可用 usable |

**所有 jar 与资源包都发布在 [Releases](../../releases/latest) 页面**（源码仓库不含二进制）；
`dist/` 只是本地构建输出目录。

*All jars and the resource pack are published on the [Releases](../../releases/latest) page — the source
repository ships no binaries; `dist/` is only the local build output folder.*

**版本号约定 / Version scheme**：`<主>.<次>.0-beta.<构建号>+26.3`，jar 名 `<版本>-<名字>.jar`，
不带 `test_` 前缀；每次修复只递增被改动 mod 的构建号。

*`<major>.<minor>.0-beta.<build>+26.3`, jar name `<version>-<name>.jar`, no `test_` prefix. Each fix bumps
only the build number of the mods it touches.*

## 安装 / Installation

1. 到 [Releases](../../releases/latest) 下载全部 6 个 jar，放进实例的 `mods/`（已装旧版本的先删掉，同名 mod 不能装两份）；
2. 需要「锁定实例独立池」时，再下载 `SeedQueue-LockedPool.zip` 放进 `resourcepacks/` 并在游戏里启用；
3. 首次启动后会生成 `config/seedqueue.json`（运行时配置）与 `config/mcsr/seedqueue.json`（SpeedrunAPI 配置）。

*1. Download the six jars from [Releases](../../releases/latest) and copy them into the instance `mods/`
folder (delete older versions first — never two copies of the same mod). 2. For the locked-instance pool,
also download `SeedQueue-LockedPool.zip` into `resourcepacks/` and enable it in game. 3. The first launch
creates `config/seedqueue.json` (runtime config) and `config/mcsr/seedqueue.json` (SpeedrunAPI config).*

## 使用 / Usage

### 墙界面 / The wall

- 标题界面的 **SeedQueue** 按钮打开墙；队列在后台生成世界，卡片实时显示 3D 预览；
- 鼠标：**左键**进入该实例，**右键**锁定/解锁，**中键**丢弃并补位；
- 键盘（默认值，可在 SeedQueue 设置 → Keybindings 里改）：

*The **SeedQueue** button on the title screen opens the wall: worlds are generated in the background and
each card shows a live 3D preview. Mouse: **left** joins, **right** locks/unlocks, **middle** discards and
refills. Keyboard defaults (rebindable in SeedQueue settings → Keybindings):*

| 操作 Action | 默认键 Default |
| --- | --- |
| 进入悬停实例 Join hovered | `R` |
| 锁定/解锁 Lock / unlock | `L` |
| 重置悬停实例 Reset hovered | `E` |
| 重置全部 Reset all | `T` |
| 进入并重置其余 Join + reset rest | `F` |
| 标记下局加入 Schedule join | `G` / 全部 all `H` |
| 重置整列 / 整行 Reset column / row | `K` / `J` |
| 下一个锁定实例 Play next lock | `M`（或 `Shift+M`） |
| 开始 / 取消跑分 Start / cancel benchmark | `B` / `N` |
| 关闭墙 Close wall | `Esc` |

### F6

- **在世界里游玩时**：F6 直接回到墙（`disconnect` 后立刻显示墙界面，保存屏按原版行为出现）；
- **其他任何情况**（标题界面、墙、加载中）：F6 完全无反应，也不会触发 Atum 的重置。

*While playing in a world, **F6** returns to the wall (plain `disconnect` + wall screen; the vanilla
saving screen still shows). In every other situation (title screen, wall, loading) F6 does nothing at all
and does not trigger Atum.*

### 跑分 / Benchmark

墙上按 `B` 开始，按实际吞吐计时，完成后面板显示 `N resets in X.XXs (Y.YY RPS)`；`benchmarkResets` 可配 **1–2000**。

开发者实机数据（12th Gen Intel(R) Core(TM) i9-12900HX，NVIDIA GeForce RTX 4060 Laptop GPU）：

| chunkRadius | RPS |
| --- | --- |
| 2 | **2.07** |
| 4 | **1.57** |

*Press `B` on the wall; the run is timed by actual throughput and the panel reports `N resets in X.XXs
(Y.YY RPS)`. `benchmarkResets` accepts **1–2000**. Measured on the developer's PC (12th Gen Intel(R)
Core(TM) i9-12900HX, NVIDIA GeForce RTX 4060 Laptop GPU): **2.07 RPS at `chunkRadius = 2`** and
**1.57 RPS at `chunkRadius = 4`**.*

## 构建 / Building

```powershell
cd 26.3_speedrun_mods_ports
# 全部构建 / build everything
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Mod all
# 只构建一个模块 / single module
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Mod seedqueue
```

编译输入全部来自本机，无需联网 / all inputs come from the local machine, no network needed：

- `%APPDATA%\.minecraft\versions\26.3\26.3.jar`
- `%APPDATA%\.minecraft\libraries\**`（fabric-loader 0.19.5、sponge-mixin、asm、log4j…）
- `build/libs/mixinextras.jar`（从 fabric-loader 内嵌 jar 提取 / extracted from the bundled loader jar）
- `minecraft/mods/sodium*.jar`（Sodium 兼容代码需要 / needed by the Sodium compatibility code）

产物输出到 `dist/<版本>-<名字>.jar`；`-Mod all` 会**先**构建 worldpreview 再构建 seedqueue，
因为 SeedQueue 编译时依赖 `dist/` 里的 WorldPreview。单独改 WorldPreview 后请再跑一次 `-Mod seedqueue`。

*Artifacts land in `dist/<version>-<name>.jar`. `-Mod all` builds worldpreview **before** seedqueue because
SeedQueue compiles against the WorldPreview jar in `dist/`; after changing WorldPreview alone, run
`-Mod seedqueue` again.*

## 实机测试（冒烟）/ Smoke testing

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\run-smoke.ps1 -Seconds 90 -ModsSource .\dist
```

脚本用本机 26.3 + Fabric Loader 0.19.5 启动 `minecraft/` 实例（带 Sodium/Lithium/Krypton/SpeedRunIGT 等真实环境），
只替换本工程构建的 jar；临时把需要 MCSR 启动器加锁的 Hermes 改名为 `.disabled`（结束后还原）；日志写到 `build/smoke.log`。

*The script launches the `minecraft/` instance with the real mod set (Sodium, Lithium, Krypton, SpeedRunIGT…),
replacing only the jars built here. Hermes needs the MCSR launcher lock, so it is temporarily renamed to
`.disabled` and restored afterwards. The log goes to `build/smoke.log`.*

常用参数（`-ExtraJvm "…;-D…"`，以 `;` 分隔）/ useful flags (semicolon separated)：

| 参数 Flag | 作用 Purpose |
| --- | --- |
| `-Dseedqueue.wall=true -Dseedqueue.wallAutoSmoke=true` | 启动即打开墙 / open the wall on launch |
| `-Dseedqueue.wallSmokeJoin=true` | 点击第一张 READY 卡进世界 / join the first ready card |
| `-Dseedqueue.f6Smoke=true` | 进世界后模拟按 F6 / simulate F6 after joining |
| `-Dseedqueue.benchmarkSmoke=true -Dseedqueue.benchmarkResets=40` | 自动跑分 / automatic benchmark |
| `-Dseedqueue.wallScreenshot=true -Dseedqueue.wallScreenshotTicks=120` | 截图墙 / screenshot the wall |
| `-Dseedqueue.openWallTrace=true` | 打印 `openWall` 调用栈 / print the `openWall` stack trace |
| `-Dseedqueue.simulateNativeOom=N` | 第 N 次原生分配强制失败，验证崩溃降级 / force the Nth native allocation to fail |
| `-Dseedqueue.exitWatchdog=false` | 关闭退出兜底（默认 12 秒后 halt）/ disable the exit watchdog |
| `-Dspeedrunapi.smoke=config` | 自动打开 SpeedrunAPI 配置界面并截图 / open the config UI and screenshot it |

## 目录结构 / Layout

```
26.3_speedrun_mods_ports/
├─ build.ps1                 # 编译打包 / compile + package (-Mod all | seedqueue | worldpreview | …)
├─ dist/                     # 产物 jar + 资源包 / artifacts + resource pack
├─ seedqueue/ worldpreview/ speedrunapi/ atum/ fast-reset/ standardsettings/   # 源码 / sources
├─ tools/run-smoke.ps1       # 冒烟测试 / launcher-free smoke test
├─ tools/publish-github.ps1  # 一键发布到 GitHub / push this repo to GitHub
├─ docs/                     # 技术文档与截图 / technical docs + screenshots
├─ minecraft/                # 本地 26.3 测试实例（含第三方 mod）/ local test instance
├─ archive/                  # 历史归档 / archived earlier ports
└─ build/                    # 编译中间产物、测试日志 / build output + smoke logs
```

## 文档 / Documentation

- [docs/seedqueue-3d-wall.md](docs/seedqueue-3d-wall.md)：3D 墙的完整技术记录（27 节）——渲染路径、
  与 Sodium 的多管理器协作、零加载交接、F6 语义、跑分、原生崩溃修复、出生点对齐、卡片闪烁/错乱排查等；
- [docs/seedqueue-live-preview.md](docs/seedqueue-live-preview.md)：实时预览抓帧流程；
- `docs/*.png`：各阶段的验证截图（墙、配置界面、跑分面板、锁定池资源包…）。

*`docs/seedqueue-3d-wall.md` is the full engineering log (27 sections): render path, multi-manager handling
with Sodium, zero-load hand-off, F6 semantics, benchmark, native-crash fix, spawn alignment, card
flicker/corruption investigations. `docs/seedqueue-live-preview.md` covers the live capture pipeline and
`docs/*.png` holds the verification screenshots.*

## 已知限制 / Known limitations

- **Sodium 0.9 是单例渲染器**：切换世界会销毁并重建 `RenderSectionManager`，因此卡片切换有重建开销；
- **预览范围由 `chunkRadius` 决定**（默认 4）：调大能看得更远，但生成更慢、内存占用更高；
- **Hermes 需要 MCSR 启动器加锁运行**，直接用普通启动器启动会被拒绝（冒烟脚本会临时禁用）；
- 原生内存不足时 Sodium 的顶点缓冲分配会失败：现在会记 `[seedqueue] sodium could not allocate …` 并跳过该
  区块编译（不再崩 JVM），此时把 `-Xmx` 调大即可；
- 退出游戏若被第三方非守护线程池挂住，12 秒后由 `SeedQueue exit watchdog` 强制 halt。

*Sodium 0.9 keeps a single renderer and rebuilds `RenderSectionManager` on every level switch, so card
swaps cost a rebuild. The previewed area is `chunkRadius` (default 4) chunks — larger looks further but
generates slower and uses more memory. Hermes only runs under the MCSR launcher lock (the smoke script
disables it). When native memory runs out, Sodium vertex allocation fails; the mod now logs
`[seedqueue] sodium could not allocate …` and skips that section instead of crashing the JVM — raise `-Xmx`.
If another mod keeps a non-daemon pool alive at exit, the `SeedQueue exit watchdog` halts the JVM after 12s.*

## 许可与致谢 / License & credits

本仓库的移植代码与 `SeedQueue-LockedPool` 资源包按 **MIT** 发布，见 [LICENSE](LICENSE)；
上游模组的原作者与来源列在 [NOTICE.md](NOTICE.md)，其版权归原作者所有。

*The port code and the `SeedQueue-LockedPool` pack are MIT licensed — see [LICENSE](LICENSE). Upstream
authors and sources are listed in [NOTICE.md](NOTICE.md) and keep their own copyright.*

## 发布到 GitHub / Publishing to GitHub

**源码进仓库，二进制进 Release。** `dist/*.jar` 与资源包 zip 已被 `.gitignore` 排除，
它们作为 Release 附件发布。

*Source goes into the repository, binaries go into a Release. `dist/*.jar` and the resource pack zip are
excluded by `.gitignore` and published as release assets.*

```powershell
# 1) 先在 GitHub 上建一个空仓库（不要勾选 README/.gitignore）
#    create an empty repository on GitHub first (no README/.gitignore)
powershell -ExecutionPolicy Bypass -File .\tools\publish-github.ps1 `
    -RepoUrl https://github.com/<user>/<repo>.git `
    -CommitMessage "26.3 port: SeedQueue 0.9.0-beta.60 / WorldPreview 6.3.0-beta.35"

# 2) 打 tag 并创建 Release，把 dist 里的 6 个 jar + 资源包作为附件
#    tag + release, attaching the six jars and the resource pack from dist/
powershell -ExecutionPolicy Bypass -File .\tools\publish-github.ps1 `
    -RepoUrl https://github.com/<user>/<repo>.git -Release mods_update_v0.1.0
```

没有 `gh` CLI 时，直接在 GitHub 网页上 **Releases → Draft a new release**：标题写 `26.3 port mods update vX.Y.Z`、
tag 用 `mods_update_vX.Y.Z`，说明以 `# 26.3 Speedrun Mods — vX.Y.Z` 开头并附一段 English，
然后把 `dist/` 里的文件拖进 Assets。

*Without the `gh` CLI, use **Releases → Draft a new release** on GitHub: title `26.3 port mods update vX.Y.Z`,
tag `mods_update_vX.Y.Z`, notes starting with `# 26.3 Speedrun Mods — vX.Y.Z` plus an English section,
then drag the files from `dist/` into the assets area.*

`.gitignore` 已排除 `minecraft/`（约 1.8 GB 的测试实例，含第三方 mod）与 `build/`，
实际提交约 300 个文件 / 25 MB（其中 19 MB 是 `docs/` 里的截图）。

*`.gitignore` excludes `minecraft/` (the ~1.8 GB test instance with third-party mods) and `build/`; the
commit is roughly 300 files / 25 MB, of which 19 MB are screenshots in `docs/`.*

<a id="english"></a>

---

## English summary

Unofficial **Minecraft 26.3** ports of the MCSR Fabric mods SeedQueue, WorldPreview, SpeedrunAPI, Atum,
FastReset and StandardSettings, made by the **Chinese 1.16.2+ speedrunning community**. They are **not
recognised by speedrun.com**: runs that use them will not pass verification, and they are test builds that
may still contain major bugs.

- **Install**: copy every `dist/*.jar` into `mods/`; optionally enable `dist/SeedQueue-LockedPool.zip` as a
  resource pack; configs are `config/seedqueue.json` and `config/mcsr/seedqueue.json`.
- **Wall**: the **SeedQueue** button on the title screen opens it. Left click joins, right click locks,
  middle click discards. Default keys: `R` join, `L` lock, `E` reset, `T` reset all, `F` join + reset rest,
  `G`/`H` schedule join (one/all), `K`/`J` reset column/row, `M` play next lock, `B`/`N` start/cancel
  benchmark, `Esc` close.
- **F6**: while playing in a world it returns to the wall; anywhere else it does nothing (and does not reset
  through Atum).
- **Build**: `powershell -ExecutionPolicy Bypass -File .\build.ps1 -Mod all` (local JDK 25, local 26.3 jar,
  no Gradle, no network). `-Mod all` builds WorldPreview before SeedQueue because SeedQueue compiles against it.
- **Test**: `powershell -ExecutionPolicy Bypass -File .\tools\run-smoke.ps1 -Seconds 90 -ModsSource .\dist`.
- **Benchmark**: press `B` on the wall (`benchmarkResets` 1–2000). On the developer's PC (i9-12900HX +
  RTX 4060 Laptop): **2.07 RPS at `chunkRadius = 2`**, **1.57 RPS at `chunkRadius = 4`**.
- **License**: MIT — see [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for upstream credits.
