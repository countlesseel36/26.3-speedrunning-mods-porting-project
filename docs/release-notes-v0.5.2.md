# 26.3 Speedrun Mods — v0.5.2

## 警告 / WARNING

**WARNING（测试版本）** — 社区自行移植，未获 speedrun.com 官方承认；vibe coding 产物，可能存在重大 bug。
/ Unofficial community ports, not recognised by speedrun.com; test builds, vibe-coded.

## 相对 v0.5.1 的变化 / Changes since v0.5.1

- **伤害类型改为黑名单处理**：只有`minecraft:spear` 这类已知无法编码的类型会被改写为 `minecraft:generic`，其余伤害类型原样发送，火焰等专属音效恢复；矛不再导致掉线。
  / Damage types now use a blacklist: only known-unencodable types such as `minecraft:spear` are rewritten to `minecraft:generic`; everything else is sent unchanged, so fire and other dedicated sounds are back.
- SeedQueue 版本：`1.0.0-rc.2` → `1.0.0-rc.3`；其余模组不变。

## 包含的模组 / Included mods

| 文件 / File | 版本 / Version |
| --- | --- |
| `1.0.0-rc.3+26.3-SeedQueue.jar` | 1.0.0-rc.3 |
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
