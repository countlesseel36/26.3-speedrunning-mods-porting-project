# 26.3 Speedrun Mods — v0.3.2

## 警告 / WARNING

**WARNING（警告）** — 这些模组由社区自行移植，未获 speedrun.com 官方承认；测试版本，vibe coding 产物，可能存在重大 bug。
/ Unofficial ports, **not recognised by speedrun.com**; test builds, vibe-coded, major bugs may exist.

**WARNING（显存 / VRAM）** — 队列每个实例都会占用渲染目标与显存，请把队列控制在 6 个以内。
/ Every queued instance owns a render target and VRAM; keep the queue at 6 or fewer.

## 包含的模组 / Included mods

| 文件 / File | 版本 / Version |
| --- | --- |
| `0.9.0-rc.3-disabled-benchmark.1+26.3-SeedQueue.jar` | 0.9.0-rc.3-disabled-benchmark.1 |
| `6.3.0-rc.1-build.1+26.3-WorldPreview.jar` | 6.3.0-rc.1-build.1 |
| `2.1.0-rc.2-disabled-benchmark.1+26.3-Speedrun-API.jar` | 2.1.0-rc.2-disabled-benchmark.1 |
| `2.3.0-rc.2-build.1+26.3-StandardSettings.jar` | 2.3.0-rc.2-build.1 |
| `1.4.0-rc.2-build.1+26.3-Atum.jar` | 1.4.0-rc.2-build.1 |
| `1.6.0-rc.1-build.1+26.3-FastReset.jar` | 1.6.0-rc.1-build.1 |

## 更新内容（相对 v0.3.1）/ Changes since v0.3.1

### 重大修复 / Critical fixes

- 修复显存泄漏：`displayTarget` / `renderTarget` 在丢弃实例时未释放，且 `destroyBuffers()` 在准备线程调用会抛 "Rendersystem called from wrong thread"（等于没释放）。现在改在渲染线程释放，压测 200+ 次重置不再出现 `GpuOutOfMemoryException`。
  / Fixed the VRAM leak: both per-instance render targets were never freed, and `destroyBuffers()` on the prepare thread threw "Rendersystem called from wrong thread". They are now freed on the render thread; 200+ resets no longer hit `GpuOutOfMemoryException`.
- 修复鼠标绑定失效：上一版把键位按数字序列化，鼠标键（负值）被当成键盘键重建，导致所有鼠标操作失灵。现已回退为名称存储，并修正了被写坏的 `standardsettings.json` / `options.txt`。
  / Fixed broken mouse bindings: numeric key serialisation recreated mouse keys as keyboard keys. Storage is name-based again, and the corrupted config files were repaired.
- 修复 `damage_event` 编码失败导致的突然掉线：跨服务端的伤害类型无法编码，现按当前服务端注册表校验并丢弃非法包。
  / Fixed the abrupt disconnect from `damage_event`: cross-server damage types cannot be encoded, so packets are validated against the current registry and dropped when invalid.
- 修复改键在重启后恢复默认：`save()` 曾在 `load()` 之前执行，把默认键位覆盖到配置里。
  / Fixed rebinds reverting after restart: `save()` ran before `load()` and wrote defaults over the file.

### 配置界面 / Config UI

- 新增置顶大类「全局设置」：使用 StandardSettings（黄色、第一行）、全部切换、Pie 目录、视场角；
- 「进世界时暂停 / 首次进世界暂停延迟 / 进世界时视场角」归入「进入世界时设置」；
- 调试渲染开关（3d crosshair、Chunk borders、Chunk section *、Entity hitboxes、Visualize *）并入「调试」；
- 分组标题贴近选项名、统一由一个 key 生成（修复重复标题与未翻译的 `speedrunapi.group.accessibility`）；
- 选项顺序改为**语言无关**（中英文行序一致，按英文顺序）；键位与调试项保持原版注册顺序；
- 跑分警告与显存警告改为红色文字。
  / New pinned "Global" group; pause/FOV-on-join options moved to "On World Join"; debug render toggles merged into "Debug"; headings next to labels with a single key each; language-independent ordering (English order); red warning text.

### 其他 / Other

- Atum 在 SeedQueue 存在时禁用金靴子的单实例重开（Shift+左键仍可打开设置）；
- F6 可从暂停界面（含 F3+Esc 暂停）返回墙界面。
  / Atum's single-instance reset is disabled while SeedQueue is installed (Shift+click still opens settings); F6 returns to the wall from the pause screen.

## 致谢 / Credits

感谢 **ttkuilei** 的实测与问题反馈——本版多数修复（改键持久化、鼠标绑定、配置页分组/顺序、显存泄漏与掉线）都由其测试报告定位。
/ Thanks to **ttkuilei** for the testing and reports behind most of these fixes.

## 已知问题 / Known issues

1. 未修复：第三人称下玩家皮肤消失 / Not fixed: no player skin in third-person cards
2. 未修复：视角远景仍为黑色 / Not fixed: far distance still black
3. 未修复：实例偶尔地形闪烁 / Not fixed: occasional terrain flickering
4. 跑分功能有重大 bug，已暂时停用 / Benchmarking disabled because of a major bug

## 安装 / Install

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议 Sodium 0.9.3）
2. 6 个 jar 放入 `mods/`，先删除旧版本
