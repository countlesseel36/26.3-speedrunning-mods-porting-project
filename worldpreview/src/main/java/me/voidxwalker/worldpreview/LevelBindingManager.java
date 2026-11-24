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
 * 预览与真实世界的绑定：重绑、解绑、相机与实体释放。
 * 从 WorldPreview 拆分出来，行为保持不变。
 */
final class LevelBindingManager {
   private LevelBindingManager() {
   }

   public static void rebindRealLevel(Minecraft minecraft) {
      keepSodiumBound = false;
      realLevelBound = false;
      ensureRealLevelBound(minecraft);
      SodiumCompat.refreshTerrain();
      releasePreviewCameraAndEntities(minecraft);
      LOGGER.info("[preview] rebind to real level requested (level={})", minecraft.level == null ? "null" : minecraft.level.dimension().identifier());
   }
   public static void releasePreviewCameraAndEntities(Minecraft minecraft) {
      try {
         net.minecraft.world.entity.Entity player = minecraft.player;

         if (player == null) {
            return;
         }

         net.minecraft.client.Camera camera = minecraft.gameRenderer.mainCamera();
         boolean released = false;

         // Camera#setEntity has to be re-run unconditionally: the preview binds the render camera to
         // its own anchor entity directly, and Minecraft#getCameraEntity already reports the real
         // player, so a check on the entity alone missed it and the player kept looking from the
         // preview camera (far above and behind the body) after joining.
         minecraft.setCameraEntity(player);
         camera.setEntity(player);
         released = true;

         net.minecraft.client.multiplayer.ClientLevel level = minecraft.level;

         if (level != null) {
            for (int id = 1; id <= 4; id++) {
               net.minecraft.world.entity.Entity entity = level.getEntity(id);

               if (entity != null
                  && entity != player
                  && entity instanceof net.minecraft.client.player.LocalPlayer
                  && !(entity instanceof net.minecraft.client.player.LocalPlayer real && real == player)) {
                  level.removeEntity(id, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                  released = true;
               }
            }
         }

         if (released) {
            LOGGER.info("[preview] released the preview camera and fake entities");
         }
      } catch (Throwable t) {
         LOGGER.warn("[preview] failed to release the preview camera", t);
      }
   }
   public static void ensureRealLevelBound(Minecraft minecraft) {
      if (properties != null || minecraft.level == null || realLevelBound) {
         return;
      }

      var accessor = (me.voidxwalker.worldpreview.mixin.LevelExtractorAccessor)minecraft.levelExtractor;
      suppressBackground = false;

      if (accessor.worldpreview$getLevel() != minecraft.level) {
         minecraft.levelExtractor.setLevel(minecraft.level);
         SodiumCompat.restore(minecraft);
         LOGGER.info("[preview] renderer rebound to the real level {}", minecraft.level.dimension().identifier());
      }

      realLevelBound = true;
   }
   public static void recenterIfNeeded(Minecraft minecraft) {
      if (!isActive() || recentered || forceLevelRender) {
         return;
      }

      recentered = true;
      minecraft.levelRenderer.resetLevelRenderData();
      minecraft.levelRenderer.invalidateCompiledGeometry(
         properties.level,
         minecraft.options,
         minecraft.gameRenderer.mainCamera(),
         minecraft.getBlockColors()
      );

      if (ensureSectionTracker(minecraft)) {
         minecraft.levelExtractor.allChanged();
      }

      SodiumCompat.refreshTerrain();

      var area = minecraft.levelRenderer.viewArea();
      LOGGER.info(
         "[preview] recentered: viewArea={}, size={}, camera={}, spawn={}",
         area != null,
         area == null ? -1 : area.size(),
         minecraft.gameRenderer.mainCamera().position(),
         properties.spawnPos
      );
   }
   public static void checkRealLevel(Minecraft minecraft) {
      if (properties == null || minecraft.level == null || minecraft.level == properties.level) {
         realLevelFrames = 0;
         return;
      }

      // Once the real level exists its player already carries the authoritative spawn position,
      // so follow it to make the last preview frames match where the player will appear.
      if (minecraft.player != null && minecraft.player != properties.player) {
         syncServerPosition(minecraft.player.position(), minecraft.player.getYRot(), minecraft.player.getXRot());
      }

      ensureRealLevelBound(minecraft);

      // Keep the preview on screen until the real world has generated around the player, then close
      // the loading screen ourselves so the switch goes preview -> world without showing the real
      // (half loaded) world behind the loading screen.
      boolean worldReady = false;

      if (minecraft.player != null && minecraft.player != properties.player) {
         var server = minecraft.getSingleplayerServer();
         var blockPos = minecraft.player.blockPosition();
         worldReady = server != null && server.isReady() && minecraft.level.hasChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
      }

      // Keep the preview on screen for a moment of rendered terrain so it does not just flash by.
      if (worldReady && readyFrames > 60) {
         if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) {
            minecraft.gui.setScreen(null);
            LOGGER.info("[preview] world ready, closed the loading screen");
         }

         LOGGER.info("[preview] world ready, ending preview (preview visible {} ms)", System.currentTimeMillis() - previewShownAt);
         clear();
         return;
      }

      // Give the preview a grace period to render its terrain before giving the client back.
      if (++realLevelFrames > 200) {
         if (readyFrames == 0) {
            LOGGER.warn(
               "[preview] handing over without rendered terrain: chunks={}/{}, sodiumVisible={}, sodiumReady={}, stats={}, spawn={}, camera={}",
               previewApplied,
               previewExpected,
               SodiumCompat.visibleSections(),
               SodiumCompat.readyChunks(),
               SodiumCompat.stats(),
               properties.spawnPos,
               minecraft.gameRenderer.mainCamera().position()
            );
         }

         LOGGER.info("[preview] real level installed, ending preview");
         clear();
      }
   }
   public static void logSkinOnce(Minecraft minecraft) {
      if (minecraft.player == null) {
         return;
      }

      var skin = minecraft.player.getSkin();
      var profile = minecraft.player.getGameProfile();
      LOGGER.info(
         "[preview] player skin: body={}, secure={}, model={}, profile={}, properties={}",
         skin == null ? "null" : skin.body(),
         skin == null ? "null" : skin.secure(),
         skin == null ? "null" : skin.model(),
         profile == null ? "null" : profile.name(),
         profile == null ? -1 : profile.properties().size()
      );
   }
   public static void tickPending(Minecraft minecraft) {
      if (!pending || isActive()) {
         return;
      }

      WorldPreviewProperties built = PreviewBuilder.build(minecraft);
      if (built != null) {
         set(built);
         pending = false;
         LOGGER.info("[preview] preview is active");
      }
   }
}
