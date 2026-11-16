# 26.3 Speedrun Mods — v0.5.4

> [!WARNING]
> **WARNING（警告）** — 本项目为纯AI（LLM）生成移植，无人工代码审查，无法保证与原版逻辑一致，可能存在未知Bug或改变原版游戏机制。仅供娱乐，未经速通社区（MCSR）合法性认证，严禁用于提交任何排行榜成绩。
>
> / This project is a pure AI (LLM) generated port with no human code review; behaviour may differ from vanilla and unknown bugs or gameplay changes are possible. For entertainment only, not certified by the MCSR community, and must not be used to submit any leaderboard run.

## 相对 v0.5.3 的变化 / Changes since v0.5.3

### 修复 / Fixes

- 实例数设置 10 后重启变回 6：读取配置时上限被硬编码为 6，现改为按墙的 `rows × columns` 收口。
  / Queue size reverted to 6 after restart; the load-time clamp was hardcoded to 6.
- 快速丢弃实例时弹出「无法保存 XXX 处的区块」：服务端 IO 尚未落盘就删除世界目录，现会重试等待文件锁释放，仍占用则留到下次启动清理。
  / "Failed to save chunk" when discarding instances quickly: world folders are now deleted only after their IO finishes.
- 模拟距离设置不生效：该值通过 `ClientInformation` 下发，现在进入世界后会重新发送。
  / Simulation distance now takes effect: the client information packet is re-sent on world join.
- 渲染距离设置不生效：进入世界后重新应用，并再次同步给服务端。
  / Render distance is re-applied after joining a world.
- 聊天可见性、聊天颜色、皮肤部件、主手、粒子状态等同类选项只在启动时生效，现在每次进入世界都会重新应用。
  / Chat visibility, chat colours, skin parts, main hand and particles are now re-applied on every world join.
- 游玩时多个后台服务端并发生成导致偶发卡顿：现在世界里最多只保留 1 个并发生成（回墙后恢复）。
  / Background generation is limited to one instance while playing to reduce stutter.

### 调整 / Changes

- StandardSettings 移除 `fullscreen`、`exclusiveFullscreen`、`fullscreenResolution` 三项，全屏与分辨率完全交还原版设置即时生效。
  / Fullscreen and resolution options were removed from StandardSettings and are handled by vanilla again.

### 版本 / Versions

- SeedQueue `1.0.0-rc.4` → `1.0.0-rc.5`
- StandardSettings `2.3.0-rc.2` → `2.3.0-rc.3`
- SpeedrunAPI / WorldPreview / Atum / FastReset 版本不变。

## 包含的模组 / Included mods

| 文件 / File | 版本 / Version |
| --- | --- |
| `1.0.0-rc.5+26.3-SeedQueue.jar` | 1.0.0-rc.5 |
| `2.1.0-rc.4+26.3-Speedrun-API.jar` | 2.1.0-rc.4 |
| `2.3.0-rc.3+26.3-StandardSettings.jar` | 2.3.0-rc.3 |
| `6.3.0-rc.1+26.3-WorldPreview.jar` | 6.3.0-rc.1 |
| `1.4.0-rc.2+26.3-Atum.jar` | 1.4.0-rc.2 |
| `1.6.0-rc.1+26.3-FastReset.jar` | 1.6.0-rc.1 |

## 已知问题 / Known issues

1. 第三人称下玩家皮肤不显示 / no player skin in third-person previews
2. 远景仍为黑色 / far distance still black
3. 偶尔地形闪烁 / occasional terrain flickering
4. 长时间游玩仍可能出现 GC 引起的偶发卡顿 / occasional GC-induced stutter in long sessions

## 安装 / Install

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议 Sodium 0.9.3）
2. 6 个 jar 放入 mods/，先删除旧版本
