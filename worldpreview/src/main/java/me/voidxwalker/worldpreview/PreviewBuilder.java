package me.voidxwalker.worldpreview;

import com.mojang.authlib.GameProfile;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * Builds a client-side copy of the world the integrated server is currently generating.
 *
 * <p>Only a small area around the spawn point is streamed (a two chunk render distance), which is
 * enough for the preview and finishes quickly. Chunk packets must be created on the server thread:
 * serialising a {@link LevelChunk} from the render thread races with world generation.</p>
 */
public final class PreviewBuilder {
   public static final int CHUNK_RADIUS = 2;
   private static final int CHUNKS_PER_REQUEST = 8;
   private static final ConcurrentLinkedQueue<ClientboundLevelChunkWithLightPacket> PACKETS = new ConcurrentLinkedQueue<>();
   private static final Set<Long> REQUESTED = ConcurrentHashMap.newKeySet();
   private static final AtomicBoolean SNAPSHOT_PENDING = new AtomicBoolean();
   private static final java.util.concurrent.atomic.AtomicReference<net.minecraft.world.phys.Vec3> SAFE_SPAWN = new java.util.concurrent.atomic.AtomicReference<>();
   private static boolean spawnLookupStarted;
   private static BlockPos lookupSpawn;
   private static int appliedChunks;
   private static int refreshTicks;

   private PreviewBuilder() {
   }

   public static int expectedChunks() {
      int diameter = CHUNK_RADIUS * 2 + 1;
      return diameter * diameter;
   }

   /** Drops the per-position bookkeeping so a new spawn area is streamed again. */
   public static void resetChunks() {
      REQUESTED.clear();
      PACKETS.clear();
      appliedChunks = 0;
      WorldPreview.resetPreviewProgress();
   }

   /** Drains chunk packets produced by the server thread and applies them to the preview level. */
   public static int refresh(WorldPreviewProperties properties) {
      Minecraft minecraft = Minecraft.getInstance();
      MinecraftServer server = minecraft.getSingleplayerServer();
      if (server == null) {
         return 0;
      }

      ServerLevel overworld = server.overworld();
      if (overworld == null) {
         return 0;
      }

      BlockPos spawnPos = overworld.getRespawnData().pos();
      refreshTicks++;

      // Ask the server for the safe spawn position it will use before deciding where the camera
      // goes: world spawn and safe spawn can be hundreds of blocks apart and switching between
      // them every refresh would recenter (and invalidate) the preview over and over.
      if (!spawnLookupStarted) {
         spawnLookupStarted = true;
         lookupSpawn = spawnPos;
         server.execute(() -> net.minecraft.server.level.PlayerSpawnFinder.findSpawn(overworld, spawnPos).thenAccept(v -> { SAFE_SPAWN.set(v); WorldPreview.LOGGER.info("[preview] finder future completed: {}", v); }));
      }

      net.minecraft.world.phys.Vec3 safeSpawn = SAFE_SPAWN.get();
      boolean safeSpawnMatches = safeSpawn != null && lookupSpawn != null && lookupSpawn.equals(spawnPos);

      if (WorldPreview.hasFinalSpawn()) {
         // the spawn is already frozen from the authoritative player position; do not move it again
      } else if (safeSpawnMatches) {
         if (WorldPreview.updateSpawn(BlockPos.containing(safeSpawn.x, safeSpawn.y, safeSpawn.z), true)) {
            resetChunks();
         }
      } else {
         boolean valid = isValidSpawn(overworld, spawnPos);

         if (!valid && !WorldPreview.hasSpawn() && refreshTicks > 200 && spawnPos.getY() > 0) {
            valid = true;
         }

         if (WorldPreview.updateSpawn(spawnPos, valid)) {
            resetChunks();
         }
      }

      if (!WorldPreview.hasSpawn()) {
         return 0;
      }

      requestSnapshot(server, overworld, WorldPreview.properties().spawnPos);

      ClientPacketListener listener = properties.listener;
      int applied = 0;
      ClientboundLevelChunkWithLightPacket packet;

      // Chunks are only injected once the preview camera is actually swapped in: Sodium's chunk
      // tracker only accepts chunks while its level is the active one.
      // The preview chunk cache starts with a (0,0)/radius 0 window, so packets outside it would be
      // dropped. Move the window to the spawn before applying anything.
      var cache = properties.level.getChunkSource();
      cache.updateViewCenter(WorldPreview.properties().spawnPos.getX() >> 4, WorldPreview.properties().spawnPos.getZ() >> 4);
      cache.updateViewRadius(CHUNK_RADIUS + 2);

      while ((packet = PACKETS.poll()) != null) {
         packet.handle(listener);
         SodiumCompat.markLightReady(properties.level, packet.x(), packet.z());
         SodiumCompat.rebuildColumn(properties.level, packet.x(), packet.z(), WorldPreview.properties().spawnPos.getY() >> 4);
         applied++;
         appliedChunks++;
      }

      if (applied > 0) {
         WorldPreview.invalidateCompiledSections();
         WorldPreview.noteChunksApplied(appliedChunks, expectedChunks());
      }

      return applied;
   }

