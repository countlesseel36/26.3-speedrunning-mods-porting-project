# 26.3 Speedrun Mods — v0.5.1

## 警告 / WARNING

**WARNING（测试版本）** — 社区自行移植，未获 speedrun.com 官方承认；vibe coding 产物，可能存在重大 bug。
/ Unofficial community ports, not recognised by speedrun.com; test builds, vibe-coded.

## 相对 v0.5.0 的变化 / Changes since v0.5.0

### 修复 / Fixes

- **使用矛时掉线**：`minecraft:spear` 等伤害类型的 id 不在连接的注册表快照里，发包时抛 `EncoderException` 直接踢人。现在统一把 `damage_event` 改写成 `minecraft:generic` 后再发送，受击动画与音效保留。
  / **Disconnect when attacking with a spear**: damage types such as `minecraft:spear` are missing from the connection's registry snapshot, so encoding threw and kicked the player. Damage events are now rewritten to `minecraft:generic` before sending; hit animation and sound are preserved.

### 版本 / Version

- SeedQueue：`1.0.0-rc.1` → `1.0.0-rc.2`；其余模组版本不变。

## 包含的模组 / Included mods

| 文件 / File | 版本 / Version |
| --- | --- |
| `1.0.0-rc.2+26.3-SeedQueue.jar` | 1.0.0-rc.2 |
| `2.1.0-rc.3+26.3-Speedrun-API.jar` | 2.1.0-rc.3 |
| `2.3.0-rc.2+26.3-StandardSettings.jar` | 2.3.0-rc.2 |
| `6.3.0-rc.1+26.3-WorldPreview.jar` | 6.3.0-rc.1 |
| `1.4.0-rc.2+26.3-Atum.jar` | 1.4.0-rc.2 |
| `1.6.0-rc.1+26.3-FastReset.jar` | 1.6.0-rc.1 |

## 已知问题 / Known issues

1. 第三人称下玩家皮肤不显示 / no player skin in third-person previews
2. 远景仍为黑色 / far distance still black
3. 偶尔地形闪烁 / occasional terrain flickering

## 安装 / Install

1. Minecraft 26.3 + Fabric Loader 0.19.5+（建议 Sodium 0.9.3）
2. 6 个 jar 放入 mods/，先删除旧版本
