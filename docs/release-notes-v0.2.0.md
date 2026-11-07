# 26.3 Speedrun Mods — v0.2.0

> ⚠️ 这些 mod 由中国 1.16.2+ 速通社区自行移植，**未获 speedrun.com 官方承认**；使用它们的速通记录在
> speedrun.com **无法通过审核**。本项目为**测试版本**、**vibe coding 的产物**，可能影响你的速通，请权衡后使用。

## 包含的模组

| 文件 | 版本 |
| --- | --- |
| `0.9.0-gamma.28+26.3-SeedQueue.jar` | 0.9.0-gamma.28 |
| `6.3.0-gamma.1+26.3-WorldPreview.jar` | 6.3.0-gamma.1 |
| `2.1.0-gamma.1+26.3-Speedrun-API.jar` | 2.1.0-gamma.1 |
| `2.3.0-gamma.1+26.3-StandardSettings.jar` | 2.3.0-gamma.1 |
| `1.4.0-gamma.1+26.3-Atum.jar` | 1.4.0-gamma.1 |
| `1.6.0-gamma.1+26.3-FastReset.jar` | 1.6.0-gamma.1 |

## 本版更新（相对 v0.1.3）

### 墙界面

- 预览改为**满幅无缝拼接**：去掉卡片底色、标签条与布局间距，卡片画面直接邻接；
- 卡片状态文字（`generating` / `ready` 与区块计数）默认隐藏，由「显示区块加载进度」控制；
- 打开「显示调试菜单」后，墙左上角显示 `SeedQueue 4/6 | generating 0`、`Heap … MiB | M …%` 与状态行；
- 预览未就绪时**禁止进入世界**，进入后清理残留的标题/加载界面；
- 预览渲染距离限制为"生成半径 + 2"，不再按原版渲染距离渲染大片空区块。

### 队列与世界生成

- 队列世界**继承 Atum 设置**：生成建筑、奖励箱、难度；
- **世界内不生成新世界**，只在墙界面补卡；回墙立即恢复；
- 重生/换维度时释放预览绑定与显存，唤醒冻结服务端时重置 tick 计时（不再出现 `Can't keep up`）；
- 修复"prepare 中的世界被删除"导致的 `NoSuchFileException`；
- Sodium manager 改为逐帧回收（同帧批量销毁会触发 256 MiB arena 分配失败崩溃）；
- 「模拟窗口大小」接入预览目标分配，默认 1280×720。

### 配置界面（SpeedrunAPI / StandardSettings）

- 配置页改为**单页滚动** + 分类标题 + 右侧滚动条（可拖动）；
- 选项按类别聚拢：队列/墙/性能/进阶/线程/按键/视频/聊天/声音/鼠标/皮肤/辅助功能/调试；
- StandardSettings 所有非调试项完成中文化；
- 键位行可直接改键，**支持鼠标按键**，未绑定显示「未指定」；
- 悬停显示选项说明；修复进入配置页闪退、点击被忽略、标题色透明等问题；
- StandardSettings 不再每次进世界做全量资源重载（进入更顺）。

### 按键

- 墙界面：「返回墙界面」可单独改键（默认 F6）；
- **Atum 检测到 SeedQueue 时禁用重置键与单世界重开**，避免与 SeedQueue 抢 F6；
- 改键页「Quit Wall Screen」允许绑定 Esc；未绑定时 Esc 不再关闭墙；
- 鼠标默认：左键进入、右键锁定/解锁、中键丢弃并补位。

### 稳定性

- 修复退出游戏时崩溃/挂起（退出过程跳过 GPU 资源清理，显式停止后台服务端）；
- 修复配置页 NPE 闪退、语言文件被写坏导致整体回退英文等问题。

## 已知问题

1. **仍未能正确显示第三人称下玩家皮肤**——预览连接没有玩家列表，皮肤解析拿不到真实 `PlayerInfo`；
2. **预览中远景仍是黑色**——超出已生成区块的范围只能显示清屏色，尚未填成天空色。

## 安装

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议同时装 Sodium 0.9.3 与 Fabric API）；
2. 把 6 个 jar 放入 `mods/`，**先删除旧版本**；
3. 首次启动会生成 `config/seedqueue.json` 与 `config/mcsr/` 下的配置。

## 实测

开发者机器（i9-12900HX + RTX 4060 Laptop）：`chunkRadius = 2` 时 **2.50 RPS**，`chunkRadius = 4` 时 **1.90 RPS**。

---

## English summary

**v0.2.0** — Unofficial 26.3 ports; **not endorsed by speedrun.com**, test builds only.

Wall: seamless full-bleed previews, card status text hidden by default, debug HUD on the wall, joining is
blocked until the preview exists, preview render distance limited to the generated area.

Queue: queued worlds inherit Atum's structures/bonus chest/difficulty; the queue only refills while the wall
is open; preview GPU state is released on respawn and parked servers no longer report "Can't keep up"; fixed
the prepare-time world deletion error and the Sodium arena OOM crash; preview resolution defaults to 1280×720.

Config: single scrollable page with category headings, draggable scrollbar, grouped and fully translated
options (Chinese), rebindable keys including mouse buttons, "unbound" shown like vanilla, tooltips, and no
more full resource reload on every world join.

Keys: rebindable "back to wall" key (F6 by default); Atum's reset key/reset feature is disabled while
SeedQueue is present; Escape handling fixed.

Stability: clean shutdown, no config-screen crashes.

**Known issues**: no player skin in third-person cards; far distance in previews is still black.