   private static boolean isValidSpawn(ServerLevel overworld, BlockPos pos) {
      // Accept immediately so chunk requests can start while the spawn area is still generating;
      // chunk lookups simply retry until the chunks exist.
      return pos.getY() > 0;
   }

   /** Asks the server thread for the chunks of the spawn area that have not been sent yet. */
   private static void requestSnapshot(MinecraftServer server, ServerLevel overworld, BlockPos spawnPos) {
      if (!SNAPSHOT_PENDING.compareAndSet(false, true)) {
         return;
      }

      int centerX = spawnPos.getX() >> 4;
      int centerZ = spawnPos.getZ() >> 4;
      server.execute(() -> {
         try {
            List<ClientboundLevelChunkWithLightPacket> packets = new ArrayList<>();
            outer:
            for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
               for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                  long key = ChunkPos.pack(centerX + dx, centerZ + dz);
                  if (REQUESTED.contains(key)) {
                     continue;
                  }

                  LevelChunk chunk = (LevelChunk)overworld.getChunkSource().getChunk(centerX + dx, centerZ + dz, ChunkStatus.FULL, false);
                  if (chunk == null) {
                     continue;
                  }

                  if (packets.size() >= CHUNKS_PER_REQUEST) {
                     break outer;
                  }

                  REQUESTED.add(key);
                  packets.add(new ClientboundLevelChunkWithLightPacket(chunk, overworld.getLightEngine(), null, null));
               }
            }

            PACKETS.addAll(packets);
         } catch (Throwable t) {
            WorldPreview.LOGGER.warn("[preview] failed to snapshot chunks", t);
         } finally {
            SNAPSHOT_PENDING.set(false);
         }
      });
   }

   public static WorldPreviewProperties build(Minecraft minecraft) {
      MinecraftServer server = minecraft.getSingleplayerServer();
      if (server == null) {
         return null;
      }

      ServerLevel overworld = server.overworld();
      if (overworld == null) {
         return null;
      }

      try {
         BlockPos spawnPos = overworld.getRespawnData().pos();
         Connection connection = new Connection(PacketFlow.CLIENTBOUND);
         GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes("worldpreview".getBytes()), "Preview");
         WorldSessionTelemetryManager telemetry = new WorldSessionTelemetryManager(net.minecraft.client.telemetry.TelemetryEventSender.DISABLED, false, Duration.ZERO, "", profile.id());
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
            CHUNK_RADIUS,
            CHUNK_RADIUS,
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

         // Feet exactly on the spawn block so the camera matches the player's spawn view.
         player.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
         player.setNoGravity(true);
         player.setOldPosAndRot();
         player.setDeltaMovement(0.0, 0.0, 0.0);
         player.setId(1);
         level.addEntity(player);

         // Sodium has to know the level before the chunks are injected, otherwise its
         // RenderSectionManager never receives the chunk events and no terrain is built.
         SodiumCompat.setup(level);

         WorldPreview.LOGGER.info("[preview] built preview shell for {}", overworld.dimension().identifier());
         return new WorldPreviewProperties(level, listener, gameMode, player, spawnPos);
      } catch (Throwable t) {
         WorldPreview.LOGGER.warn("[preview] failed to build preview", t);
         return null;
      }
   }
}
