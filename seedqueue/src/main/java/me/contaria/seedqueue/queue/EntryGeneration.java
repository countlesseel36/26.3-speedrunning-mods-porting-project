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

/**
 * 实例的区块生成：生成循环、半径、许可与扩展生成。
 * 从 QueueEntry 拆分出来，行为保持不变。
 */
final class EntryGeneration {
   private EntryGeneration() {
   }

   static int effectiveRadius(QueueEntry entry) {
      return SeedQueue.benchmarking && QueueConfig.benchmarkChunkRadius > 0 ? QueueConfig.benchmarkChunkRadius : entry.chunkRadius;
   }

   static void startGenerating(QueueEntry entry) {
      entry.state = QueueEntry.State.GENERATING;
      entry.reachedLimit = false;
      int count = entry.effectiveRadius();
      int count2 = count * 2 + 1;
      entry.chunksTotal = count2 * count2;
      long now = System.currentTimeMillis();
      SeedQueue.LOGGER.info("[seedqueue] entry #{} generating {} chunks", entry.number, entry.chunksTotal);
      SeedQueue.acquireGeneratingPermit();
      entry.server.execute(() -> generateStep(entry));
   }

   static void generateStep(QueueEntry entry) {
      try {
         int count = entry.effectiveRadius();
         long value = entry.prepareT0;
         if (!entry.genInitialized) {
            entry.genLevel = entry.server.overworld();
            entry.genChunkSource = entry.genLevel.getChunkSource();
            BlockPos pos = entry.genLevel.getRespawnData().pos();
            entry.genSpawnX = pos.getX() >> 4;
            entry.genSpawnZ = pos.getZ() >> 4;
            entry.genRingCursor = 0;
            entry.genInitialized = true;
         }

         ServerLevel level = entry.genLevel;
         ServerChunkCache chunkSource = entry.genChunkSource;
         int count2 = entry.genSpawnX;
         int count3 = entry.genSpawnZ;
         long nanos = System.nanoTime();

         generationLoop:
         for (int i = 0; i <= count; i++) {
            for (int j = -count; j <= count; j++) {
               for (int k = -count; k <= count; k++) {
                  if (Math.max(Math.abs(j), Math.abs(k)) == i) {
                     if (!entry.waitForGenerationPermit()) {
                        break generationLoop;
                     }

                     if (entry.state == QueueEntry.State.DISCARDED) {
                        return;
                     }

                     if (SeedQueue.isBackgroundGenerationPaused()) {
                        entry.genPausedForPlay = true;
                        SeedQueue.LOGGER
                           .info(
                              "[seedqueue] entry #{} generation yielded (player is in a world, {}/{} chunks)",
                              entry.number,
                              entry.chunksGenerated,
                              entry.chunksTotal
                           );
                        return;
                     }

                     chunkSource.getChunk(count2 + j, count3 + k, ChunkStatus.FULL, true);
                     entry.chunksGenerated++;
                     long now = System.currentTimeMillis() - value;
                     if (now - entry.lastMapCaptureAt >= 250L) {
                        entry.lastMapCaptureAt = now;
                        entry.captureMap(level, count2, count3);
                     }
                  }
               }
            }
         }

         entry.genInitialized = false;

         try {
            for (int l = 0; l < 128 && level.getLightEngine().hasLightWork(); l++) {
               level.getLightEngine().runLightUpdates();
            }
         } catch (Throwable error) {
            SeedQueue.LOGGER.warn("[seedqueue] failed to flush light before capturing preview chunks", error);
         }

         for (int index = entry.genRingCursor; index <= count; index++) {
            for (int index2 = -count; index2 <= count; index2++) {
               for (int index3 = -count; index3 <= count; index3++) {
                  if (Math.max(Math.abs(index2), Math.abs(index3)) == index) {
                     LevelChunk chunk = (LevelChunk)chunkSource.getChunk(count2 + index2, count3 + index3, ChunkStatus.FULL, false);
                     if (chunk != null) {
                        try {
                           entry.chunkPackets.add(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
                        } catch (Throwable error2) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error2);
      }
                     }
                  }
               }
            }
         }

         entry.genMs = System.currentTimeMillis() - entry.prepareT0 - entry.serverMs;
         long now2 = System.currentTimeMillis();
         if (Boolean.parseBoolean(System.getProperty("seedqueue.fillMissingChunks", "true"))) {
            int count4 = 0;

            for (int index4 = -count; index4 <= count; index4++) {
               for (int index5 = -count; index5 <= count; index5++) {
                  if (entry.state == QueueEntry.State.DISCARDED) {
                     return;
                  }

                  int count5 = count2 + index4;
                  int count6 = count3 + index5;
                  if (!entry.hasPacketFor(count5, count6) && (Math.abs(index4) == count || Math.abs(index5) == count)) {
                     try {
                        LevelChunk chunk2 = (LevelChunk)chunkSource.getChunk(count5, count6, ChunkStatus.FULL, true);
                        if (chunk2 != null) {
                           entry.chunkPackets.add(new ClientboundLevelChunkWithLightPacket(chunk2, level.getLightEngine(), null, null));
                           count4++;
                        }
                     } catch (Throwable error3) {
                        SeedQueue.LOGGER.warn("[seedqueue] entry #{} failed to snapshot chunk {},{}", entry.number, count5, count6, error3);
                     }
                  }
               }
            }


            try {
               net.minecraft.world.level.chunk.LevelChunk varSampleChunk = (net.minecraft.world.level.chunk.LevelChunk)chunkSource.getChunk(count2, count3, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
               if (varSampleChunk != null) {
                  int varSampleX = 8;
                  int varSampleZ = 8;
                  int varSampleY = varSampleChunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, varSampleX, varSampleZ);
                  net.minecraft.world.level.block.state.BlockState varSampleState = varSampleChunk.getBlockState(new net.minecraft.core.BlockPos(varSampleX, varSampleY - 1, varSampleZ));
                  SeedQueue.LOGGER.info("[seedqueue] entry #{} capture sample: chunk({},{}) block({},{},{})={}", entry.number, count2, count3, count2 * 16 + varSampleX, varSampleY - 1, count3 * 16 + varSampleZ, varSampleState.getBlock());

            if (Boolean.getBoolean("seedqueue.cardDiagnostics")) {
            }
               }
            } catch (Throwable varSampleError) {
               SeedQueue.LOGGER.warn("[seedqueue] entry #{} capture sample failed", entry.number, varSampleError);
            }

            try {
               long varServerSeed = entry.server == null ? 0L : entry.server.overworld().getSeed();
               SeedQueue.LOGGER.info(
                  "[seedqueue] entry #{} capture seed check: entrySeed={} serverSeed={} match={}",
                  entry.number, entry.seed, varServerSeed, entry.seed == varServerSeed
               );
            } catch (Throwable varSeedError) {
               SeedQueue.LOGGER.warn("[seedqueue] capture seed check failed for #{}", entry.number, varSeedError);
            }
            int count7 = (2 * count + 1) * (2 * count + 1);
            SeedQueue.LOGGER.info("[seedqueue] entry #{} preview packets: {} ({} filled, expected {})", entry.number, entry.chunkPackets.size(), count4, count7);
            if (entry.chunkPackets.size() < count7) {
               StringBuilder builder = new StringBuilder();

               for (int index6 = -count; index6 <= count; index6++) {
                  for (int index7 = -count; index7 <= count; index7++) {
                     if (!entry.hasPacketFor(count2 + index6, count3 + index7)) {
                        if (builder.length() > 0) {
                           builder.append(' ');
                        }

                        builder.append(index6).append(',').append(index7);
                     }
                  }
               }

               SeedQueue.LOGGER.warn("[seedqueue] entry #{} missing preview chunks (relative): {}", entry.number, builder);
            }
         }

         entry.genMs = System.currentTimeMillis() - entry.prepareT0 - entry.serverMs;
         entry.captureMap(level, count2, count3);
         long now3 = System.currentTimeMillis() - now2;
         SeedQueue.releaseGeneratingPermit();
         entry.state = QueueEntry.State.READY;
         entry.readyAt = System.currentTimeMillis();
         entry.readyAtMillis = System.currentTimeMillis();

         try {
            entry.extendGeneration(level, chunkSource, count2, count3, count);
         } catch (Throwable error4) {
            SeedQueue.LOGGER.warn("[seedqueue] entry #{} extended generation failed", entry.number, error4);
         }

         entry.park();
         SeedQueue.LOGGER
            .info(
               "[seedqueue] entry #{} ready (seed {}, {} chunks in {} ms; server={}ms gen={}ms capture={}ms)",
               entry.number,
               entry.seed,
               entry.chunksGenerated,
               System.currentTimeMillis() - entry.prepareT0,
               entry.serverMs,
               entry.genMs,
               now3
            );
         SeedQueue.logMemory("after entry #" + entry.number + " ready");
      } catch (Throwable error5) {
         entry.state = QueueEntry.State.FAILED;
         entry.error = error5.toString();
         SeedQueue.LOGGER.error("[seedqueue] entry #{} chunk generation failed", entry.number, error5);
      } finally {
         SeedQueue.releaseGeneratingPermit();
      }
   }

   static void extendGeneration(QueueEntry entry, ServerLevel level, ServerChunkCache chunkSource, int count, int count2, int count3) {
      int count4 = Math.max(0, Math.min(Integer.getInteger("seedqueue.extendPreviewRadius", 0), (16 - count3) / 2));
      int count5 = count3 + count4;

      for (int i = count3 + 1; i <= count5; i++) {
         if (entry.selected || entry.state == QueueEntry.State.DISCARDED) {
            return;
         }

         for (int j = -i; j <= i; j++) {
            for (int k = -i; k <= i; k++) {
               if (Math.max(Math.abs(j), Math.abs(k)) == i) {
                  if (entry.selected || entry.state == QueueEntry.State.DISCARDED) {
                     return;
                  }

                  level.getChunk(count + j, count2 + k, ChunkStatus.FULL, true);
                  LevelChunk chunk = (LevelChunk)chunkSource.getChunk(count + j, count2 + k, ChunkStatus.FULL, false);
                  if (chunk != null) {
                     try {
                        entry.pendingPackets.add(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
                     } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }
                  }
               }
            }
         }

         try {
            Thread.sleep(20L);
         } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return;
         }
      }

      if (!entry.pendingPackets.isEmpty()) {
         SeedQueue.LOGGER.info("[seedqueue] entry #{} generated {} extra chunks beyond preview radius", entry.number, entry.pendingPackets.size());
      }
   }

   static boolean hasPacketFor(QueueEntry entry, int count, int count2) {
      for (ClientboundLevelChunkWithLightPacket packet : entry.chunkPackets) {
         if (packet.x() == count && packet.z() == count2) {
            return true;
         }
      }

      return false;
   }
}
