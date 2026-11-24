package me.voidxwalker.worldpreview;

import com.mojang.authlib.GameProfile;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.ItemActivation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import static me.voidxwalker.worldpreview.ExternalPreview.*;

/**
 * 预览区块：应用抓包、失效段与激活段渲染。
 * 从 ExternalPreview 拆分出来，行为保持不变。
 */
final class PreviewSectionHelper {
   private PreviewSectionHelper() {
   }

   public static void applyNewPackets(ExternalPreview preview, java.util.List<ClientboundLevelChunkWithLightPacket> packets) {
      if (packets == null || packets.isEmpty()) {
         return;
      }

      SodiumCompat.setup(preview.level);
      int centerSectionY = preview.spawn.getY() >> 4;

      for (ClientboundLevelChunkWithLightPacket packet : packets) {
         packet.handle(preview.listener);
         SodiumCompat.markLightReady(preview.level, packet.x(), packet.z());
      }

      SodiumCompat.pumpChunkEvents();

      for (ClientboundLevelChunkWithLightPacket packet : packets) {
         SodiumCompat.rebuildColumn(preview.level, packet.x(), packet.z(), centerSectionY);
      }

      SodiumCompat.pumpChunkEvents();
      SodiumCompat.refreshTerrain();
   }

   public static void invalidateSections(ExternalPreview preview) {
      preview.chunksApplied = false;
      preview.packetCursor = 0;
      preview.lastActivationAt = System.currentTimeMillis();
   }

   public static void activateSections(ExternalPreview preview) {
      int centerSectionY = preview.spawn.getY() >> 4;
      // Re-bind Sodium first: the renderer may have been restored to another level in between,
      // and its chunk tracker calls crash when no section manager is bound.
      SodiumCompat.setup(preview.level);

      if (!preview.chunksApplied) {
         preview.level.getChunkSource().updateViewCenter(preview.spawn.getX() >> 4, preview.spawn.getZ() >> 4);
         preview.level.getChunkSource().updateViewRadius(preview.radius + 2);

         // Wall Data Limit: apply at most this many chunk packets per preview update, so a big
         // snapshot does not cause a frame spike when a card is first shown.
         int limit = Math.max(1, Integer.getInteger("seedqueue.wallDataLimit", 100));
         int end = Math.min(preview.packets.size(), preview.packetCursor + limit);

         for (int i = preview.packetCursor; i < end; i++) {
            ClientboundLevelChunkWithLightPacket packet = preview.packets.get(i);
            packet.handle(preview.listener);
            SodiumCompat.markLightReady(preview.level, packet.x(), packet.z());
         }

         SodiumCompat.pumpChunkEvents();

         for (int i = preview.packetCursor; i < end; i++) {
            ClientboundLevelChunkWithLightPacket packet = preview.packets.get(i);
            SodiumCompat.rebuildColumn(preview.level, packet.x(), packet.z(), centerSectionY);
         }

         SodiumCompat.pumpChunkEvents();
         preview.packetCursor = end;

         if (preview.packetCursor >= preview.packets.size()) {
            preview.chunksApplied = true;
            preview.lastActivationAt = System.currentTimeMillis();
            SodiumCompat.refreshTerrain();
         }
      } else {
         // The snapshot is already in the level and Sodium's tracker: on the next rotation just
         // drain queued chunk events instead of re-sending every packet and rebuilding all sections.
         SodiumCompat.pumpChunkEvents();
      }

      // Resolve the exact spawn the server will use (respawn_radius is 0 for queue worlds) and move the
      // preview camera there; the raw respawn suggestion is off by up to the old search radius.
      if (!preview.spawnResolved && preview.chunksApplied) {
         preview.spawnResolved = true;

         BlockPos resolved = PreviewSpawnHelper.fixupSpawnHeight(preview.level, preview.spawn);

         if (resolved != null && !resolved.equals(preview.spawn)) {
            WorldPreview.LOGGER.info("[preview] spawn resolved: {} -> {}", preview.spawn, resolved);
            preview.spawn = resolved;
            preview.spawnPosY = resolved.getY();
            preview.surfaceAdjusted = true;
            preview.player.setPos(resolved.getX() + 0.5, resolved.getY(), resolved.getZ() + 0.5);
            preview.player.setOldPosAndRot();
         }
      }

      // The first person preview has to stay on the real spawn block: only lift the player when the
      // spawn position itself is inside a block (the old code always jumped to WORLD_SURFACE, which
      // put the camera several blocks above the spawn - not the spawn view).
      if (!preview.surfaceAdjusted && preview.chunksApplied) {
         preview.surfaceAdjusted = true;

         try {
            var feet = preview.level.getBlockState(preview.spawn);
            var head = preview.level.getBlockState(preview.spawn.above());

            if (!feet.isAir() || !head.isAir()) {
               int surface = preview.level.getHeight(
                  net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, preview.spawn.getX(), preview.spawn.getZ()
               );

               if (surface > preview.level.getMinY()) {
                  preview.player.setPos(preview.spawn.getX() + 0.5, surface, preview.spawn.getZ() + 0.5);
                  preview.player.setOldPosAndRot();
                  preview.spawnPosY = surface;
               }
            }

            if (preview.cameraAnchor != null) {
                  // Sample the height of the camera's own column (three blocks south of spawn): using
                  // the spawn height here can bury the camera inside a hill, which renders the cards
                  // as a dark box with a bright sky ("black fog" look).
                  int cameraX = preview.spawn.getX();
                  int cameraZ = preview.spawn.getZ() - 3;
                  int cameraY = preview.level.getHeight(
                     net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cameraX, cameraZ
                  );
                  var cameraPos = new net.minecraft.core.BlockPos(cameraX, cameraY, cameraZ);

                  for (int i = 0; i < 5 && !preview.level.getBlockState(cameraPos).isAir(); i++) {
                     cameraPos = cameraPos.above();
                  }

               preview.cameraAnchor.setPos(cameraX + 0.5, cameraPos.getY() + 0.2, cameraZ + 0.5);
               preview.cameraAnchor.setOldPosAndRot();
            }
         } catch (Throwable t) {
            WorldPreview.LOGGER.warn("[preview] failed to place the preview camera on the surface", t);
         }
      }

      if (Boolean.getBoolean("worldpreview.cameraLog")) {
         WorldPreview.LOGGER.info(
            "[preview] spawn camera: spawn={} feet={} eye={} yaw={} pitch={} (thirdPerson={})",
         preview.spawn,
         preview.player.getY(),
         preview.player.getEyeY(),
         preview.player.getYRot(),
            preview.player.getXRot(),
            preview.cameraAnchor != null
         );
      }
      WorldPreview.LOGGER.info("[preview] activated {} preview sections (sodium {})", preview.packets.size(), SodiumCompat.stats());
   }
}
