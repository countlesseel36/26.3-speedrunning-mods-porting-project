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
 * Builds a client-side preview of a world that is not the currently loading world, e.g. one of the
 * worlds a queue mod keeps generating in the background. Rendering is done separately.
 */
public final class ExternalPreview {
   public final ClientLevel level;
   public final ClientPacketListener listener;
   public final MultiPlayerGameMode gameMode;
   public final LocalPlayer player;
   /** Third-person camera anchor (invisible marker); null for first-person previews. */
   public final net.minecraft.world.entity.Entity cameraAnchor;
   public final BlockPos spawn;
   public final int radius;
   public final List<ClientboundLevelChunkWithLightPacket> packets;

   private ExternalPreview(
      ClientLevel level,
      ClientPacketListener listener,
      MultiPlayerGameMode gameMode,
      LocalPlayer player,
      net.minecraft.world.entity.Entity cameraAnchor,
      BlockPos spawn,
      int radius,
      List<ClientboundLevelChunkWithLightPacket> packets
   ) {
      this.level = level;
      this.listener = listener;
      this.gameMode = gameMode;
      this.player = player;
      this.cameraAnchor = cameraAnchor;
      this.spawn = spawn;
      this.radius = radius;
      this.packets = packets;
   }

   /**
    * Builds a preview from an already captured chunk packet snapshot, without blocking on the
    * server thread. Used while the world is still generating.
    */
   public static ExternalPreview fromPackets(
      Minecraft minecraft,
      MinecraftServer server,
      java.util.List<ClientboundLevelChunkWithLightPacket> packets,
      int radius
   ) {
      ServerLevel overworld = server.overworld();

      if (overworld == null) {
         return null;
      }

      try {
         BlockPos spawn = overworld.getRespawnData().pos();
         Connection connection = new Connection(PacketFlow.CLIENTBOUND);
         GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes("worldpreview".getBytes()), "Preview");
         WorldSessionTelemetryManager telemetry = new WorldSessionTelemetryManager(
            net.minecraft.client.telemetry.TelemetryEventSender.DISABLED, false, Duration.ZERO, "", profile.id()
         );
         CommonListenerCookie cookie = new CommonListenerCookie(
            new LevelLoadTracker(),
            profile,
            telemetry,
            server.registryAccess(),
            server.getWorldData().enabledFeatures(),
            "",
            null,
            null,
            Map.of(),
            new ChatComponent.State(List.of(), List.of(), List.of()),
            Map.of(),
            ServerLinks.EMPTY,
            Map.of(),
            false
         );
         ClientPacketListener listener = new ClientPacketListener(minecraft, connection, cookie);
         ClientLevel.ClientLevelData levelData = new ClientLevel.ClientLevelData(
            overworld.getDifficulty(), overworld.getLevelData().isHardcore(), false
         );
         ClientLevel level = new ClientLevel(
            listener,
            levelData,
            overworld.dimension(),
            overworld.dimensionTypeRegistration(),
            radius + 1,
            radius + 1,
            minecraft.levelExtractor,
            false,
            overworld.getSeed(),
            overworld.getSeaLevel()
         );
         ((me.voidxwalker.worldpreview.mixin.ClientPacketListenerAccessor)listener).worldpreview$setLevel(level);
         MultiPlayerGameMode gameMode = new MultiPlayerGameMode(minecraft, listener);
         LocalPlayer player = gameMode.createPlayer(level, null, null, new ItemActivation());

         if (player == null) {
            return null;
         }

         player.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
         player.setYRot(0.0F);
         player.setXRot(0.0F);
         player.setNoGravity(true);
         player.setOldPosAndRot();
         player.setDeltaMovement(0.0, 0.0, 0.0);
         player.setId(1);
         level.addEntity(player);

         net.minecraft.world.entity.Entity cameraAnchor = null;

         if (Boolean.getBoolean("worldpreview.thirdPerson")) {
            LocalPlayer camera = gameMode.createPlayer(level, null, null, new ItemActivation());

            if (camera != null) {
               camera.setPos(spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5 - 3.0);
               camera.setYRot(0.0F);
               camera.setXRot(-10.0F);
               camera.setNoGravity(true);
               camera.setOldPosAndRot();
               camera.setDeltaMovement(0.0, 0.0, 0.0);
               camera.setId(2);
               level.addEntity(camera);
               cameraAnchor = camera;
            }
         }

         SodiumCompat.setup(level);
         int centerX = spawn.getX() >> 4;
         int centerZ = spawn.getZ() >> 4;
         var cache = level.getChunkSource();
         cache.updateViewCenter(centerX, centerZ);
         cache.updateViewRadius(radius + 2);

         for (ClientboundLevelChunkWithLightPacket packet : packets) {
            packet.handle(listener);
         }

         WorldPreview.LOGGER.info("[preview] external preview built from snapshot: {} chunks", packets.size());
         return new ExternalPreview(level, listener, gameMode, player, cameraAnchor, spawn, radius, packets);
      } catch (Throwable t) {
         WorldPreview.LOGGER.warn("[preview] failed to build external preview from snapshot", t);
         return null;
      }
   }

   /** Must be called on the client thread. Blocks briefly while the server serialises chunks. */
   public static ExternalPreview build(Minecraft minecraft, MinecraftServer server, int radius) {
      ServerLevel overworld = server.overworld();

      if (overworld == null) {
         return null;
      }

      try {
         BlockPos spawn = overworld.getRespawnData().pos();
         Connection connection = new Connection(PacketFlow.CLIENTBOUND);
         GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes("worldpreview".getBytes()), "Preview");
         WorldSessionTelemetryManager telemetry = new WorldSessionTelemetryManager(
            net.minecraft.client.telemetry.TelemetryEventSender.DISABLED, false, Duration.ZERO, "", profile.id()
         );
         CommonListenerCookie cookie = new CommonListenerCookie(
            new LevelLoadTracker(),
            profile,
            telemetry,
            server.registryAccess(),
            server.getWorldData().enabledFeatures(),
            "",
            null,
            null,
            Map.of(),
            new ChatComponent.State(List.of(), List.of(), List.of()),
            Map.of(),
            ServerLinks.EMPTY,
            Map.of(),
            false
         );
         ClientPacketListener listener = new ClientPacketListener(minecraft, connection, cookie);
         ClientLevel.ClientLevelData levelData = new ClientLevel.ClientLevelData(
            overworld.getDifficulty(), overworld.getLevelData().isHardcore(), false
         );
         ClientLevel level = new ClientLevel(
            listener,
            levelData,
            overworld.dimension(),
            overworld.dimensionTypeRegistration(),
            radius + 1,
            radius + 1,
            minecraft.levelExtractor,
            false,
            overworld.getSeed(),
            overworld.getSeaLevel()
         );
         ((me.voidxwalker.worldpreview.mixin.ClientPacketListenerAccessor)listener).worldpreview$setLevel(level);
         MultiPlayerGameMode gameMode = new MultiPlayerGameMode(minecraft, listener);
         LocalPlayer player = gameMode.createPlayer(level, null, null, new ItemActivation());

         if (player == null) {
            return null;
         }

         player.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
         player.setYRot(0.0F);
         player.setXRot(0.0F);
         player.setNoGravity(true);
         player.setOldPosAndRot();
         player.setDeltaMovement(0.0, 0.0, 0.0);
         player.setId(1);
         level.addEntity(player);

         net.minecraft.world.entity.Entity cameraAnchor = null;

         if (Boolean.getBoolean("worldpreview.thirdPerson")) {
            LocalPlayer camera = gameMode.createPlayer(level, null, null, new ItemActivation());

            if (camera != null) {
               camera.setPos(spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5 - 3.0);
               camera.setYRot(0.0F);
               camera.setXRot(-10.0F);
               camera.setNoGravity(true);
               camera.setOldPosAndRot();
               camera.setDeltaMovement(0.0, 0.0, 0.0);
               camera.setId(2);
               level.addEntity(camera);
               cameraAnchor = camera;
            }
         }

         SodiumCompat.setup(level);

         int centerX = spawn.getX() >> 4;
         int centerZ = spawn.getZ() >> 4;
         List<ClientboundLevelChunkWithLightPacket> packets = server.submit(() -> {
            List<ClientboundLevelChunkWithLightPacket> snapshot = new ArrayList<>();

            for (int dx = -radius; dx <= radius; dx++) {
               for (int dz = -radius; dz <= radius; dz++) {
                  LevelChunk chunk = (LevelChunk)overworld.getChunkSource()
                     .getChunk(centerX + dx, centerZ + dz, ChunkStatus.FULL, false);

                  if (chunk != null) {
                     snapshot.add(new ClientboundLevelChunkWithLightPacket(chunk, overworld.getLightEngine(), null, null));
                  }
               }
            }

            return snapshot;
         }).get(10, java.util.concurrent.TimeUnit.SECONDS);

         var cache = level.getChunkSource();
         cache.updateViewCenter(centerX, centerZ);
         cache.updateViewRadius(radius + 2);

         for (ClientboundLevelChunkWithLightPacket packet : packets) {
            packet.handle(listener);
         }

         WorldPreview.LOGGER.info(
            "[preview] external preview built for {}: {} chunks", overworld.dimension().identifier(), packets.size()
         );
         return new ExternalPreview(level, listener, gameMode, player, cameraAnchor, spawn, radius, packets);
      } catch (Throwable t) {
         WorldPreview.LOGGER.warn("[preview] failed to build external preview", t);
         return null;
      }
   }
}