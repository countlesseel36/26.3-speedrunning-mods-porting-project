# SeedQueue 26.3 wall preview (0.7.0)

> **已过时（2026-11-04，0.9.0）**：墙卡片已改为 26.3 原生管线 3D 实时预览 + 活服务端零加载交接，
> 见 [seedqueue-3d-wall.md](seedqueue-3d-wall.md)。本文仅保留 0.7.0 的截图/CPU 方案记录。

Every queued instance shows a **static spawn-view screenshot of the real game screen**, and
is frozen after generation until the player clicks it.

## Screenshot capture (primary, default on)

`SeedQueue.tickScreenshotPreview` (property `seedqueue.shotPreview`, default `true`):

1. pick a `READY` entry without a card image;
2. freeze it (`QueueEntry.releaseForScreenshot()`: save + halt the background server, close
   stem/access) so the world folder is unlocked;
3. after ~2 s open the world (`createWorldOpenFlows().openWorld(folder, …)`);
4. once the world is loaded: close the loading/pause screen, hide the HUD and chat, apply
   the configured camera (`perspective`), wait 30 ticks;
5. `Screenshot.grab` → read the screenshot → letterbox the **whole frame** into a 320x138
   thumbnail (no cropping) → upload as the entry's `DynamicTexture`;
6. `disconnect`, restore HUD/chat/camera/pause options, reopen the wall;
7. the entry stays `frozen`; clicking it loads the world from disk.

Captured thumbnails are real game frames (terrain, sky, water, textures), not the CPU
renderer; the card image is static and is not refreshed.

## CPU fallback (off by default)

`-Dseedqueue.cpuPreview=true` re-enables the 0.5.0 CPU ray-marched renderer
(`SpawnViewRenderer` + `BlockTextureColors`) for cases where opening the world is not
desirable. It is not the default and not what the wall shows normally.

## Freeze

`SeedQueue.freezeReadyEntries` freezes an entry ~800 ms after it becomes `READY`
(`-Dseedqueue.freeze=false` disables this). A frozen entry has no background server and
waits for the player's click; joining loads it from disk.

## Notes and limitation

- The picture is taken when the instance world is first displayed (during the load it is
  opened for this screenshot), not from WorldPreview's background generation preview.
  WorldPreview's own preview only renders during the world-creation flow; for the queue's
  background servers there is no client-side preview render, and 26.3's frame graph does
  not expose preview-world pixels for readback (see the archive README for the failed GPU
  paths).
- Because the entry is frozen first and then opened once for the screenshot, a card costs
  one world load (~3–4 s); afterwards the world is frozen on disk until the player joins.
