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
 * 预览生命周期：状态/属性、出生点同步、创建与销毁外部预览世界。
 * 从 WorldPreview 拆分出来，行为保持不变。
 */
final class PreviewLifecycleManager {
   private PreviewLifecycleManager() {
   }

   public static boolean isActive() {
      return properties != null;
   }

   public static boolean isBuilding() {
      return building;
   }

   public static void setBuilding(boolean value) {
      building = value;
   }

   public static boolean hasSpawn() {
      return properties != null && spawnAccepted;
   }

   public static WorldPreviewProperties properties() {
      return properties;
   }

   public static void set(WorldPreviewProperties previewProperties) {
      spawnFinal = false;
      realLevelBound = false;
      properties = previewProperties;
   }

   public static void abort() {
      if (properties != null) {
         LOGGER.info("[preview] aborted by reset request");
      }

      pending = false;
      creatingFreshLevel = false;
      clear();
   }

   public static void clear() {
      if (properties != null) {
         Minecraft minecraft = Minecraft.getInstance();
         LOGGER.info(
            "[preview] clearing preview: spawn={}, camera={}, player={}",
            properties.spawnPos,
            minecraft.gameRenderer.mainCamera().position(),
            properties.player.position()
         );
      } else {
         LOGGER.info("[preview] clearing preview");
      }
      if (!keepSodiumBound) {
         SodiumCompat.restore(Minecraft.getInstance());
      }

      Minecraft instance = Minecraft.getInstance();
      if (instance.player != null) {
         logSkinOnce(instance);
      }
      properties = null;
      recentered = false;
      spawnAccepted = false;
      PreviewBuilder.resetChunks();
   }

   public static boolean updateSpawn(BlockPos spawnPos, boolean valid) {
      if (properties == null || !valid) {
         return false;
      }

      boolean movedFar = !spawnAccepted || properties.spawnPos.distSqr(spawnPos) > 64.0 * 64.0;
      spawnAccepted = true;
      properties.spawnPos = spawnPos;
      properties.player.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
      properties.player.setNoGravity(true);
      properties.player.setOldPosAndRot();
      properties.player.setDeltaMovement(0.0, 0.0, 0.0);
      if (movedFar) {
         recentered = false;
         LOGGER.info("[preview] accepted spawn {}", spawnPos);
         return true;
      }

      return false;
   }

   public static void syncServerPosition(net.minecraft.world.phys.Vec3 position, float yRot, float xRot) {
      if (properties == null) {
         return;
      }

      WorldPreview.LOGGER.info("[preview] position packet at {}", position);
      markSpawnFinal("server position packet");
      BlockPos newPos = BlockPos.containing(position.x, position.y, position.z);
      boolean movedFar = !spawnAccepted || properties.spawnPos.distSqr(newPos) > 32.0 * 32.0;
      properties.spawnPos = newPos;
      properties.player.setPos(position);
      properties.player.setYRot(yRot);
      properties.player.setXRot(xRot);
      properties.player.setOldPosAndRot();
      properties.player.setNoGravity(true);
      properties.player.setDeltaMovement(0.0, 0.0, 0.0);
      spawnAccepted = true;

      if (movedFar) {
         recentered = false;
         PreviewBuilder.resetChunks();
         LOGGER.info("[preview] synced spawn from server position {}", position);
      }
   }

   public static void setCreatingFreshLevel(boolean value) {
      creatingFreshLevel = value;
      // Before the preview is ready the vanilla loading panorama stays visible; the background is
      // only hidden while the preview is on screen (and for the short hand-over afterwards).
      hideBackgroundAfterPreview = false;
      LOGGER.info("[preview] createFreshLevel flag={}", value);
   }

   public static boolean hasFinalSpawn() {
      return spawnFinal;
   }

   public static boolean shouldHideBackground() {
      return hideBackgroundAfterPreview || suppressBackground || isActive() && hasFinalSpawn();
   }

   public static void markSpawnFinal(String source) {
      if (!spawnFinal) {
         spawnFinal = true;
         suppressBackground = false;
         LOGGER.info("[preview] spawn finalized via {}", source);
      }
   }

   public static void onLoadingScreenClosed() {
      hideBackgroundAfterPreview = false;
   }

   public static void activateExternal(WorldPreviewProperties props) {
      externalCapture = true;
      set(props);

      // Do not reposition the player/camera here: the external preview already placed them (the
      // ExternalPreview surface adjustment). updateSpawn() would reset them to the raw respawn
      // point on every wall switch, making the card jump after it finished loading.
      if (properties != null) {
         properties.spawnPos = props.spawnPos;
      }

      spawnAccepted = true;
      markSpawnFinal("external instance");
      // The external preview does its own Sodium/level setup; never invalidate geometry in-frame.
      recentered = true;
      forceLevelRender = true;
   }

   public static void deactivateExternal() {
      externalCapture = false;
      forceLevelRender = false;
      instanceRendering = false;
      recentered = false;
      clear();
   }

   public static boolean isWorldDebugLine(String line) {
      if (line == null) {
         return false;
      }

      return line.contains("XYZ")
         || line.contains("Block:")
         || line.contains("Chunk:")
         || line.contains("Facing:")
         || line.contains("Biome:")
         || line.contains("Section-relative")
         || line.contains("minecraft:")
         || line.contains(" FC:")
         || line.startsWith("C: ")
         || line.startsWith("E: ");
   }

   public static void onServerPreparingLevels(net.minecraft.server.MinecraftServer server) {
      LOGGER.info("[preview] {} (prepareLevels), creatingFreshLevel={}", BUILD, creatingFreshLevel);
      // Only worlds created from the "create new world" flow should get a preview; loading an
      // existing world must stay untouched.
      if (ENABLED && creatingFreshLevel) {
         creatingFreshLevel = false;

         // 只有 SeedQueue 的"进入队列实例"流程才需要武装预览。手动创建世界时武装预览会把
         // 客户端卡在 "Preparing for world creation..."（实测）。
         if (seedqueueJoinPending()) {
            pending = true;
            LOGGER.info("[preview] fresh world creation detected, preview armed");
         } else {
            LOGGER.info("[preview] fresh world creation outside SeedQueue, preview skipped");
         }
      }
   }

   static boolean seedqueueJoinPending() {
      try {
         Class<?> seedqueue = Class.forName("me.contaria.seedqueue.SeedQueue");
         return (boolean) seedqueue.getMethod("isTransitionPending").invoke(null);
      } catch (Throwable ignored) {
         return false;
      }
   }
}
