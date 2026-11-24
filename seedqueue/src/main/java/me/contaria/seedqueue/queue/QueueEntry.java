package me.contaria.seedqueue.queue;
import me.contaria.seedqueue.SeedQueue;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import me.voidxwalker.worldpreview.ExternalPreview;
import me.voidxwalker.worldpreview.WorldPreviewProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.WorldLoader.DataLoadOutput;
import net.minecraft.server.WorldLoader.InitConfig;
import net.minecraft.server.WorldLoader.PackConfig;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.level.progress.LevelLoadListener.Stage;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.LevelSettings.DifficultySettings;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.WorldDimensions.Complete;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.LevelDataAndDimensions.WorldDataAndGenSettings;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

public final class QueueEntry {
   public final int number;
   public final long seed;
   public final String folder;
   public volatile QueueEntry.State state = QueueEntry.State.CREATED;
   public long createdAt = System.currentTimeMillis();
   long prepareT0;
   long serverMs;
   long genMs;
   boolean genInitialized;
   int genRingCursor;
   int genSpawnX;
   int genSpawnZ;
   ServerLevel genLevel;
   ServerChunkCache genChunkSource;
   public volatile long readyAt;
   public volatile long discardStartAt;
   private volatile boolean deleteAfterPrepare;
   public volatile LevelStorageAccess access;
   public volatile PackRepository packs;
   public volatile WorldStem stem;
   public volatile IntegratedServer server;
   public volatile boolean locked;
   public volatile boolean transferred;
   public volatile boolean scheduledJoin;
   public volatile long scheduledJoinAt;
   public final List<ClientboundLevelChunkWithLightPacket> pendingPackets = Collections.synchronizedList(new ArrayList<>());
   public volatile boolean reachedLimit;
   public volatile boolean paused;
   public volatile long lockedAt;
   long lastMapCaptureAt;
   public volatile boolean startQueued;
   private final Object generationGate = new Object();
   private boolean generationPermit;
   private boolean forceFinish;
   public volatile String error;
   public volatile Path worldDirectory;
   public volatile int chunkRadius;
   public volatile int[] mapColors;
   public volatile int[] mapHeights;
   public volatile Identifier previewTexture;
   public volatile NativeImage nativePreview;
   public volatile ExternalPreview externalPreview;
   public volatile WorldPreviewProperties renderProperties;
   public volatile RenderTarget renderTarget;
   public volatile RenderTarget displayTarget;
   public volatile boolean sectionsPrepared;
   public volatile long lastRenderedAt;
   public volatile long lastPreviewAt;
   public volatile int captureWidth;
   Identifier previousPreviewTexture;
   public volatile int captureHeight;
   int previewUploads;
   public volatile boolean frozen;
   public volatile boolean parked;
   public volatile boolean selected;
   private final Object parkLock;
   public volatile long readyAtMillis;
   public final List<ClientboundLevelChunkWithLightPacket> chunkPackets;
   double cameraX;
   double cameraY;
   double cameraZ;
   public volatile int chunksGenerated;
   public volatile int chunksTotal;
   public volatile boolean genPausedForPlay;
   private boolean parkLogged;
   private boolean resumeLogged;
   private boolean prioritySet;

   public MinecraftServer server() {
      return this.server;
   }

   public QueueEntry(int count, long value) {
      this.chunkRadius = Integer.getInteger("seedqueue.chunkRadius", QueueConfig.chunkRadius);
      this.parkLock = new Object();
      this.chunkPackets = Collections.synchronizedList(new ArrayList<>());
      this.number = count;
      this.seed = value;
      this.folder = "SeedQueue-" + System.currentTimeMillis() + "-" + count + "-" + String.format("%08x", ThreadLocalRandom.current().nextInt());
   }

   private static int atumDifficultyId() {
      return EntryWorldData.atumDifficultyId();
   }

   private static int atumGeneratorType() {
      return EntryWorldData.atumGeneratorType();
   }

