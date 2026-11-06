package me.contaria.seedqueue;

import com.mojang.serialization.Lifecycle;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.Util;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;

/** One queued world: prepared on a background thread, started as its own integrated server. */
public final class QueueEntry {
   public enum State {
      CREATED,
      PREPARING,
      PREPARED,
      STARTING,
      GENERATING,
      READY,
      DISCARDED,
      FAILED
   }

   public final int number;
   public final long seed;
   public final String folder;
   public volatile State state = State.CREATED;
   public volatile LevelStorageSource.LevelStorageAccess access;
   public volatile PackRepository packs;
   public volatile WorldStem stem;
   public volatile IntegratedServer server;
   public volatile boolean locked;
   public volatile boolean transferred;
   /** Set by the wall "schedule join" action: the entry joins as soon as it is ready. */
   public volatile boolean scheduledJoin;
   /** Chunks generated after the preview appeared, drained into the preview by the wall. */
   public final java.util.List<net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket> pendingPackets =
      java.util.Collections.synchronizedList(new java.util.ArrayList<>());
   /** Set when maxWorldGenerationPercentage stopped this entry; cleared when it may finish. */
   public volatile boolean reachedLimit;
   public volatile boolean paused;
   /** Order in which the player locked entries (for "play next lock"). */
   public volatile long lockedAt;
   private long lastMapCaptureAt;
   /** Set while the entry is scheduled to start but the state has not changed yet. */
   public volatile boolean startQueued;
   private final Object generationGate = new Object();
   private boolean generationPermit;
   private boolean forceFinish;
   public volatile String error;
   public volatile java.nio.file.Path worldDirectory;
   public volatile int chunkRadius = Integer.getInteger("seedqueue.chunkRadius", QueueConfig.chunkRadius);
   public volatile int[] mapColors;
   public volatile int[] mapHeights;
   public volatile int[] previewPixels;
   public final int previewWidth = 160;
   public final int previewHeight = 90;
   public volatile net.minecraft.resources.Identifier previewTexture;
   public volatile com.mojang.blaze3d.platform.NativeImage nativePreview;
   public volatile me.voidxwalker.worldpreview.ExternalPreview externalPreview;
   public volatile me.voidxwalker.worldpreview.WorldPreviewProperties renderProperties;
   public volatile com.mojang.blaze3d.pipeline.RenderTarget renderTarget;
   public volatile com.mojang.blaze3d.pipeline.RenderTarget displayTarget;
   /** prepareExternal (section tracker + allChanged) only has to run once per built preview level. */
   public volatile boolean sectionsPrepared;
   public volatile long lastRenderedAt;
   public volatile long lastPreviewAt;
   public volatile int captureWidth;
   private net.minecraft.resources.Identifier previousPreviewTexture;
   public volatile int captureHeight;
   private int previewUploads;
   public volatile boolean previewActive;
   public volatile boolean shotCaptured;
   public volatile boolean frozen;
   /** True while the background server is held paused at the wall (kept in memory for hand-off). */
   public volatile boolean parked;
   /** True once the player selected this entry; unpark + hand the live server to the client. */
   public volatile boolean selected;
   private final Object parkLock = new Object();
   public volatile long readyAtMillis;
   public final java.util.List<net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket> chunkPackets =
      java.util.Collections.synchronizedList(new java.util.ArrayList<>());
   private double cameraX;
   private double cameraY;
   private double cameraZ;
   public final int mapWidth = 32;
   public final int mapHeight = 20;
   public volatile int chunksGenerated;
   public volatile int chunksTotal;
   public volatile int stageProgress;
   public volatile String stage = "";

   public QueueEntry(int number, long seed) {
      this.number = number;
      this.seed = seed;
      this.folder = "SeedQueue-" + System.currentTimeMillis() + "-" + number;
   }

