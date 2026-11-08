# 26.3 Speedrun Mods（非官方移植 / Unofficial port）

> ⚠️ 本项目由社区自行移植，未获 speedrun.com 官方承认；使用这些 mod 的速通记录无法通过审核。
> ⚠️ Unofficial community port, **not recognised by speedrun.com**; runs made with these mods will not pass verification.

> 测试版本、vibe coding 产物，可能出现重大 bug。/ Test builds, products of vibe coding; expect bugs.

将 1.16.2+ 速通常用的 6 个 mod 移植到 Minecraft 26.3（Fabric Loader 0.19.5+，JDK 25）。
/ Ports of six 1.16.2+ speedrunning mods to Minecraft 26.3 (Fabric Loader 0.19.5+, JDK 25).

## 包含的模组 / Included mods

| 模组 / Mod | 版本 / Version | 说明 / Description |
| --- | --- | --- |
| SeedQueue | `0.9.0-gamma.70+26.3` | 后台生成世界 + 墙界面预览 / Background world generation + wall previews |
| WorldPreview | `6.3.0-gamma.2+26.3` | 预览渲染管线 / Preview rendering pipeline |
| SpeedrunAPI | `2.1.0-gamma.4+26.3` | 配置界面框架 / Config screen framework |
| StandardSettings | `2.3.0-gamma.3+26.3` | 进世界应用预设选项 / Applies preset options on world join |
| Atum | `1.4.0-gamma.2+26.3` | 自动重置 / Auto reset |
| FastReset | `1.6.0-gamma.1+26.3` | 秒退到标题 / Instant quit to title |

可选 / Optional: `SeedQueue-LockedPool.zip`（锁定池资源包 / locked-pool resource pack）

## 特性 / Features

- 墙界面预览满幅无缝拼接，卡片内部显示进度文字
  / Seamless full-bleed wall previews with the progress text drawn inside the card
- 点击卡片瞬间进入已生成世界，落点与卡片预览一致
  / Clicking a card joins the generated world instantly, landing exactly where the card showed
- 鼠标操作：左键进入、右键锁定/解锁、中键丢弃并补位
  / Mouse controls: left = play, right = lock/unlock, middle = discard and refill
- 队列世界继承 Atum 的生成建筑、奖励箱与难度
  / Queued worlds inherit Atum's structures, bonus chest and difficulty
- 只在开墙时补新世界，游玩中不后台生成
  / The queue only refills while the wall is open; nothing generates in-game
- 配置页单页滚动、分类标题、悬停说明，键盘与鼠标均可改键，中英文界面
  / Single scrollable config page with headings, tooltips, rebindable keyboard/mouse keys, Chinese and English UI

## 已知问题 / Known issues

1. 第三人称卡片仍可能显示不完整（预览只覆盖出生点周围区块）
   / Third-person cards can still look incomplete (the preview only covers the chunks around spawn)
2. 第三人称卡片不显示玩家皮肤
   / No player skin in third-person cards
3. 关闭窗口后需要 watchdog 约 4 秒结束进程
   / The process still needs ~4s watchdog after closing the window

## 安装 / Install

1. 安装 Fabric Loader 0.19.5+（Minecraft 26.3）
   / Install Fabric Loader 0.19.5+ for Minecraft 26.3
2. 把 6 个 jar 放入 `mods/`，先删除旧版本
   / Put the six jars into `mods/`, deleting older versions first
3. 建议同时安装 Sodium 0.9.3
   / Sodium 0.9.3 is recommended
4. 首次启动生成 `config/seedqueue.json` 与 `config/mcsr/` 配置
   / The first launch creates `config/seedqueue.json` and the files under `config/mcsr/`

## 构建 / Build

```powershell
cd 26.3_speedrun_mods_ports
powershell -File .\build.ps1 -Mod all
```

产物在 `dist/`，历史构建在 `dist/past-build/<Mod>/`。
/ Artifacts go to `dist/`; older builds are kept in `dist/past-build/<Mod>/`.

## 性能调优 / Performance tuning

- 6 个队列世界常驻时建议 4G 堆 + G1，并开启并行引用处理
  / Six queued servers keep a lot of heap alive: use a 4G heap with G1 and parallel reference processing

```
-Xms4G -Xmx4G -XX:+UseG1GC -XX:MaxGCPauseMillis=30 -XX:+ParallelRefProcEnabled
```

- 预览默认隔帧渲染（`-Dseedqueue.previewEveryNthFrame`）
  / Previews render every other frame by default (`-Dseedqueue.previewEveryNthFrame`)
- 补卡检查默认每 10 tick 一次（`-Dseedqueue.refillIntervalTicks`）
  / Refill checks run every 10 ticks (`-Dseedqueue.refillIntervalTicks`)
- 预览 chunk builder 线程上限 2（`-Dseedqueue.previewWorkers`）
  / Preview chunk builder threads are capped at 2 (`-Dseedqueue.previewWorkers`)

## 说明 / Notes

- 源码来自各 mod 原作者，本仓库只为 26.3 适配层与必要修补
  / Sources belong to the original authors; this repository only holds the 26.3 adaptation layer and fixes
- 许可证沿用上游 / Licences follow upstream
- 提交 issue 请附 `logs/latest.log` / Attach `logs/latest.log` when reporting issues
