package me.contaria.seedqueue.preview;
import me.contaria.seedqueue.SeedQueue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import me.voidxwalker.worldpreview.ExternalPreview;
import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.WorldPreviewProperties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

public final class PreviewPipeline {
   private static long previewFrameCounter;
   public static long previewFramesRendered;
   private static long lastCopiedFrame = -1L;

   private PreviewPipeline() {
   }


   public static void frameSwapTick(Minecraft minecraft) {
      int count = Math.max(1, Integer.getInteger("seedqueue.previewEveryNthFrame", 2));
      if (count <= 1 || previewFrameCounter++ % (long)count == 0L) {
         previewFramesRendered++;
         if (!SeedQueue.joinInProgress && PreviewState.handoffPreviewEntry == null) {
            if (!SeedQueue.wallPreviewEnabled()) {
               if (WorldPreview.keepSodiumBound) {
                  WorldPreview.keepSodiumBound = false;
               }
            } else {
               boolean flag = minecraft.gui.screen() instanceof WallScreen;
               WorldPreview.keepSodiumBound = flag || PreviewState.frameSwapEntry != null;
               long value = System.currentTimeMillis();
               if (PreviewState.frameSwapEntry != null) {
                  if (!flag) {
                     WorldPreview.deactivateExternal();
                     PreviewState.frameSwapEntry = null;
                     PreviewState.renderDistance = -1;
                     return;
                  }

                  if (value < PreviewState.frameSwapDwellUntil) {
                     return;
                  }

                  WorldPreview.deactivateExternal();
                  PreviewState.frameSwapEntry.lastPreviewAt = value;
                  PreviewState.frameSwapEntry = null;
                  PreviewState.renderDistance = -1;
                  SeedQueue.nextFrameSwapAt = value + (long)SeedQueue.PREVIEW_GAP_MS;
               }

               if (flag && value >= SeedQueue.nextFrameSwapAt) {
                  List list = SeedQueue.FRAME_SWAP_READY;
                  list.clear();

                  for (QueueEntry entry : SeedQueue.entries()) {
                     if (entry.state == QueueEntry.State.READY && entry.server != null && (!QueueConfig.freezeLockedPreviews || !entry.locked)) {
                        list.add(entry);
                     }
                  }

                  if (list.isEmpty()) {
                     for (QueueEntry entry2 : SeedQueue.entries()) {
                        if (entry2.state == QueueEntry.State.READY && entry2.server != null) {
                           list.add(entry2);
                        }
                     }
                  }

                  if (!list.isEmpty()) {
                     int count2 = Math.max(1, QueueConfig.previewFps);
                     long value2 = Math.max(1L, 1000L / (long)count2);
                     QueueEntry entry3 = null;
                     QueueEntry entry4 = WallScreen.hoveredEntry();

                     for (int i = 0; i < list.size(); i++) {
                        QueueEntry entry5 = (QueueEntry)list.get((SeedQueue.frameSwapIndex + i) % list.size());
                        int count3 = QueueConfig.idlePreviewFPS > 0 && entry5 != entry4 ? QueueConfig.idlePreviewFPS : count2;
                        long value3 = Math.max(1L, 1000L / (long)Math.max(1, count3));
                        if (entry5.lastPreviewAt == 0L || value - entry5.lastPreviewAt >= value3) {
                           entry3 = entry5;
                           SeedQueue.frameSwapIndex = (SeedQueue.frameSwapIndex + i + 1) % list.size();
                           break;
                        }
                     }

                     if (entry3 != null) {
                        entry3.lastPreviewAt = value;
                        applyPerspective();

                        try {
                           if (entry3.externalPreview == null) {
                              ArrayList list2;
                              synchronized (entry3.chunkPackets) {
                                 list2 = new ArrayList<>(entry3.chunkPackets);
                              }

                               if (entry3.externalPreview == null && list2.size() >= 9) {
                                  entry3.externalPreview = me.voidxwalker.worldpreview.ExternalPreview.fromPackets(minecraft, entry3.server, list2, SeedQueue.previewRadius(entry3));
                               }
                               if (entry3.externalPreview == null) {
                                  entry3.lastPreviewAt = 0L;
                                  return;
                               }
                           }

                           if (entry3.externalPreview == null) {
                              return;
                           }

                            try {
                               net.minecraft.core.BlockPos varPreviewSpawn = entry3.externalPreview.spawn;
                               int varPreviewCx = varPreviewSpawn.getX() >> 4;
                               int varPreviewCz = varPreviewSpawn.getZ() >> 4;
                               net.minecraft.world.level.chunk.LevelChunk varPreviewChunk = (net.minecraft.world.level.chunk.LevelChunk)entry3.externalPreview.level.getChunk(varPreviewCx, varPreviewCz);
                               int varPreviewY = varPreviewChunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 8, 8);
                               net.minecraft.world.level.block.state.BlockState varPreviewState = varPreviewChunk.getBlockState(new net.minecraft.core.BlockPos(8, varPreviewY - 1, 8));
                               SeedQueue.LOGGER.info("[seedqueue] card #{} preview sample: chunk({},{}) block({},{},{})={}", entry3.number, varPreviewCx, varPreviewCz, varPreviewCx * 16 + 8, varPreviewY - 1, varPreviewCz * 16 + 8, varPreviewState.getBlock());
                            } catch (Throwable varPreviewError) {
                               SeedQueue.LOGGER.warn("[seedqueue] card #{} preview sample failed", entry3.number, varPreviewError);
                            }
                           SodiumLevelCache.markPreview(entry3.externalPreview.level, entry3.externalPreview.spawn);
                           if (entry3.renderProperties == null) {
                              entry3.renderProperties = new WorldPreviewProperties(
                                 entry3.externalPreview.level,
                                 entry3.externalPreview.listener,
                                 entry3.externalPreview.gameMode,
                                 entry3.externalPreview.player,
                                 entry3.externalPreview.spawn
                              );
                           }

                           ensureDisplayTarget(minecraft, entry3);
                           if (entry3.displayTarget == null) {
                              return;
                           }

                           WorldPreview.hideDebugOverlay = true;
                           WorldPreview.suppressDebugOverlay = true;
                           WorldPreview.activateExternal(entry3.renderProperties);
                           entry3.externalPreview.activateSections();
                           if (!entry3.pendingPackets.isEmpty()) {
                              ArrayList list3;
                              synchronized (entry3.pendingPackets) {
                                 list3 = new ArrayList<>(entry3.pendingPackets);
                                 entry3.pendingPackets.clear();
                              }

                              if (!list3.isEmpty()) {
                                 entry3.externalPreview.applyNewPackets(list3);
                              }
                           }

                           if (!entry3.sectionsPrepared) {
                              entry3.sectionsPrepared = true;
                              WorldPreview.prepareExternal(minecraft);
                           }

                           WorldPreview.forceLevelRender = true;
                           if (SeedQueue.CAMERA_LOG) {
                              int count4 = (2 * SeedQueue.previewRadius(entry3) + 1) * (2 * SeedQueue.previewRadius(entry3) + 1);
                              int count5 = -1;

                              try {
                                 count5 = entry3.externalPreview.level.getChunkSource().getLoadedChunksCount();
                              } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }

                              SeedQueue.LOGGER
                                 .info(
                                    "[seedqueue] card #{} camera={} spawnY={} previewChunks={}/{} renderDistance={} thirdPerson={}",
                                    entry3.number,
                                    entry3.renderProperties.player.position(),
                                    entry3.externalPreview.spawnPosY,
                                    count5,
                                    count4,
                                    SeedQueue.previewRadius(entry3) + 2,
                                    System.getProperty("worldpreview.thirdPerson")
                                 );
                           }

                            if (Boolean.getBoolean("seedqueue.cardDiagnostics")) {
                            }
                           PreviewState.frameSwapEntry = entry3;
                           PreviewState.renderDistance = SeedQueue.previewRadius(entry3) + 2;
                           long value4 = Math.max(1L, Math.round(1000.0 / ((double)count2 * (double)Math.max(1, list.size()))));
                            PreviewState.frameSwapDwellUntil = Math.max(value + value4, value + 100L);
                        } catch (Throwable error2) {
                           SeedQueue.LOGGER.warn("[seedqueue] failed to arm wall frame for #{}", entry3.number, error2);
                           WorldPreview.deactivateExternal();
                           PreviewState.frameSwapEntry = null;
                           PreviewState.renderDistance = -1;
                        }
                     }
                  }
               }
            }
         } else {
            if (PreviewState.frameSwapEntry != null) {
               WorldPreview.deactivateExternal();
               PreviewState.frameSwapEntry = null;
               PreviewState.renderDistance = -1;
            }
         }
      }
   }

   public static void applyPerspective() {
      if (System.getProperty("worldpreview.thirdPerson") == null) {
         System.setProperty("worldpreview.thirdPerson", "false");
      }
   }

   public static int cardDownscale() {
      return 1;
   }

   public static void ensureDisplayTarget(Minecraft minecraft, QueueEntry entry) {
      if (entry.displayTarget == null) {
         if (cardDownscale() <= 1) {
            byte customWidth = 0;
            byte customHeight = 0;
            entry.displayTarget = WorldPreview.createTarget(
               minecraft, "seedqueue card " + entry.number, customWidth > 0 ? customWidth : minecraft.getWindow().getWidth(), customHeight > 0 ? customHeight : minecraft.getWindow().getHeight()
            );
         }
      }
   }
   private static QueueEntry lastCaptureEntry;

   public static void captureWallFrame(Minecraft minecraft) {
      QueueEntry entry = PreviewState.frameSwapEntry;

      if (entry == null || entry.displayTarget == null) {
         return;
      }

      // 同一张卡必须连续两帧处于装配状态才抓帧：第一帧交给 Sodium 编译新区块。
      if (entry != lastCaptureEntry) {
         lastCaptureEntry = entry;
         return;
      }

      if (lastCopiedFrame == previewFramesRendered && previewFramesRendered > 0L) {
         return;
      }

      lastCopiedFrame = previewFramesRendered;


      if (entry.externalPreview != null && !entry.externalPreview.settled()) {
         return;
      }

      try {
         entry.displayTarget.copyColorFrom(minecraft.gameRenderer.mainRenderTarget());
         entry.lastRenderedAt = System.currentTimeMillis();
         SeedQueue.renderedFrames++;
      } catch (Throwable error) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to capture wall frame for #{}", entry.number, error);
      }
   }

   public static void stop(Minecraft minecraft) {
      SeedQueue.pendingGrab = false;
      SeedQueue.grabStartedAt = 0L;
      WorldPreview.instanceRendering = false;
      WorldPreview.forceLevelRender = false;
      WorldPreview.restoreMainTarget(minecraft);
      WorldPreview.hideDebugOverlay = false;
      WorldPreview.suppressDebugOverlay = false;
      PreviewCapture.stop();
      if (PreviewState.activePreviewEntry != null) {
         try {
            WorldPreview.swapOut(minecraft);
         } catch (Throwable error) {
            SeedQueue.LOGGER.warn("[seedqueue] failed to swap out preview", error);
         }

         PreviewState.activePreviewEntry = null;
      }

      if (WorldPreview.isActive()) {
         WorldPreview.deactivateExternal();
      }

      WorldPreview.setTargetOverride(null);
   }


   public static Path findNewScreenshot() {
      try {
         Path path = FabricLoader.getInstance().getGameDir().resolve("screenshots");
         if (!Files.isDirectory(path)) {
            return null;
         } else {
            Path path2;
            try (java.util.stream.Stream<java.nio.file.Path> stream = Files.list(path)) {
               path2 = stream.filter(arg -> arg.getFileName().toString().endsWith(".png")).filter(arg -> {
                  try {
                     return Files.getLastModifiedTime(arg).toMillis() >= PreviewCapture.startMillis;
                  } catch (Throwable error) {
                     return false;
                  }
               }).max(Comparator.comparingLong(arg -> {
                  try {
                     return Files.getLastModifiedTime(arg).toMillis();
                  } catch (Throwable error) {
                     return 0L;
                  }
               })).orElse(null);
            }

            return path2;
         }
      } catch (Throwable error) {
         return null;
      }
   }
}
