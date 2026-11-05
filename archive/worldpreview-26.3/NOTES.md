# WorldPreview 26.3 — archived module

Archived 2026-11-03. WorldPreview development is shelved because world loads are already fast
enough; it is no longer part of the default build and is not installed in the instance.

## Contents

- `module/` — full source tree as it was at archive time (`worldpreview/` in the project root).
- `test_6.3.1-beta.3+26.3-WorldPreview.jar` — last built jar (25 KB, 26.3 + Fabric loader 0.19.5).
- `screenshots/` — smoke-test evidence:
  - `worldpreview-preview-spawn.png` — last preview frame; F3 XYZ matches the real spawn.
  - `worldpreview-preview-terrain.png` — earlier 13x13 preview with terrain.
  - `worldpreview-preview-sky.png` — early blank/sky-only stage.

## State at archive time

Working:
- Preview renders terrain for a 2-chunk render distance (5x5 chunks) around the spawn point and ends
  as soon as those chunks are rendered, so the loading screen closes promptly.
- Preview camera follows the real player spawn (verified: F3 XYZ == server login position).
- Loading timing matches the no-preview baseline (~3-4 s from "Starting integrated" to login).

Known/open:
- 4th reported bug (player skin not rendering in-world) was **not** fixed; the offline smoke
  environment could not reproduce it (identical default-Alex skin with the mod enabled and disabled).
  Diagnostics remain in `module/` (`[preview] player skin:` log line in `WorldPreview.clear()`).
- The vanilla chunk-progress map overlay is still drawn over the preview.
- Only a small area is previewed; the rest of the world is out of view by design.

## Restoring it

1. Move `module/` back to `<project>/worldpreview`.
2. Restore the build entry: add `'worldpreview'` to the `$mods` list and to the `ValidateSet` in
   `build.ps1`.
3. Build with `powershell -ExecutionPolicy Bypass -File .\build.ps1 -Mod worldpreview`.
4. Copy `dist/test_6.3.1-beta.3+26.3-WorldPreview.jar` into `minecraft/mods` if you want to test it.

Notes on the 26.3 renderer (learned while porting, kept here for any future attempt):

- `LevelExtractor#setLevel` must only be called when the target level actually changes: it calls
  `allChanged()`, which invalidates compiled geometry and clears the visible-section list.
- Sections are compiled by seeding `CompiledSectionMesh.EMPTY` into `RenderSection#sectionMesh`
  (bypasses the `hasAllNeighbors` gate) and marking them dirty via `LevelExtractor#setSectionDirty`.
- Sodium needs `ChunkTracker` events pumped (`processChunkEvents`) plus chunk light flags and
  `scheduleRebuildForChunk` to build geometry for the preview world.
- Chunk packets must be serialised on the server thread, in small batches; serialising them while
  the server is still in `Preparing spawn area` blocks the server thread and stalls loading.
