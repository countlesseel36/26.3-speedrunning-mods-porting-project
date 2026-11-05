# dist/

发布产物（jar 与资源包 zip）**不进入源码仓库**，而是作为 **GitHub Release** 的附件提供：

> Releases 页面 → 最新版本 → Assets → 下载全部 6 个 jar + `SeedQueue-LockedPool.zip`

本地构建会直接生成到本目录（`build.ps1`），提交前已被 `.gitignore` 排除：

| 文件 | 说明 |
| --- | --- |
| `0.9.0-beta.52+26.3-SeedQueue.jar` | SeedQueue |
| `6.3.0-beta.31+26.3-WorldPreview.jar` | WorldPreview |
| `2.1.0-beta.8+26.3-Speedrun-API.jar` | SpeedrunAPI |
| `1.4.0-beta.4+26.3-Atum.jar` | Atum |
| `1.6.0-beta.3+26.3-FastReset.jar` | FastReset |
| `2.3.0-beta.2+26.3-StandardSettings.jar` | StandardSettings |
| `SeedQueue-LockedPool.zip` | 锁定实例独立池资源包 |

`SeedQueue-LockedPool/` 目录是资源包的**源文件**（`pack.mcmeta`、`custom_layout.json`、`lock.png`），
会随源码一起提交；zip 由 `build.ps1` 生成并作为 Release 附件。