   private static Holder<WorldPreset> selectPreset(RegistryLookup<WorldPreset> holder, int count) {
      return EntryWorldData.selectPreset(holder, count);
   }

   private static Difficulty difficultyOf(int count) {
      return EntryWorldData.difficultyOf(count);
   }

   public void prepare(Minecraft minecraft) {
      this.state = QueueEntry.State.PREPARING;
      this.prepareT0 = System.currentTimeMillis();

      try {
         this.worldDirectory = minecraft.getLevelSource().getBaseDir().resolve(this.folder);

         try {
            Files.createDirectories(this.worldDirectory);
            Files.writeString(this.worldDirectory.resolve(".seedqueue-temporary"), Long.toString((long)this.number));
         } catch (Throwable error) {
            SeedQueue.LOGGER.warn("[seedqueue] failed to mark entry #{} as temporary", this.number, error);
         }

         this.access = minecraft.getLevelSource().validateAndCreateAccess(this.folder);
         this.packs = ServerPacksSource.createPackRepository(this.access);
         int count = atumDifficultyId();
         boolean flag = count == -1;
         LevelSettings levelSettings = new LevelSettings(
            this.folder, GameType.SURVIVAL, new DifficultySettings(difficultyOf(count), flag, false), false, WorldDataConfiguration.DEFAULT
         );
         SeedQueue.LOGGER.info("[seedqueue] entry #{} difficulty from Atum: id={} -> {} (hardcore={})", this.number, count, difficultyOf(count), flag);
         boolean flag2 = true;
         boolean flag3 = false;

         try {
            Class clazz = Class.forName("me.voidxwalker.autoreset.Atum");
            flag2 = clazz.getField("structures").getBoolean(null);
            flag3 = clazz.getField("bonusChest").getBoolean(null);
         } catch (Throwable error2) {
            SeedQueue.LOGGER.warn("[seedqueue] could not read Atum world settings, using defaults", error2);
         }

         WorldOptions worldOptions = new WorldOptions(this.seed, flag2, flag3);
         PackConfig packConfig = new PackConfig(this.packs, levelSettings.dataConfiguration(), false, false);
         InitConfig initConfig = new InitConfig(packConfig, CommandSelection.INTEGRATED, PermissionSet.ALL_PERMISSIONS);
         this.stem = (WorldStem)WorldLoader.load(
               initConfig,
               arg -> {
                  int generatorType = atumGeneratorType();
                  RegistryLookup registryLookup = arg.datapackWorldRegistries().lookupOrThrow(Registries.WORLD_PRESET);
                  Holder holder = selectPreset(registryLookup, generatorType);
                  WorldDimensions worldDimensions = ((WorldPreset)(holder != null ? holder : registryLookup.getOrThrow(WorldPresets.NORMAL)).value()).createWorldDimensions();
                  SeedQueue.LOGGER
                     .info(
                        "[seedqueue] entry #{} world preset from Atum: generatorType={} -> {}",
                        this.number,
                        generatorType,
                        holder == null ? "NORMAL(fallback)" : holder.unwrapKey().orElse(null)
                     );
                  Complete complete = worldDimensions.bake(arg.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM));
                  PrimaryLevelData primaryLevelData = new PrimaryLevelData(levelSettings, complete.specialWorldProperty(), complete.lifecycle());
                  WorldGenSettings worldGenSettings = new WorldGenSettings(worldOptions, worldDimensions);
                  return new DataLoadOutput(new WorldDataAndGenSettings(primaryLevelData, worldGenSettings), complete.dimensionsRegistryAccess());
               },
               WorldStem::new,
               Util.backgroundExecutor(),
               Util.backgroundExecutor()
            )
            .get();
         this.access.saveDataTag(this.stem.worldDataAndGenSettings().data());
         this.serverMs = System.currentTimeMillis() - this.prepareT0;
         this.state = QueueEntry.State.PREPARED;
         SeedQueue.LOGGER.info("[seedqueue] prepared entry #{} ({}) with seed {}", this.number, this.folder,
            this.stem.worldDataAndGenSettings().genSettings().options().seed());
      } catch (Throwable error3) {
         this.state = QueueEntry.State.FAILED;
         this.error = error3.toString();
         SeedQueue.LOGGER.error("[seedqueue] failed to prepare entry #{}", this.number, error3);
      }

