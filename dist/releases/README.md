# 发布归档 / Release archive

`dist/releases/<版本>/` 保存每次 GitHub Release 的完整附件，便于离线复现与回滚。
`dist/releases/<version>/` keeps the exact assets of every GitHub Release.

| 版本 | 发布页 | SeedQueue | SpeedrunAPI | StandardSettings | WorldPreview | Atum | FastReset |
| --- | --- | --- | --- | --- | --- | --- | --- |
| v0.5.3 | https://github.com/countlesseel36/26.3-speedrunning-mods-porting-project/releases/tag/v0.5.3 | 1.0.0-rc.4 | 2.1.0-rc.4 | 2.3.0-rc.2 | 6.3.0-rc.1 | 1.4.0-rc.2 | 1.6.0-rc.1 |
| v0.5.2 | https://github.com/countlesseel36/26.3-speedrunning-mods-porting-project/releases/tag/v0.5.2 | 1.0.0-rc.3 | 2.1.0-rc.3 | 2.3.0-rc.2 | 6.3.0-rc.1 | 1.4.0-rc.2 | 1.6.0-rc.1 |
| v0.5.1 | https://github.com/countlesseel36/26.3-speedrunning-mods-porting-project/releases/tag/v0.5.1 | 1.0.0-rc.2 | 2.1.0-rc.3 | 2.3.0-rc.2 | 6.3.0-rc.1 | 1.4.0-rc.2 | 1.6.0-rc.1 |
| v0.5.0 | https://github.com/countlesseel36/26.3-speedrunning-mods-porting-project/releases/tag/v0.5.0 | 1.0.0-rc.1 | 2.1.0-rc.3 | 2.3.0-rc.2 | 6.3.0-rc.1 | 1.4.0-rc.2 | 1.6.0-rc.1 |

## 目录约定 / Layout

- `dist/`：当前发布版构建（只有一套 / only the current release set）；
- `dist/releases/<version>/`：历史 Release 附件（含 `SeedQueue-LockedPool.zip`）；
- `dist/past-build/<Mod>/`：开发期间的中间构建（带 build 号）；
- `archive/`：早期实验性快照。

## 版本号规则 / Versioning

- 开发构建：`<base>-build.N`（同一 rc 线内递增，不升 rc）；
- 正式发布：去掉 build 号；若该模组的 build ≠ 1，则 rc 进位（例如 `1.0.0-rc.3-build.37` → `1.0.0-rc.4`）。