   /** Difficulty configured in Atum, so queued worlds match what the reset key would create. */
   private static net.minecraft.world.Difficulty atumDifficulty() {
      int id = 1;

      try {
         Class<?> atum = Class.forName("me.voidxwalker.autoreset.Atum");
         id = atum.getField("difficulty").getInt(null);
      } catch (Throwable ignored) {
      }

      return switch (id) {
         case 0 -> net.minecraft.world.Difficulty.PEACEFUL;
         case 2 -> net.minecraft.world.Difficulty.NORMAL;
         case 3 -> net.minecraft.world.Difficulty.HARD;
         default -> net.minecraft.world.Difficulty.EASY;
      };
   }

   public void prepare(Minecraft minecraft) {
      state = State.PREPARING;

      try {
         worldDirectory = minecraft.getLevelSource().getBaseDir().resolve(folder);
         access = minecraft.getLevelSource().validateAndCreateAccess(folder);
         packs = ServerPacksSource.createPackRepository(access);
         LevelSettings settings = new LevelSettings(
            folder, GameType.SURVIVAL, LevelSettings.DifficultySettings.DEFAULT, false, WorldDataConfiguration.DEFAULT
         ).withDifficulty(atumDifficulty());
         // Use the settings the player configured in Atum instead of vanilla defaults, so queued worlds
         // match what the reset key would create (structures / bonus chest).
         boolean generateStructures = true;
         boolean bonusChest = false;

         try {
            Class<?> atum = Class.forName("me.voidxwalker.autoreset.Atum");
            generateStructures = atum.getField("structures").getBoolean(null);
            bonusChest = atum.getField("bonusChest").getBoolean(null);
         } catch (Throwable t) {
            SeedQueue.LOGGER.warn("[seedqueue] could not read Atum world settings, using defaults", t);
         }

         WorldOptions options = new WorldOptions(seed, generateStructures, bonusChest);
         WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(packs, settings.dataConfiguration(), false, false);
         WorldLoader.InitConfig initConfig = new WorldLoader.InitConfig(
            packConfig, Commands.CommandSelection.INTEGRATED, PermissionSet.ALL_PERMISSIONS
         );
         stem = WorldLoader.load(
               initConfig,
               context -> {
                  WorldDimensions dimensions = context.datapackWorldRegistries()
                     .lookupOrThrow(Registries.WORLD_PRESET)
                     .getOrThrow(WorldPresets.NORMAL)
                     .value()
                     .createWorldDimensions();
                  WorldDimensions.Complete complete = dimensions.bake(
                     context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM)
                  );
                  PrimaryLevelData data = new PrimaryLevelData(settings, complete.specialWorldProperty(), complete.lifecycle());
                  WorldGenSettings genSettings = new WorldGenSettings(options, dimensions);
                  return new WorldLoader.DataLoadOutput<>(
                     new LevelDataAndDimensions.WorldDataAndGenSettings(data, genSettings),
                     complete.dimensionsRegistryAccess()
                  );
               },
               WorldStem::new,
               Util.backgroundExecutor(),
               Util.backgroundExecutor()
            )
            .get();
         access.saveDataTag(stem.worldDataAndGenSettings().data());
         state = State.PREPARED;
         SeedQueue.LOGGER.info("[seedqueue] prepared entry #{} ({})", number, folder);
      } catch (Throwable t) {
         state = State.FAILED;
         error = t.toString();
         SeedQueue.LOGGER.error("[seedqueue] failed to prepare entry #{}", number, t);
      }
   }

   public void start(Minecraft minecraft) {
      if (state != State.PREPARED) {
         return;
      }

      state = State.STARTING;
      this.startQueued = false;
      LevelLoadListener listener = new LevelLoadListener() {
         @Override
         public void start(LevelLoadListener.Stage loadStage, int i) {
            stage = loadStage.name();
            stageProgress = 0;
         }

         @Override
         public void update(LevelLoadListener.Stage loadStage, int i, int j) {
            stage = loadStage.name();
            stageProgress = i * 100 / Math.max(1, j);
         }

         @Override
         public void finish(LevelLoadListener.Stage loadStage) {
            stage = loadStage.name();
            stageProgress = 100;
         }

         @Override
         public void updateFocus(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key, net.minecraft.world.level.ChunkPos pos) {
         }
      };

      try {
         server = MinecraftServer.spin(thread -> {
            thread.setName("SeedQueue server " + number);

            try {
               thread.setPriority(Math.max(1, Math.min(10, QueueConfig.seedQueueThreadPriority)));
            } catch (Throwable ignored) {
            }
            IntegratedServer integratedServer = new IntegratedServer(
               thread, minecraft, access, packs, stem, Optional.empty(), minecraft.services(), listener
            );
            ((QueueServer) integratedServer).seedqueue$setEntry(this);
            return integratedServer;
         });
      } catch (Throwable t) {
         state = State.FAILED;
         error = t.toString();
         SeedQueue.LOGGER.error("[seedqueue] failed to start entry #{}", number, t);
      }
   }

   private void captureMap(net.minecraft.server.level.ServerLevel level, int spawnChunkX, int spawnChunkZ) {
      try {
         int[] colors = new int[mapWidth * mapHeight];
         int[] heights = new int[mapWidth * mapHeight];
         int centerX = spawnChunkX * 16 + 8;
         int centerZ = spawnChunkZ * 16 + 8;
         int step = 2;

         for (int j = 0; j < mapHeight; j++) {
            for (int i = 0; i < mapWidth; i++) {
               int wx = centerX + (i - mapWidth / 2) * step;
               int wz = centerZ + (j - mapHeight / 2) * step;
               var chunk = level.getChunk(wx >> 4, wz >> 4);
               int height = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx & 15, wz & 15);
               var state = chunk.getBlockState(new net.minecraft.core.BlockPos(wx, Math.max(height - 1, level.getMinY()), wz));
               colors[j * mapWidth + i] = colorFor(state);
               heights[j * mapWidth + i] = height;
            }
         }

         mapHeights = heights;
         mapColors = colors;
         cameraX = centerX + 0.5;
         cameraZ = centerZ + 0.5;
         cameraY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, centerX, centerZ) + 1.7;
         if (!Boolean.parseBoolean(System.getProperty("seedqueue.freezeCapture", "true"))) {
            capturePerspective();
         }
         SeedQueue.LOGGER.info("[seedqueue] entry #{} captured preview map", number);
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to capture preview map for #{}", number, t);
      }
   }

   /** Renders a simple first-person-like view of the sampled terrain into a pixel buffer. */
   private void capturePerspective() {
      try {
         int[] heights = mapHeights;
         int[] colors = mapColors;

         if (heights == null || colors == null) {
            return;
         }

         int[] pixels = new int[previewWidth * previewHeight];
         double fov = Math.toRadians(70.0);
         double focal = previewWidth / (2.0 * Math.tan(fov / 2.0));
         double horizon = previewHeight * 0.45;
         double maxDistance = Math.max(mapWidth, mapHeight) * 2.0;

         for (int screenX = 0; screenX < previewWidth; screenX++) {
            double angle = Math.atan((screenX - previewWidth / 2.0) / focal);
            double dirX = Math.sin(angle);
            double dirZ = Math.cos(angle);

            for (int screenY = 0; screenY < previewHeight; screenY++) {
               double pitch = Math.atan((horizon - screenY) / focal);
               int pixel = skyColor(screenY);

               for (double dist = 1.0; dist <= maxDistance; dist += 1.0) {
                  double worldX = cameraX + dirX * dist;
                  double worldZ = cameraZ + dirZ * dist;
                  double worldY = cameraY + Math.tan(pitch) * dist;
                  int column = (int)Math.floor((worldX - (cameraX - mapWidth)) / 2.0);
                  int row = (int)Math.floor((worldZ - (cameraZ - mapHeight)) / 2.0);

                  if (column < 0 || column >= mapWidth || row < 0 || row >= mapHeight) {
                     continue;
                  }

                  int index = row * mapWidth + column;
                  double terrain = heights[index];

                  if (worldY <= terrain) {
                     double fog = Math.max(0.35, 1.0 - dist / maxDistance);
                     pixel = applyFog(colors[index], fog);
                     break;
                  }
               }

               pixels[screenY * previewWidth + screenX] = pixel;
            }
         }

         previewPixels = pixels;
         SeedQueue.LOGGER.info("[seedqueue] rendered perspective preview for #{} ({}x{})", number, previewWidth, previewHeight);
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to render perspective preview for #{}", number, t);
      }
   }

   private static int skyColor(int screenY) {
      int t = Math.max(0, Math.min(255, screenY * 255 / 90));
      int r = 120 + t * 100 / 255;
      int g = 170 + t * 75 / 255;
      int b = 235;
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static int applyFog(int color, double factor) {
      int r = (int)(((color >> 16) & 0xFF) * factor);
      int g = (int)(((color >> 8) & 0xFF) * factor);
      int b = (int)((color & 0xFF) * factor);
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static int colorFor(net.minecraft.world.level.block.state.BlockState state) {
      String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();

      if (id.contains("water")) {
         return 0xFF3F76E4;
      } else if (id.contains("lava")) {
         return 0xFFD45A12;
      } else if (id.contains("sand")) {
         return 0xFFE7D8A0;
      } else if (id.contains("snow")) {
         return 0xFFF0F0F0;
      } else if (id.contains("grass") || id.contains("leaves") || id.contains("moss")) {
         return 0xFF5B8C3E;
      } else if (id.contains("log") || id.contains("wood")) {
         return 0xFF6B4F2A;
      } else if (id.contains("stone") || id.contains("deepslate") || id.contains("gravel")) {
         return 0xFF888888;
      } else if (id.contains("dirt")) {
         return 0xFF8B6B4A;
      } else {
         return 0xFF7A7A7A;
      }
   }

   public void tick() {
      if (state == State.STARTING && server != null && server.isReady()) {
         startGenerating();
      }
   }

   /** Allows or pauses chunk generation; called by SeedQueue's per-tick generation scheduler. */
   public void permit(boolean permit, boolean finish) {
      synchronized (this.generationGate) {
         this.generationPermit = permit;
         this.forceFinish = finish;
         this.generationGate.notifyAll();
      }
   }

   /**
    * Blocks the server thread between chunks while the queue is at its generation limit. Mirrors the
    * 1.21.4 waitForGenerationPermit: maxWorldGenerationPercentage pauses the world, resumeOnFilledQueue
    * (forceFinish) or locking/playing it lets it finish.
    */
   /**
    * Waits for a generation permit. Returns {@code false} when the world reached
    * maxWorldGenerationPercentage and should stop generating here: the already generated chunks are
    * captured, the entry becomes READY and the remaining chunks generate on demand when it is played.
    * Locking/scheduling/playing it (or resumeOnFilledQueue) sets forceFinish so it keeps going to 100%.
    */
   public boolean waitForGenerationPermit() {
      synchronized (this.generationGate) {
         while (this.state != State.DISCARDED && !this.selected) {
            boolean limitHit = QueueConfig.maxWorldGenerationPercentage < 100
               && this.chunksTotal > 0
               && this.chunksGenerated * 100L >= (long) this.chunksTotal * QueueConfig.maxWorldGenerationPercentage;

            if (limitHit) {
               this.reachedLimit = true;
               this.paused = false;
               // Resume On Filled Queue: once every queue slot is taken, finish the world instead of
               // stopping at the generation limit (1.16.1/1.21.4 behaviour).
               boolean resumeFilledQueue = QueueConfig.resumeOnFilledQueue && SeedQueue.queueIsFull();
               return this.forceFinish || this.locked || this.scheduledJoin || resumeFilledQueue;
            }

            if (this.generationPermit) {
               this.paused = false;
               return true;
            }

            this.paused = true;

            try {
               this.generationGate.wait(50L);
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
               break;
            }
         }

         this.paused = false;
         return false;
      }
   }

   /** Generates the rings beyond the preview radius one at a time and publishes them to the wall. */
   private void extendGeneration(
      net.minecraft.server.level.ServerLevel level,
      net.minecraft.server.level.ServerChunkCache chunkSource,
      int spawnX,
      int spawnZ,
      int baseRadius
   ) {
      // Off by default: changing the preview client's view radius after the card is shown made the
      // existing terrain disappear. Opt in with -Dseedqueue.extendPreviewRadius=N.
      int extra = Math.max(0, Math.min(Integer.getInteger("seedqueue.extendPreviewRadius", 0), (16 - baseRadius) / 2));
      int target = baseRadius + extra;

      for (int ring = baseRadius + 1; ring <= target; ring++) {
         if (this.selected || this.state == State.DISCARDED) {
            return;
         }

         for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                  continue;
               }

               if (this.selected || this.state == State.DISCARDED) {
                  return;
               }

               level.getChunk(spawnX + dx, spawnZ + dz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
               net.minecraft.world.level.chunk.LevelChunk chunk =
                  (net.minecraft.world.level.chunk.LevelChunk)chunkSource.getChunk(
                     spawnX + dx, spawnZ + dz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false
                  );

               if (chunk != null) {
                  try {
                     this.pendingPackets.add(
                        new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                           chunk, level.getLightEngine(), null, null
                        )
                     );
                  } catch (Throwable ignored) {
                  }
               }
            }
         }

         try {
            Thread.sleep(20L);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
         }
      }

      if (!this.pendingPackets.isEmpty()) {
         SeedQueue.LOGGER.info(
            "[seedqueue] entry #{} generated {} extra chunks beyond preview radius", number, this.pendingPackets.size()
         );
      }
   }

   /** Freezes the instance: the background server stops ticking but stays in memory. */
   public void park() {
      if (state == State.READY && server != null && !selected) {
         parked = true;
         frozen = true;
         SeedQueue.LOGGER.info("[seedqueue] entry #{} frozen (server parked, world kept loaded)", number);
      }
   }

   /** Resumes a parked server; used when the player selects this entry. */
   public void select() {
      selected = true;
      parked = false;
      synchronized (parkLock) {
         parkLock.notifyAll();
      }
   }

   private boolean parkLogged;
   private boolean resumeLogged;

   private boolean prioritySet;

   /** Blocks the server thread while the entry is parked at the wall. */
   public void awaitUnpark() {
      if (!prioritySet) {
         prioritySet = true;

         try {
            Thread.currentThread().setPriority(Math.max(1, Math.min(10, QueueConfig.serverThreadPriority)));
         } catch (Throwable ignored) {
         }
      }

      if (!parked && !frozen) {
         return;
      }

      synchronized (parkLock) {
         if (!parkLogged) {
            parkLogged = true;
            SeedQueue.LOGGER.info("[seedqueue] entry #{} server thread parked (frozen={})", number, frozen);
         }

         boolean waited = false;

         while (parked && !selected && state != State.DISCARDED) {
            waited = true;

            try {
               parkLock.wait(50L);
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
               return;
            }
         }

         if (waited && selected && !resumeLogged) {
            resumeLogged = true;
            SeedQueue.LOGGER.info("[seedqueue] entry #{} server thread resumed", number);
         }
      }
   }

   /**
    * Hands this entry's live integrated server to the client. No world is loaded from disk: the
    * server already has the spawn chunks, so the vanilla loading screen never appears.
    */
   public boolean play(Minecraft minecraft) {
      if (state != State.READY || transferred || server == null || access == null || stem == null) {
         return false;
      }

      SeedQueue.joining = this;
      select();

      try {
         minecraft.doWorldLoad(access, packs, stem, java.util.Optional.empty(), true);
         transferred = true;

         SeedQueue.lastPlayedFolder = folder;

         // Mark the world as played so the singleplayer list keeps it and startup cleanup skips it.
         try {
            if (worldDirectory != null) {
               java.nio.file.Files.writeString(
                  worldDirectory.resolve("seedqueue_played.marker"), Long.toString(System.currentTimeMillis())
               );
            }
         } catch (Throwable t) {
            SeedQueue.LOGGER.warn("[seedqueue] failed to mark entry #{} as played", number, t);
         }

         // Ownership of access/stem/server moved to the client's integrated server.
         access = null;
         stem = null;
         packs = null;
         server = null;
         SeedQueue.LOGGER.info("[seedqueue] entry #{} handed to the client (no world load)", number);
         return true;
      } catch (Throwable t) {
         SeedQueue.LOGGER.error("[seedqueue] failed to hand entry #{} to the client", number, t);
         return false;
      } finally {
         SeedQueue.joining = null;
      }
   }

   private void startGenerating() {
      state = State.GENERATING;
      this.reachedLimit = false;
      int radius = chunkRadius;
      int diameter = radius * 2 + 1;
      chunksTotal = diameter * diameter;
      long started = System.currentTimeMillis();
      SeedQueue.LOGGER.info("[seedqueue] entry #{} generating {} chunks", number, chunksTotal);
      server.execute(() -> {
         try {
            var level = server.overworld();
            var chunkSource = level.getChunkSource();

            // Vanilla picks the join position with PlayerSpawnFinder, which randomises the candidate
            // when respawn_radius > 0 - the preview could never match that. With radius 0 there is a
            // single candidate (the spawn column), which the preview reproduces exactly.
            try {
               server.getGameRules().set(
                  net.minecraft.world.level.gamerules.GameRules.RESPAWN_RADIUS, 0, server
               );
            } catch (Throwable t) {
               SeedQueue.LOGGER.warn("[seedqueue] failed to pin respawn radius", t);
            }

            var spawnPos = level.getRespawnData().pos();
            int spawnX = spawnPos.getX() >> 4;
            int spawnZ = spawnPos.getZ() >> 4;

            // Generate from the spawn chunk outwards; the packet snapshot is taken afterwards so the
            // light engine has fully propagated (capturing mid-generation leaves unlit, black chunks).
            generateLoop:
            for (int ring = 0; ring <= radius; ring++) {
               for (int dx = -radius; dx <= radius; dx++) {
                  for (int dz = -radius; dz <= radius; dz++) {
                     if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                     }

                     if (!waitForGenerationPermit()) {
                        break generateLoop;
                     }

                     if (state == State.DISCARDED) {
                        return;
                     }

                     chunkSource.getChunk(
                        spawnX + dx, spawnZ + dz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true
                     );
                     chunksGenerated++;

                     // Chunkmap Freezing: refresh the map while generating until the configured time,
                     // then stop updating it (the overlay keeps showing the frozen snapshot).
                     long elapsed = System.currentTimeMillis() - started;

                     if (elapsed - this.lastMapCaptureAt >= 250L
                        && (QueueConfig.chunkMapFreezing < 0 || elapsed < QueueConfig.chunkMapFreezing)) {
                        this.lastMapCaptureAt = elapsed;
                        this.captureMap(level, spawnX, spawnZ);
                     }
                  }
               }
            }

            try {
               for (int i = 0; i < 128 && level.getLightEngine().hasLightWork(); i++) {
                  level.getLightEngine().runLightUpdates();
               }
            } catch (Throwable t) {
               SeedQueue.LOGGER.warn("[seedqueue] failed to flush light before capturing preview chunks", t);
            }

            for (int ring = 0; ring <= radius; ring++) {
               for (int dx = -radius; dx <= radius; dx++) {
                  for (int dz = -radius; dz <= radius; dz++) {
                     if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                     }

                     net.minecraft.world.level.chunk.LevelChunk chunk =
                        (net.minecraft.world.level.chunk.LevelChunk)chunkSource.getChunk(
                           spawnX + dx, spawnZ + dz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false
                        );

                     if (chunk != null) {
                        try {
                           chunkPackets.add(
                              new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                                 chunk, level.getLightEngine(), null, null
                              )
                           );
                        } catch (Throwable ignored) {
                        }
                     }
                  }
               }
            }

            captureMap(level, spawnX, spawnZ);
            state = State.READY;
            readyAtMillis = System.currentTimeMillis();

            // World preview is now visible: keep generating outer rings in the background and feed
            // the new chunks to the preview so the cards fill out further over time.
            try {
               extendGeneration(level, chunkSource, spawnX, spawnZ, radius);
            } catch (Throwable t) {
               SeedQueue.LOGGER.warn("[seedqueue] entry #{} extended generation failed", number, t);
            }
            park();
            SeedQueue.LOGGER.info(
               "[seedqueue] entry #{} ready (seed {}, {} chunks in {} ms)",
               number,
               seed,
               chunksGenerated,
               System.currentTimeMillis() - started
            );

            SeedQueue.logMemory("after entry #" + number + " ready");
         } catch (Throwable t) {
            state = State.FAILED;
            error = t.toString();
            SeedQueue.LOGGER.error("[seedqueue] entry #{} chunk generation failed", number, t);
         }
      });
   }

   /** Uploads a frame captured from the wall preview as this card's GUI texture. */
   public void uploadCardImage(com.mojang.blaze3d.platform.NativeImage image) {
      try {
         if (Boolean.getBoolean("seedqueue.dumpCaptures")) {
            try {
               image.writeToFile(new java.io.File("seedqueue_card_" + number + ".png"));
            } catch (Throwable ignored) {
            }
         }

         captureWidth = image.getWidth();
         captureHeight = image.getHeight();
         var texture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "seedqueue card " + number, image);
         net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
            "seedqueue", "card_" + number + "_" + previewUploads++
         );
         net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();

         if (previousPreviewTexture != null) {
            try {
               minecraft.getTextureManager().release(previousPreviewTexture);
            } catch (Throwable ignored) {
            }
         }

         previousPreviewTexture = previewTexture;
         minecraft.getTextureManager().register(id, texture);
         previewTexture = id;
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to upload card image for #{}", number, t);
      }
   }

   /** Uploads a captured world frame as a GUI texture (client thread only). */
   public void uploadNativePreview() {
      if (nativePreview == null) {
         return;
      }

      try {
         com.mojang.blaze3d.platform.NativeImage image = nativePreview;
         nativePreview = null;

         if (Boolean.getBoolean("seedqueue.dumpCaptures")) {
            try {
               image.writeToFile(new java.io.File("seedqueue_capture_" + number + ".png"));
            } catch (Throwable ignored) {
            }
         }

         captureWidth = image.getWidth();
         captureHeight = image.getHeight();
         var texture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "seedqueue preview", image);
         net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
            "seedqueue", "capture_" + number + "_" + previewUploads++
         );
         net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();

         // Release one generation late: the GUI may still reference the texture for the current frame.
         if (previousPreviewTexture != null) {
            try {
               minecraft.getTextureManager().release(previousPreviewTexture);
            } catch (Throwable ignored) {
            }
         }

         previousPreviewTexture = previewTexture;
         minecraft.getTextureManager().register(id, texture);
         previewTexture = id;
         SeedQueue.LOGGER.info("[seedqueue] uploaded captured preview for entry #{}", number);
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to upload captured preview for #{}", number, t);
      }
   }

   /** Uploads the generated preview image as a GUI texture (client thread only). */
   public void uploadPreview() {
      if (previewPixels == null || previewTexture != null) {
         return;
      }

      try {
         com.mojang.blaze3d.platform.NativeImage image = new com.mojang.blaze3d.platform.NativeImage(
            previewWidth, previewHeight, true
         );

         for (int y = 0; y < previewHeight; y++) {
            for (int x = 0; x < previewWidth; x++) {
               int argb = previewPixels[y * previewWidth + x];
               int abgr = (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
               image.setPixelABGR(x, y, abgr);
            }
         }

         try {
            image.writeToFile(new java.io.File("seedqueue_capture_" + number + ".png"));
         } catch (Throwable ignored) {
         }

         captureWidth = image.getWidth();
         captureHeight = image.getHeight();
         var texture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "seedqueue preview", image);
         net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
            "seedqueue", "preview_" + number
         );
         net.minecraft.client.Minecraft.getInstance().getTextureManager().register(id, texture);
         previewTexture = id;
         SeedQueue.LOGGER.info("[seedqueue] uploaded preview texture for entry #{}", number);
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to upload preview for #{}", number, t);
      }
   }

   /**
    * Releases the background server and storage lock so the client can open the world once to
    * take a screenshot. The entry stays READY and can still be played from disk.
    */
   public boolean releaseForScreenshot() {
      try {
         if (server != null) {
            server.halt(true);
            server = null;
         }

         if (stem != null) {
            stem.close();
            stem = null;
         }

         if (access != null) {
            access.close();
            access = null;
         }

         SeedQueue.LOGGER.info("[seedqueue] entry #{} released for screenshot", number);
         return true;
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to release entry #{}", number, t);
         return false;
      }
   }

   public void discard() {
      if (state == State.DISCARDED) {
         return;
      }

      State previous = state;
      state = State.DISCARDED;

      if (this.externalPreview != null && this.externalPreview.level != null) {
         // The Sodium section manager cached for this preview level is destroyed on the render thread.
         SodiumLevelCache.evictLater(this.externalPreview.level);
      }

      parked = false;
      synchronized (parkLock) {
         parkLock.notifyAll();
      }
      synchronized (generationGate) {
         generationGate.notifyAll();
      }
      SeedQueue.LOGGER.info("[seedqueue] discarding entry #{} (was {})", number, previous);

      if (previewTexture != null) {
         try {
            net.minecraft.client.Minecraft.getInstance().getTextureManager().release(previewTexture);
         } catch (Throwable ignored) {
         }

         previewTexture = null;
      }

      if (nativePreview != null) {
         try {
            nativePreview.close();
         } catch (Throwable ignored) {
         }

         nativePreview = null;
      }

      if (transferred) {
         SeedQueue.LOGGER.info("[seedqueue] entry #{} was transferred to the client, keeping its files", number);
         return;
      }

      try {
         if (server != null) {
            server.halt(true);
         }
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to halt server #{}", number, t);
      }

      try {
         if (stem != null) {
            stem.close();
         }
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to close stem #{}", number, t);
      }

      try {
         if (access != null) {
            access.safeClose();
         }
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to close access #{}", number, t);
      }

      try {
         if (worldDirectory != null
            && worldDirectory.isAbsolute()
            && java.nio.file.Files.exists(worldDirectory)
            && worldDirectory.getFileName() != null
            && worldDirectory.getFileName().toString().startsWith("SeedQueue-")) {
            try (var paths = java.nio.file.Files.walk(worldDirectory)) {
               paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                  try {
                     java.nio.file.Files.deleteIfExists(path);
                  } catch (Throwable ignored) {
                  }
               });
            }

            SeedQueue.LOGGER.info("[seedqueue] deleted world folder {}", worldDirectory);
         }
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to delete world folder #{}", number, t);
      }
   }
}