      if (this.deleteAfterPrepare) {
         this.deleteAfterPrepare = false;
         SeedQueue.LOGGER.info("[seedqueue] deleting folder of discarded entry #{} after prepare", this.number);
         this.deleteWorldFolder();
      }
   }

   public void start(Minecraft minecraft) {
      if (this.state == QueueEntry.State.PREPARED) {
         this.state = QueueEntry.State.STARTING;
         this.startQueued = false;
         LevelLoadListener listener = new LevelLoadListener() {
            public void start(Stage minecraft, int listener) {
            }

            public void update(Stage minecraft, int listener, int count) {
            }

            public void finish(Stage minecraft) {
            }

            public void updateFocus(ResourceKey<Level> minecraft, ChunkPos listener) {
            }
         };

         try {
            this.server = (IntegratedServer)MinecraftServer.spin(count -> {
               count.setName("SeedQueue server " + this.number);

               try {
                  count.setPriority(Math.max(1, Math.min(10, QueueConfig.seedQueueThreadPriority)));
               } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }

               IntegratedServer integratedServer = new IntegratedServer(count, minecraft, this.access, this.packs, this.stem, Optional.empty(), minecraft.services(), listener);
               ((QueueServer)integratedServer).seedqueue$setEntry(this);
               return integratedServer;
            });
         } catch (Throwable error2) {
            this.state = QueueEntry.State.FAILED;
            this.error = error2.toString();
            SeedQueue.LOGGER.error("[seedqueue] failed to start entry #{}", this.number, error2);
         }
      }
   }

   void captureMap(ServerLevel level, int chunkX, int chunkZ) {
      EntryCapture.captureMap(this, level, chunkX, chunkZ);
   }

   void capturePerspective() {
      EntryCapture.capturePerspective(this);
   }

   private static  int skyColor(int count) {
      return EntryCapture.skyColor(count);
   }

   private static  int applyFog(int count, double ratio) {
      return EntryCapture.applyFog(count, ratio);
   }

   private static  int colorFor(BlockState blockState) {
      return EntryCapture.colorFor(blockState);
   }

   public void tick() {
      if (this.state == QueueEntry.State.STARTING && this.server != null && this.server.isReady()) {
         this.startGenerating();
      }
   }

   public void permit(boolean flag, boolean flag2) {
      synchronized (this.generationGate) {
         this.generationPermit = flag;
         this.forceFinish = flag2;
         this.generationGate.notifyAll();
      }
   }

   public void resumeGeneration() {
      boolean flag = this.state == QueueEntry.State.GENERATING || this.state == QueueEntry.State.PREPARING || this.state == QueueEntry.State.STARTING;
      if (this.genPausedForPlay && this.server != null && flag) {
         this.genPausedForPlay = false;
         SeedQueue.LOGGER.info("[seedqueue] entry #{} generation resumed ({}/{} chunks)", this.number, this.chunksGenerated, this.chunksTotal);
         this.server.execute(this::generateStep);
      }
   }

   public boolean waitForGenerationPermit() {
      synchronized (this.generationGate) {
         while (this.state != QueueEntry.State.DISCARDED && !this.selected) {
            boolean flag = QueueConfig.maxWorldGenerationPercentage < 100
               && this.chunksTotal > 0
               && (long)this.chunksGenerated * 100L >= (long)this.chunksTotal * (long)QueueConfig.maxWorldGenerationPercentage;
            if (flag) {
               this.reachedLimit = true;
               this.paused = false;
               boolean flag2 = QueueConfig.resumeOnFilledQueue && SeedQueue.queueIsFull();
               return this.forceFinish || this.locked || this.scheduledJoin || flag2;
            }

            if (this.generationPermit) {
               this.paused = false;
               return true;
            }

            this.paused = true;

            try {
               this.generationGate.wait(50L);
            } catch (InterruptedException interruptedException) {
               Thread.currentThread().interrupt();
               break;
            }
         }

         this.paused = false;
         return false;
      }
   }

   void extendGeneration(ServerLevel level, ServerChunkCache chunkSource, int count, int count2, int count3) {
      EntryGeneration.extendGeneration(this, level, chunkSource, count, count2, count3);
   }

   public void park() {
      if (this.state == QueueEntry.State.READY && this.server != null && !this.selected && !this.transferred && this != SeedQueue.liveEntry()) {
         this.parked = true;
         this.frozen = true;
         SeedQueue.LOGGER.info("[seedqueue] entry #{} frozen (server parked, world kept loaded)", this.number);
      }
   }

   public void select() {
      this.selected = true;
      this.parked = false;
      synchronized (this.parkLock) {
         this.parkLock.notifyAll();
      }
   }

   public void awaitUnpark() {
      if (!this.prioritySet) {
         this.prioritySet = true;

         try {
            Thread.currentThread().setPriority(Math.max(1, Math.min(10, QueueConfig.serverThreadPriority)));
         } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }
      }

      if (this.parked || this.frozen) {
         synchronized (this.parkLock) {
            if (!this.parkLogged) {
               this.parkLogged = true;
               SeedQueue.LOGGER.info("[seedqueue] entry #{} server thread parked (frozen={})", this.number, this.frozen);
            }

            boolean flag = false;

            while (this.parked && !this.selected && this.state != QueueEntry.State.DISCARDED) {
               flag = true;

               try {
                  this.parkLock.wait(50L);
               } catch (InterruptedException interruptedException) {
                  Thread.currentThread().interrupt();
                  return;
               }
            }

            if (flag && this.selected && !this.resumeLogged) {
               this.resumeLogged = true;
               SeedQueue.LOGGER.info("[seedqueue] entry #{} server thread resumed", this.number);
            }
         }
      }
   }

   public boolean play(Minecraft minecraft) {
      if (this.state == QueueEntry.State.READY && !this.transferred && this.server != null && this.access != null && this.stem != null) {
         SeedQueue.joining = this;
         this.select();

         boolean flag;
         try {
            minecraft.doWorldLoad(this.access, this.packs, this.stem, Optional.empty(), true);
            this.transferred = true;

            try {
               Files.deleteIfExists(this.worldDirectory.resolve(".seedqueue-temporary"));
               SeedQueue.LOGGER.info("[seedqueue] entry #{} is now permanent (marker removed)", this.number);
            } catch (Throwable error) {
               SeedQueue.LOGGER.warn("[seedqueue] failed to make entry #{} permanent", this.number, error);
            }

            SeedQueue.lastPlayedFolder = this.folder;

            try {
               if (this.worldDirectory != null) {
                  Files.writeString(this.worldDirectory.resolve("seedqueue_played.marker"), Long.toString(System.currentTimeMillis()));
               }
            } catch (Throwable error2) {
               SeedQueue.LOGGER.warn("[seedqueue] failed to mark entry #{} as played", this.number, error2);
            }

            this.access = null;
            this.stem = null;
            this.packs = null;
            this.server = null;
            SeedQueue.LOGGER.info("[seedqueue] entry #{} handed to the client (no world load)", this.number);
            return true;
         } catch (Throwable error3) {
            SeedQueue.LOGGER.error("[seedqueue] failed to hand entry #{} to the client", this.number, error3);
            flag = false;
         } finally {
            SeedQueue.joining = null;
         }

         return flag;
      } else {
         return false;
      }
   }

   int effectiveRadius() {
      return EntryGeneration.effectiveRadius(this);
   }

   private void startGenerating() {
      EntryGeneration.startGenerating(this);
   }

   private void generateStep() {
      EntryGeneration.generateStep(this);
   }

   private void destroyTarget(RenderTarget target, String text) {
      if (target != null) {
         Runnable runnable = () -> {
            try {
               target.destroyBuffers();
            } catch (Throwable error) {
               SeedQueue.LOGGER.warn("[seedqueue] failed to destroy the {} target of #{}", text, this.number, error);
            }
         };
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft != null && !minecraft.isSameThread()) {
            minecraft.execute(runnable);
         } else {
            runnable.run();
         }
      }
   }

   boolean hasPacketFor(int count, int count2) {
      return EntryGeneration.hasPacketFor(this, count, count2);
   }

   public  void uploadNativePreview() {
      EntryCapture.uploadNativePreview(this);
   }


   public boolean releaseForScreenshot() {
      try {
         if (this.server != null) {
            this.server.halt(true);
            this.server = null;
         }

         if (this.stem != null) {
            this.stem.close();
            this.stem = null;
         }

         if (this.access != null) {
            this.access.close();
            this.access = null;
         }

         SeedQueue.LOGGER.info("[seedqueue] entry #{} released for screenshot", this.number);
         return true;
      } catch (Throwable error) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to release entry #{}", this.number, error);
         return false;
      }
   }

   public void discard() {
      if (this.state != QueueEntry.State.DISCARDED) {
         QueueEntry.State queueEntryState = this.state;
         this.state = QueueEntry.State.DISCARDED;
         boolean flag = queueEntryState == QueueEntry.State.PREPARING;
         this.deleteAfterPrepare = flag;
         if (!SeedQueue.shuttingDown && this.externalPreview != null && this.externalPreview.level != null) {
            SodiumLevelCache.evictLater(this.externalPreview.level);
         }

         this.parked = false;
         synchronized (this.parkLock) {
            this.parkLock.notifyAll();
         }

         synchronized (this.generationGate) {
            this.generationGate.notifyAll();
         }

         SeedQueue.LOGGER.info("[seedqueue] discarding entry #{} (was {})", this.number, queueEntryState);
         if (this.previewTexture != null) {
            try {
               Minecraft.getInstance().getTextureManager().release(this.previewTexture);
            } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }

            this.previewTexture = null;
         }

         this.destroyTarget(this.displayTarget, "display");
         this.displayTarget = null;
         this.destroyTarget(this.renderTarget, "preview");
         this.renderTarget = null;
         if (this.nativePreview != null) {
            try {
               this.nativePreview.close();
            } catch (Throwable error2) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error2);
      }

            this.nativePreview = null;
         }

         if (this.transferred) {
            SeedQueue.LOGGER.info("[seedqueue] entry #{} was transferred to the client, keeping its files", this.number);
         } else {
            try {
               if (this.server != null) {
                  this.server.halt(true);
               }
            } catch (Throwable error3) {
               SeedQueue.LOGGER.warn("[seedqueue] failed to halt server #{}", this.number, error3);
            }

            try {
               if (this.stem != null) {
                  this.stem.close();
               }
            } catch (Throwable error4) {
               SeedQueue.LOGGER.warn("[seedqueue] failed to close stem #{}", this.number, error4);
            }

            try {
               if (this.access != null) {
                  this.access.safeClose();
               }
            } catch (Throwable error5) {
               SeedQueue.LOGGER.warn("[seedqueue] failed to close access #{}", this.number, error5);
            }

            if (flag) {
               SeedQueue.LOGGER.info("[seedqueue] entry #{} is still preparing, folder deletion deferred", this.number);
            } else {
               this.deleteWorldFolder();
            }
         }
      }
   }

   public static boolean hasPlayerData(Path path) {
      return EntryWorldData.hasPlayerData(path);
   }

   public void deleteFolderIfUnplayed() {
      EntryWorldData.deleteFolderIfUnplayed(this);
   }

   void deleteWorldFolder() {
      EntryWorldData.deleteWorldFolder(this);
   }

   public static enum State {
      CREATED,
      PREPARING,
      PREPARED,
      STARTING,
      GENERATING,
      READY,
      DISCARDED,
      FAILED;
   }
}
