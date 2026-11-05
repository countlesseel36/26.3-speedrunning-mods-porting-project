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
   public volatile long lastRenderedAt;
   public volatile long lastPreviewAt;
   public volatile int captureWidth;
   private net.minecraft.resources.Identifier previousPreviewTexture;
   public volatile int captureHeight;
   private int previewUploads;
   public volatile boolean previewActive;
   public volatile boolean shotCaptured;
   public volatile boolean frozen;
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

   public void prepare(Minecraft minecraft) {
      state = State.PREPARING;

      try {
         worldDirectory = minecraft.getLevelSource().getBaseDir().resolve(folder);
         access = minecraft.getLevelSource().validateAndCreateAccess(folder);
         packs = ServerPacksSource.createPackRepository(access);
         LevelSettings settings = new LevelSettings(
            folder, GameType.SURVIVAL, LevelSettings.DifficultySettings.DEFAULT, false, WorldDataConfiguration.DEFAULT
         );
         WorldOptions options = new WorldOptions(seed, true, false);
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
         capturePerspective();
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

   private void startGenerating() {
      state = State.GENERATING;
      int radius = chunkRadius;
      int diameter = radius * 2 + 1;
      chunksTotal = diameter * diameter;
      long started = System.currentTimeMillis();
      SeedQueue.LOGGER.info("[seedqueue] entry #{} generating {} chunks", number, chunksTotal);
      server.execute(() -> {
         try {
            var level = server.overworld();
            var chunkSource = level.getChunkSource();
            var spawnPos = level.getRespawnData().pos();
            int spawnX = spawnPos.getX() >> 4;
            int spawnZ = spawnPos.getZ() >> 4;

            // Generate from the spawn chunk outwards and keep a packet snapshot of every chunk so
            // the client can build a preview of the still-generating world.
            for (int ring = 0; ring <= radius; ring++) {
               for (int dx = -radius; dx <= radius; dx++) {
                  for (int dz = -radius; dz <= radius; dz++) {
                     if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                     }

                     net.minecraft.world.level.chunk.LevelChunk chunk =
                        (net.minecraft.world.level.chunk.LevelChunk)chunkSource.getChunk(
                           spawnX + dx, spawnZ + dz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true
                        );
                     chunksGenerated++;

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
            SeedQueue.LOGGER.info(
               "[seedqueue] entry #{} ready (seed {}, {} chunks in {} ms)",
               number,
               seed,
               chunksGenerated,
               System.currentTimeMillis() - started
            );
         } catch (Throwable t) {
            state = State.FAILED;
            error = t.toString();
            SeedQueue.LOGGER.error("[seedqueue] entry #{} chunk generation failed", number, t);
         }
      });
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