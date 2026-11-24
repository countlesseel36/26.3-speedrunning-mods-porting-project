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
         GameProfile profile = previewProfile(minecraft);
         WorldPreview.previewSkin = minecraft.player == null ? null : minecraft.player.getSkin();
         WorldPreview.previewSkinUuid = minecraft.player == null ? null : minecraft.player.getUUID();
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
         applyRealSkin(minecraft, player);

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

         // Wall previews are always first person: the offset camera looked past the generated chunks
         // and left a black band at the bottom of the cards, whatever the player's perspective was.
         if (thirdPersonCameraAllowed()) {
            LocalPlayer camera = gameMode.createPlayer(level, null, null, new ItemActivation());
         applyRealSkin(minecraft, camera);

            if (camera != null) {
               camera.setPos(spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5 - thirdPersonCameraDistance());
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

   boolean chunksApplied;
   boolean surfaceAdjusted;
   boolean spawnResolved;
   int packetCursor;
   public int spawnPosY;


   /** Applies chunks generated after the preview first appeared (progressive terrain). */
   public void applyNewPackets(java.util.List<ClientboundLevelChunkWithLightPacket> packets) {
      PreviewSectionHelper.applyNewPackets(this, packets);
   }

   /**
    * Applies the chunk snapshot and feeds Sodium, once the preview level is actually the level the
    * client is rendering. Injecting chunks earlier leaves Sodium's {@code ChunkTracker} empty (its
    * global renderer is still bound to another level), so no terrain is ever built.
    */
   /**
    * Drops the "this snapshot is already inside Sodium" state.
    *
    * <p>{@link #activateSections()} only re-sends chunk packets while {@code chunksApplied} is false.
    * When the section manager is destroyed and rebuilt (chunk radius change, returning to the wall,
    * leaving the wall) the new manager starts with an empty tracker, so the flag has to be reset or the
    * card keeps showing an empty graph - the unrendered areas after F6.</p>
    */
   public void invalidateSections() {
      PreviewSectionHelper.invalidateSections(this);
   }

   /** Timestamp of the last chunk re-application; cards stay on their previous frame until settled. */
   long lastActivationAt;
   private long completeSince;

   /**
    * True when Sodium reports the preview's terrain as completely built.
    *
    * <p>Cards copy the frame from the main render target, so capturing while sections are still being
    * compiled puts half-built terrain (white blocks) into the card. Waiting for the completion flag
    * keeps the previous frame up until the rebuild really is done, instead of guessing with a timeout.
    * {@code seedqueue.cardSettleMs} stays as an upper bound: if the flag never settles (or Sodium is
    * absent) the card is released after that long anyway so it can never freeze forever.</p>
    */
   public boolean settled() {
      if (!this.chunksApplied) {
         return false;
      }

      Boolean complete = SodiumCompat.terrainComplete();
      long now = System.currentTimeMillis();

      if (Boolean.TRUE.equals(complete)) {
         // "Complete" only means Sodium has no further section updates queued; the vertex uploads for
         // the last ones may still be in flight, and copying that frame is what shows up as white
         // patches. Require the flag to have been stable for a moment before releasing the card.
         if (this.completeSince == 0L) {
            this.completeSince = now;
         }

         return now - this.completeSince >= Math.max(0L, Long.getLong("seedqueue.cardCompleteDelayMs", 80L));
      }

      this.completeSince = 0L;

      if (complete == null) {
         // No Sodium information: fall back to the short settle window.
         return System.currentTimeMillis() - this.lastActivationAt >= Math.max(0L, Long.getLong("seedqueue.cardSettleMs", 260L));
      }

      // Still compiling, but never hold a card longer than the timeout.
      long maxWait = Math.max(1000L, Long.getLong("seedqueue.cardSettleMaxMs", 3000L));
      return System.currentTimeMillis() - this.lastActivationAt >= maxWait;
   }

   /**
    * Profile used for the preview player: the real one, so third-person cards show the player's skin.
    * The PlayerInfo profile is handed over as-is (not copied) because SkinManager keys its cache by
    * profile identity/properties; a copy never resolves to a skin.
    */

   /**
    * Hands the real player's {@code PlayerInfo} to a preview player.
    *
    * <p>{@code AbstractClientPlayer#getSkin()} resolves through its {@code playerInfo} field, and a
    * preview player created without one falls back to the default skin — which is why third-person
    * cards had no skin. Copying the profile was not enough; the skin manager needs the same
    * {@code PlayerInfo} instance the real connection uses.</p>
    */
   static  void applyRealSkin(Minecraft minecraft, net.minecraft.client.player.LocalPlayer player) {
      PreviewSkinHelper.applyRealSkin(minecraft, player);
   }

   static  GameProfile previewProfile(Minecraft minecraft) {
      return PreviewSkinHelper.previewProfile(minecraft);
   }

   public void activateSections() {
      PreviewSectionHelper.activateSections(this);
   }


   /**
    * Reproduces vanilla's {@code PlayerSpawnFinder} spawn resolution on the preview level.
    *
    * <p>SeedQueue forces {@code respawn_radius = 0} for queue worlds, so the server's search has a
    * single candidate: the spawn column, resolved through {@code getLevelRespawnPos} and
    * {@code fixupSpawnHeight}. Running the same steps on the preview level gives exactly the block the
    * player will stand on, instead of the raw (and often wrong) respawn suggestion.</p>
    */
   public static  BlockPos resolveSpawn(ClientLevel level, BlockPos suggestion) {
      return PreviewSpawnHelper.resolveSpawn(level, suggestion);
   }

   private static  BlockPos levelRespawnPos(ClientLevel level, int x, int z) {
      return PreviewSpawnHelper.levelRespawnPos(level, x, z);
   }

   private static  net.minecraft.core.BlockPos fixupSpawnHeight(ClientLevel level, net.minecraft.core.BlockPos spawnPos) {
      return PreviewSpawnHelper.fixupSpawnHeight(level, spawnPos);
   }

   private static  boolean noCollisionNoLiquid(ClientLevel level, net.minecraft.core.BlockPos pos) {
      return PreviewSpawnHelper.noCollisionNoLiquid(level, pos);
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
         GameProfile profile = previewProfile(minecraft);
         WorldPreview.previewSkin = minecraft.player == null ? null : minecraft.player.getSkin();
         WorldPreview.previewSkinUuid = minecraft.player == null ? null : minecraft.player.getUUID();
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
         applyRealSkin(minecraft, player);

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

         // Wall previews are always first person: the offset camera looked past the generated chunks
         // and left a black band at the bottom of the cards, whatever the player's perspective was.
         if (thirdPersonCameraAllowed()) {
            LocalPlayer camera = gameMode.createPlayer(level, null, null, new ItemActivation());
         applyRealSkin(minecraft, camera);

            if (camera != null) {
               camera.setPos(spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5 - thirdPersonCameraDistance());
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

   /**
    * Third person is only safe while the camera stays inside the generated chunks: the player-to-camera
    * line has to remain within the loaded radius, otherwise the card shows unloaded space as black.
    */
   private static int previewRadiusBlocks() {
      return Math.max(16, Integer.getInteger("worldpreview.previewRadiusBlocks", 6 * 16));
   }

   private static boolean thirdPersonCameraAllowed() {
      return Boolean.getBoolean("worldpreview.thirdPerson") && previewRadiusBlocks() > 48;
   }

   private static float thirdPersonCameraDistance() {
      // One block of slack at the loaded edge, and never further back than the vanilla 3 blocks.
      return Math.min(3.0F, Math.max(0.0F, previewRadiusBlocks() - 17.0F));
   }
}
