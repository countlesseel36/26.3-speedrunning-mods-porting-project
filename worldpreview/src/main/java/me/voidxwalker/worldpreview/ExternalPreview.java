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
   /** Where the player really spawns; refined once the preview chunks exist (vanilla spawn search). */
   public BlockPos spawn;
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
         // The server's effective respawn data is where a joining player is placed; the level's own
         // respawn data can still hold the pre-generation value (often 0,0), which is why the card
         // camera used to sit far away from the real spawn.
         var spawnData = server.getRespawnData();
         BlockPos spawn = spawnData.pos();
         float spawnYaw = spawnData.yaw();
         float spawnPitch = spawnData.pitch();
         WorldPreview.LOGGER.info(
            "[preview] spawn source: server={} level={}",
            spawn,
            overworld.getRespawnData().pos()
         );
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
         // The preview mirrors the first person view the player gets on spawn: feet on the spawn
         // block, camera at the player's eye height, and the yaw/pitch the world spawns with. The old
         // fixed yaw/pitch (0 / 10 degrees down) was not the spawn view.
         player.setYRot(spawnYaw);
         player.setXRot(spawnPitch);
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
               camera.setXRot(12.0F);
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

         WorldPreview.LOGGER.info("[preview] external preview built from snapshot: {} chunks", packets.size());
         return new ExternalPreview(level, listener, gameMode, player, cameraAnchor, spawn, radius, packets);
      } catch (Throwable t) {
         WorldPreview.LOGGER.warn("[preview] failed to build external preview from snapshot", t);
         return null;
      }
   }

   /**
    * Applies chunk packets and tells Sodium about them. Sodium ignores chunks that arrive while its
    * renderer is bound to a different level and only builds geometry for chunks its {@code
    * ChunkTracker} reports as lit, so both steps are required for the preview to have terrain.
    */
   private static void applyChunks(
      ClientLevel level,
      ClientPacketListener listener,
      List<ClientboundLevelChunkWithLightPacket> packets,
      BlockPos spawn
   ) {
      int centerSectionY = spawn.getY() >> 4;

      for (ClientboundLevelChunkWithLightPacket packet : packets) {
         packet.handle(listener);
         SodiumCompat.markLightReady(level, packet.x(), packet.z());
         SodiumCompat.rebuildColumn(level, packet.x(), packet.z(), centerSectionY);
      }

      SodiumCompat.pumpChunkEvents();
      SodiumCompat.refreshTerrain();
   }

   private boolean chunksApplied;
   private boolean surfaceAdjusted;
   private boolean spawnResolved;
   private int packetCursor;
   public int spawnPosY;


   /** Applies chunks generated after the preview first appeared (progressive terrain). */
   public void applyNewPackets(java.util.List<ClientboundLevelChunkWithLightPacket> packets) {
      if (packets == null || packets.isEmpty()) {
         return;
      }

      SodiumCompat.setup(this.level);
      int centerSectionY = this.spawn.getY() >> 4;

      for (ClientboundLevelChunkWithLightPacket packet : packets) {
         packet.handle(this.listener);
         SodiumCompat.markLightReady(this.level, packet.x(), packet.z());
      }

      SodiumCompat.pumpChunkEvents();

      for (ClientboundLevelChunkWithLightPacket packet : packets) {
         SodiumCompat.rebuildColumn(this.level, packet.x(), packet.z(), centerSectionY);
      }

      SodiumCompat.pumpChunkEvents();
      SodiumCompat.refreshTerrain();
   }

   /**
    * Applies the chunk snapshot and feeds Sodium, once the preview level is actually the level the
    * client is rendering. Injecting chunks earlier leaves Sodium's {@code ChunkTracker} empty (its
    * global renderer is still bound to another level), so no terrain is ever built.
    */
   public void activateSections() {
      int centerSectionY = this.spawn.getY() >> 4;
      // Re-bind Sodium first: the renderer may have been restored to another level in between,
      // and its chunk tracker calls crash when no section manager is bound.
      SodiumCompat.setup(this.level);

      if (!this.chunksApplied) {
         this.level.getChunkSource().updateViewCenter(this.spawn.getX() >> 4, this.spawn.getZ() >> 4);
         this.level.getChunkSource().updateViewRadius(this.radius + 2);

         // Wall Data Limit: apply at most this many chunk packets per preview update, so a big
         // snapshot does not cause a frame spike when a card is first shown.
         int limit = Math.max(1, Integer.getInteger("seedqueue.wallDataLimit", 100));
         int end = Math.min(this.packets.size(), this.packetCursor + limit);

         for (int i = this.packetCursor; i < end; i++) {
            ClientboundLevelChunkWithLightPacket packet = this.packets.get(i);
            packet.handle(this.listener);
            SodiumCompat.markLightReady(this.level, packet.x(), packet.z());
         }

         SodiumCompat.pumpChunkEvents();

         for (int i = this.packetCursor; i < end; i++) {
            ClientboundLevelChunkWithLightPacket packet = this.packets.get(i);
            SodiumCompat.rebuildColumn(this.level, packet.x(), packet.z(), centerSectionY);
         }

         SodiumCompat.pumpChunkEvents();
         this.packetCursor = end;

         if (this.packetCursor >= this.packets.size()) {
            this.chunksApplied = true;
            SodiumCompat.refreshTerrain();
         }
      } else {
         // The snapshot is already in the level and Sodium's tracker: on the next rotation just
         // drain queued chunk events instead of re-sending every packet and rebuilding all sections.
         SodiumCompat.pumpChunkEvents();
      }

      // Resolve the exact spawn the server will use (respawn_radius is 0 for queue worlds) and move the
      // preview camera there; the raw respawn suggestion is off by up to the old search radius.
      if (!this.spawnResolved && this.chunksApplied) {
         this.spawnResolved = true;

         BlockPos resolved = fixupSpawnHeight(this.level, this.spawn);

         if (resolved != null && !resolved.equals(this.spawn)) {
            WorldPreview.LOGGER.info("[preview] spawn resolved: {} -> {}", this.spawn, resolved);
            this.spawn = resolved;
            this.spawnPosY = resolved.getY();
            this.surfaceAdjusted = true;
            this.player.setPos(resolved.getX() + 0.5, resolved.getY(), resolved.getZ() + 0.5);
            this.player.setOldPosAndRot();
         }
      }

      // The first person preview has to stay on the real spawn block: only lift the player when the
      // spawn position itself is inside a block (the old code always jumped to WORLD_SURFACE, which
      // put the camera several blocks above the spawn - not the spawn view).
      if (!this.surfaceAdjusted && this.chunksApplied) {
         this.surfaceAdjusted = true;

         try {
            var feet = this.level.getBlockState(this.spawn);
            var head = this.level.getBlockState(this.spawn.above());

            if (!feet.isAir() || !head.isAir()) {
               int surface = this.level.getHeight(
                  net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, this.spawn.getX(), this.spawn.getZ()
               );

               if (surface > this.level.getMinY()) {
                  this.player.setPos(this.spawn.getX() + 0.5, surface, this.spawn.getZ() + 0.5);
                  this.player.setOldPosAndRot();
                  this.spawnPosY = surface;
               }
            }

            if (this.cameraAnchor != null) {
                  // Sample the height of the camera's own column (three blocks south of spawn): using
                  // the spawn height here can bury the camera inside a hill, which renders the cards
                  // as a dark box with a bright sky ("black fog" look).
                  int cameraX = this.spawn.getX();
                  int cameraZ = this.spawn.getZ() - 3;
                  int cameraY = this.level.getHeight(
                     net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cameraX, cameraZ
                  );
                  var cameraPos = new net.minecraft.core.BlockPos(cameraX, cameraY, cameraZ);

                  for (int i = 0; i < 5 && !this.level.getBlockState(cameraPos).isAir(); i++) {
                     cameraPos = cameraPos.above();
                  }

               this.cameraAnchor.setPos(cameraX + 0.5, cameraPos.getY() + 0.2, cameraZ + 0.5);
               this.cameraAnchor.setOldPosAndRot();
            }
         } catch (Throwable t) {
            WorldPreview.LOGGER.warn("[preview] failed to place the preview camera on the surface", t);
         }
      }

      if (Boolean.getBoolean("worldpreview.cameraLog")) {
         WorldPreview.LOGGER.info(
            "[preview] spawn camera: spawn={} feet={} eye={} yaw={} pitch={} (thirdPerson={})",
         this.spawn,
         this.player.getY(),
         this.player.getEyeY(),
         this.player.getYRot(),
            this.player.getXRot(),
            this.cameraAnchor != null
         );
      }
      WorldPreview.LOGGER.info("[preview] activated {} preview sections (sodium {})", this.packets.size(), SodiumCompat.stats());
   }


   /**
    * Reproduces vanilla's {@code PlayerSpawnFinder} spawn resolution on the preview level.
    *
    * <p>SeedQueue forces {@code respawn_radius = 0} for queue worlds, so the server's search has a
    * single candidate: the spawn column, resolved through {@code getLevelRespawnPos} and
    * {@code fixupSpawnHeight}. Running the same steps on the preview level gives exactly the block the
    * player will stand on, instead of the raw (and often wrong) respawn suggestion.</p>
    */
   public static BlockPos resolveSpawn(ClientLevel level, BlockPos suggestion) {
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

   private static BlockPos levelRespawnPos(ClientLevel level, int x, int z) {
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

   private static net.minecraft.core.BlockPos fixupSpawnHeight(ClientLevel level, net.minecraft.core.BlockPos spawnPos) {
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

   private static boolean noCollisionNoLiquid(ClientLevel level, net.minecraft.core.BlockPos pos) {
      return level.noCollision(
         null,
         net.minecraft.world.entity.EntityTypes.PLAYER
            .getDimensions()
            .makeBoundingBox(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos)),
         true
      );
   }

   /** Must be called on the client thread. Blocks briefly while the server serialises chunks. */
   public static ExternalPreview build(Minecraft minecraft, MinecraftServer server, int radius) {
      ServerLevel overworld = server.overworld();

      if (overworld == null) {
         return null;
      }

      try {
         // The server's effective respawn data is where a joining player is placed; the level's own
         // respawn data can still hold the pre-generation value (often 0,0), which is why the card
         // camera used to sit far away from the real spawn.
         var spawnData = server.getRespawnData();
         BlockPos spawn = spawnData.pos();
         float spawnYaw = spawnData.yaw();
         float spawnPitch = spawnData.pitch();
         WorldPreview.LOGGER.info(
            "[preview] spawn source: server={} level={}",
            spawn,
            overworld.getRespawnData().pos()
         );
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
         // The preview mirrors the first person view the player gets on spawn: feet on the spawn
         // block, camera at the player's eye height, and the yaw/pitch the world spawns with. The old
         // fixed yaw/pitch (0 / 10 degrees down) was not the spawn view.
         player.setYRot(spawnYaw);
         player.setXRot(spawnPitch);
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
               camera.setXRot(12.0F);
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
