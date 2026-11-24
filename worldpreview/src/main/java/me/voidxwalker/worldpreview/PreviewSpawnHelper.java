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

/**
 * 预览出生点：解析服务端出生点、修正高度与碰撞检查。
 * 从 ExternalPreview 拆分出来，行为保持不变。
 */
final class PreviewSpawnHelper {
   private PreviewSpawnHelper() {
   }

   static BlockPos resolveSpawn(ClientLevel level, BlockPos suggestion) {
      try {
         BlockPos levelSpawn = levelRespawnPos(level, suggestion.getX(), suggestion.getZ());

         if (levelSpawn != null && noCollisionNoLiquid(level, levelSpawn)) {
            return levelSpawn;
         }
      } catch (Throwable t) {
         WorldPreview.LOGGER.warn("[preview] spawn column lookup failed", t);
      }

      try {
         return fixupSpawnHeight(level, suggestion);
      } catch (Throwable t) {
         return suggestion;
      }
   }

   static BlockPos levelRespawnPos(ClientLevel level, int x, int z) {
      var chunk = level.getChunk(
         net.minecraft.core.SectionPos.blockToSectionCoord(x),
         net.minecraft.core.SectionPos.blockToSectionCoord(z)
      );
      int topY = chunk.getHeight(
         net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15
      );

      if (topY < level.getMinY()) {
         return null;
      }

      int surface = chunk.getHeight(
         net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x & 15, z & 15
      );

      if (surface <= topY
         && surface > chunk.getHeight(
            net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15
         )) {
         return null;
      }

      var pos = new net.minecraft.core.BlockPos.MutableBlockPos();

      for (int y = topY + 1; y >= level.getMinY(); y--) {
         pos.set(x, y, z);
         var state = level.getBlockState(pos);

         if (!state.getFluidState().isEmpty()) {
            break;
         }

         if (net.minecraft.world.level.block.Block.isFaceFull(
            state.getCollisionShape(level, pos), net.minecraft.core.Direction.UP
         )) {
            return pos.above().immutable();
         }
      }

      return null;
   }

   static net.minecraft.core.BlockPos fixupSpawnHeight(ClientLevel level, net.minecraft.core.BlockPos spawnPos) {
      var pos = spawnPos.mutable();

      while (!noCollisionNoLiquid(level, pos) && pos.getY() < level.getMaxY()) {
         pos.move(net.minecraft.core.Direction.UP);
      }

      pos.move(net.minecraft.core.Direction.DOWN);

      while (noCollisionNoLiquid(level, pos) && pos.getY() > level.getMinY()) {
         pos.move(net.minecraft.core.Direction.DOWN);
      }

      pos.move(net.minecraft.core.Direction.UP);
      return pos.immutable();
   }

   static boolean noCollisionNoLiquid(ClientLevel level, net.minecraft.core.BlockPos pos) {
      return level.noCollision(
         null,
         net.minecraft.world.entity.EntityTypes.PLAYER
            .getDimensions()
            .makeBoundingBox(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos)),
         true
      );
   }
}
