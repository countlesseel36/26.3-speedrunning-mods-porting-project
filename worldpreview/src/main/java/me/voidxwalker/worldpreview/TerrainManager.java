package me.voidxwalker.worldpreview;

import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static me.voidxwalker.worldpreview.WorldPreview.*;

/**
 * 预览地形：区块/段跟踪、Sodium 地形准备与完成判定。
 * 从 WorldPreview 拆分出来，行为保持不变。
 */
final class TerrainManager {
   private TerrainManager() {
   }

   public static void invalidateCompiledSections() {
      // sections are seeded again on every frame, nothing to invalidate
   }
   public static void populateVisibleSections(Minecraft minecraft) {
      if (!isActive()) {
         return;
      }

      var area = minecraft.levelRenderer.viewArea();
      if (area == null) {
         return;
      }

      SodiumCompat.pumpChunkEvents();

      SectionPos camera = SectionPos.of(minecraft.gameRenderer.mainCamera().position());
      area.repositionCamera(camera);

      var visible = minecraft.levelRenderer.visibleSections();
      minecraft.levelRenderer.clearVisibleSections();
      // The preview level only holds radius+3 chunks, so a 4-section radius is plenty. The old radius
      // of 8 walked 1445 sections per frame and allocated a BlockPos for every lookup.
      int radius = Math.min(4, area.getViewDistance());
      var mutablePos = new net.minecraft.core.BlockPos.MutableBlockPos();
      int added = 0;
      int seeded = 0;
      boolean dirtyOk = !SodiumCompat.PRESENT && recentered && hasSectionTracker(minecraft);
      boolean sweep = dirtyOk && populateTicks % 20 == 0;
      int sweepBudget = sweep ? 48 : 0;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            for (int dy = -2; dy <= 2; dy++) {
               RenderSection section = area.getRenderSectionAt(
                  mutablePos.set((camera.x() + dx) << 4, (camera.y() + dy) << 4, (camera.z() + dz) << 4)
               );
               if (section == null) {
                  continue;
               }

               visible.add(section);
               added++;

               if (!dirtyOk) {
                  continue;
               }

               if (seedEmptyMesh(section)) {
                  seeded++;
                  minecraft.levelExtractor.setSectionDirty(camera.x() + dx, camera.y() + dy, camera.z() + dz);
               } else if (sweepBudget > 0 && isEmptyMesh(section)) {
                  // recover sections whose compile got cancelled without rebuilding everything
                  minecraft.levelExtractor.setSectionDirty(camera.x() + dx, camera.y() + dy, camera.z() + dz);
                  sweepBudget--;
               }
            }
         }
      }

      if (++populateTicks % 40 == 0) {
         int withDraw = 0;
         int uncompiled = 0;
         int empty = 0;
         int other = 0;

         for (var section : visible) {
            try {
               Object mesh = section.getSectionMesh();
               if (mesh == CompiledSectionMesh.UNCOMPILED) {
                  uncompiled++;
               } else if (mesh == CompiledSectionMesh.EMPTY) {
                  empty++;
               } else {
                  other++;
                  Object draw = mesh.getClass()
                     .getMethod("getSectionDraw", net.minecraft.client.renderer.chunk.ChunkSectionLayer.class)
                     .invoke(mesh, net.minecraft.client.renderer.chunk.ChunkSectionLayer.SOLID);
                  if (draw != null) {
                     withDraw++;
                  }
               }
            } catch (Throwable ignored) {
            }
         }

         LOGGER.info("[preview] sodium: {}", SodiumCompat.stats());
         LOGGER.info(
            "[preview] sections: visible={}, seeded={}, uncompiled={}, empty={}, other={}, withDraw={}",
            visible.size(),
            seeded,
            uncompiled,
            empty,
            other,
            withDraw
         );
      }
   }
   static boolean isEmptyMesh(RenderSection section) {
      try {
         return section.getSectionMesh() == CompiledSectionMesh.EMPTY;
      } catch (Throwable t) {
         return false;
      }
   }
   static boolean hasSectionTracker(Minecraft minecraft) {
      try {
         if (sectionTrackerField == null
            || sectionTrackerField.getDeclaringClass() != minecraft.levelExtractor.getClass()) {
            sectionTrackerField = minecraft.levelExtractor.getClass().getDeclaredField("sectionUpdateTracker");
            sectionTrackerField.setAccessible(true);
         }

         return sectionTrackerField.get(minecraft.levelExtractor) != null;
      } catch (Throwable t) {
         return false;
      }
   }
   public static void setupSodiumTerrain(Minecraft minecraft) {
      if (!isActive() || !spawnFinal) {
         return;
      }

      SodiumCompat.setupTerrain(minecraft.gameRenderer.mainCamera());
   }
   public static void resetPreviewProgress() {
      readyFrames = 0;
   }
   public static void noteChunksApplied(int applied, int expected) {
      previewApplied = applied;
      previewExpected = expected;
   }
   public static void maybeComplete(Minecraft minecraft) {
      if (!isActive()) {
         return;
      }

      boolean ready = SodiumCompat.PRESENT ? SodiumCompat.visibleSections() > 0 : hasCompiledSection(minecraft);

      if (previewApplied >= previewExpected && ready) {
         readyFrames++;
      }

      if (readyFrames == 9) {
         if (Boolean.getBoolean("worldpreview.smoke")) {
            net.minecraft.client.Screenshot.grab(minecraft, false);
         }

         previewShownAt = System.currentTimeMillis();
         LOGGER.info(
            "[preview] preview rendered: chunks={}/{}, sodiumVisible={}",
            previewApplied,
            previewExpected,
            SodiumCompat.visibleSections()
         );
      }

      // The loading screen only closes once the real level renders, so hand over after a short
      // moment of rendered terrain instead of waiting for the screen to close by itself.
      if (readyFrames > 20 && Boolean.getBoolean("worldpreview.earlyhandover")) {
         if (Boolean.getBoolean("worldpreview.closeloading")
            && minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) {
            minecraft.gui.setScreen(null);
            LOGGER.info("[preview] closed the loading screen at handover");
         }

         LOGGER.info("[preview] preview complete: handing over to the real level");
         clear();
      }
   }
   static boolean hasCompiledSection(Minecraft minecraft) {
      for (var section : minecraft.levelRenderer.visibleSections()) {
         Object mesh = section.getSectionMesh();
         if (mesh != null && mesh != CompiledSectionMesh.UNCOMPILED && mesh != CompiledSectionMesh.EMPTY) {
            return true;
         }
      }

      return false;
   }
   static boolean ensureSectionTracker(Minecraft minecraft) {
      if (hasSectionTracker(minecraft)) {
         return true;
      }

      try {
         minecraft.levelExtractor.allChanged();
      } catch (Throwable t) {
         LOGGER.warn("[preview] failed to recreate section tracker", t);
         return false;
      }

      return hasSectionTracker(minecraft);
   }

   static boolean seedEmptyMesh(RenderSection section) {
      try {
         var field = section.getClass().getField("sectionMesh");
         @SuppressWarnings("unchecked")
         var ref = (AtomicReference<Object>)field.get(section);
         if (ref.get() == CompiledSectionMesh.UNCOMPILED) {
            ref.set(CompiledSectionMesh.EMPTY);
            return true;
         }
      } catch (Throwable t) {
         LOGGER.warn("[preview] failed to seed empty mesh", t);
      }

      return false;
   }
}
