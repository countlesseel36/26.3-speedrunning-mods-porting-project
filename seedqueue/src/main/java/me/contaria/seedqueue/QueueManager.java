package me.contaria.seedqueue;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Stream;
import me.voidxwalker.worldpreview.ExternalPreview;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

import static me.contaria.seedqueue.SeedQueue.*;

/**
 * 队列管理：容量、补充、冻结、进入世界与启动清理。
 * 
 * 与 GenerationManager 协作控制并发生成，与 PreviewManager 协作准备卡片预览；
 * 进入世界时由 playEntry 完成“接管服务端”的流程（不落盘加载）。
 */
public final class QueueManager {
   private QueueManager() {
   }

   /**
    * 有效队列容量（受配置与硬件限制）。
    */
   static int queueSize() {
      return Integer.getInteger("seedqueue.queueSize", QueueConfig.queueSize);
   }
   /**
    * 把队列补充到目标容量。
    */
   public static void refillQueue(Minecraft minecraft) {
      if (minecraft.level == null || minecraft.player == null || wallShown || benchmarking) {
         enforceParkedLimit();
         int count = parkedCount();
         int count2 = Math.max(1, Integer.getInteger("seedqueue.maxParkedServers", 8));
         if (benchmarking) {
            count2 = Math.max(1, Integer.getInteger("seedqueue.benchmarkConcurrency", count2));
         }

         if (count >= count2 && !benchmarking) {
            LOGGER.warn("[seedqueue] {} parked servers reached the threshold ({}), pausing refill to protect memory", count, count2);
         } else {
            long value = ENTRIES.stream().filter(arg -> !arg.transferred).count();
            int count3 = benchmarking
               ? Math.max(
                  queueSize(),
                  Integer.getInteger("seedqueue.benchmarkConcurrency", QueueConfig.benchmarkConcurrency > 0 ? QueueConfig.benchmarkConcurrency : queueSize())
               )
               : queueSize();
            if (value < (long)count3) {
               QueueEntry entry = new QueueEntry(nextEntryNumber++, nextQueueSeed());
               ENTRIES.add(entry);
               value++;
               LOGGER.info("[seedqueue] queued entry #{} (seed {})", entry.number, entry.seed);

               try {
                  QueueExecutors.prepare().execute(() -> entry.prepare(minecraft));
               } catch (Throwable error) {
                  LOGGER.warn("[seedqueue] could not queue entry #{} (executor stopped)", entry.number, error);
                  ENTRIES.remove(entry);
               }
            }
         }
      }
   }
   /**
    * 异步丢弃一个实例（世界目录稍后删除）。
    */
   static void discardAsync(QueueEntry entry) {
      entry.discardStartAt = System.currentTimeMillis();
      Runnable runnable = () -> {
         entry.discard();
         entry.deleteFolderIfUnplayed();
         pruneWhenQueueDrained();
      };

      try {
         QueueExecutors.prepare().execute(runnable);
      } catch (Throwable error) {
         LOGGER.warn("[seedqueue] could not schedule discard for entry #{}, discarding inline", entry.number, error);
         runnable.run();
      }
   }
   /**
    * 冻结已就绪实例的预览：构建 ExternalPreview 并生成静态卡片图。
    */
   static void freezeReadyEntries(Minecraft minecraft) {
      if (Boolean.parseBoolean(System.getProperty("seedqueue.freeze", "true")) && !freezeCaptureEnabled() && !wallPreviewEnabled()) {
         for (QueueEntry entry : ENTRIES) {
            if (entry.state == QueueEntry.State.READY && !entry.frozen && System.currentTimeMillis() - entry.readyAtMillis >= 800L) {
               if (entry.previewTexture == null && entry.server != null && !shotPreviewEnabled()) {
                  try {
                     ArrayList packets;
                     synchronized (entry.chunkPackets) {
                        packets = new ArrayList<>(entry.chunkPackets);
                     }

                     entry.externalPreview = packets.size() >= 9
                        ? ExternalPreview.fromPackets(minecraft, entry.server, packets, 2)
                        : null;
                     if (entry.externalPreview != null) {
                        NativeImage image = SpawnViewRenderer.render(entry.externalPreview.level, entry.externalPreview.spawn, 0.0, 0.0, false);
                        entry.nativePreview = image;
                        entry.uploadNativePreview();
                        LOGGER.info("[seedqueue] fallback preview captured for #{}", entry.number);
                     }
                  } catch (Throwable error) {
                     LOGGER.warn("[seedqueue] fallback preview failed for #{}", entry.number, error);
                  }
               }

               entry.frozen = true;
               entry.releaseForScreenshot();
            }
         }
      }
   }
   /**
    * 进入实例；实例未就绪时排队等待其完成。
    */
   public static boolean playEntry(QueueEntry entry) {
      if (entry != null && !entry.transferred) {
         if (System.currentTimeMillis() - lastHandoffAt < 1000L) {
            LOGGER.info("[seedqueue] playEntry ignored (a handoff just happened)");
            return false;
         } else if (Minecraft.getInstance().level != null) {
            LOGGER.info("[seedqueue] playEntry ignored (still in a world)");
            return false;
         } else if (entry.state == QueueEntry.State.READY) {
            Minecraft minecraft = Minecraft.getInstance();
            if (entry.externalPreview != null) {
               spawnAlignEntry = entry;
               spawnAlignPos = entry.externalPreview.spawn;
            }

            if (QueueConfig.useWall && entry.externalPreview == null && entry.previewTexture == null) {
               entry.scheduledJoin = true;
               entry.scheduledJoinAt = System.currentTimeMillis();
               LOGGER.info("[seedqueue] entry #{} has no preview yet, scheduled join", entry.number);
               return false;
            } else {
               if (QueueConfig.waitForPreviewSetup) {
                  ensurePreview(minecraft, entry);
               }

               playSound("play_instance");
               joinInProgress = true;
               boolean wasPreviewing = stopWallPreview(minecraft, entry);
               ENTRIES.remove(entry);
               suppressLoadingScreens = true;
               LOGGER.info("[seedqueue] joining queued entry #{} (seed {})", entry.number, entry.seed);
               entry.select();
               if (entry.play(minecraft)) {
                  liveEntry = entry;
                  joinedFromWall = true;
                  wallShown = false;

                  try {
                     SodiumLevelCache.evictAllExcept(Minecraft.getInstance().level);
                     SodiumLevelCache.sweep();
                  } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }

                  if (QueueConfig.joinTransition) {
                     WallScreen.beginJoinTransition();
                  }

                  timerLogTicks = Boolean.getBoolean("seedqueue.timerLog") ? 0 : -1;
                  timerFinishTicks = 0;
                  if (wasPreviewing) {
                     PreviewState.handoffPreviewEntry = entry;
                     handoffTicks = 0;
                     if (minecraft.gui.screen() instanceof LevelLoadingScreen) {
                        minecraft.gui.setScreen(null);
                     }
                  }

                  return true;
               } else {
                  suppressLoadingScreens = false;
                  joinInProgress = false;
                  LOGGER.warn("[seedqueue] hand-off failed, discarding entry #{}", entry.number);
                  entry.discard();
                  return false;
               }
            }
         } else {
            entry.locked = true;
            entry.lockedAt = System.currentTimeMillis();
            if (QueueConfig.smartSwitch) {
               for (QueueEntry candidate : ENTRIES) {
                  if (candidate != entry && candidate.locked && candidate.state == QueueEntry.State.READY && !candidate.transferred) {
                     LOGGER.info("[seedqueue] smart switch: #{} is not ready, playing locked #{}", entry.number, candidate.number);
                     return playEntry(candidate);
                  }
               }
            }

            entry.scheduledJoin = true;
            entry.scheduledJoinAt = System.currentTimeMillis();
            LOGGER.info("[seedqueue] entry #{} is not ready yet, scheduled join", entry.number);
            return false;
         }
      } else {
         return false;
      }
   }
   /**
    * 清理启动时遗留的临时世界目录。
    */
   static void pruneStaleQueueWorlds() {
      if (QueueConfig.deleteDiscardedWorlds) {
         try {
            Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("saves");
            if (!Files.isDirectory(path)) {
               return;
            }

            int count = 0;

            try (java.util.stream.Stream<java.nio.file.Path> stream = Files.list(path)) {
               for (Path path2 : stream.toList()) {
                  if (Files.isDirectory(path2) && path2.getFileName().toString().startsWith("SeedQueue-")) {
                     if (QueueEntry.hasPlayerData(path2)) {
                        LOGGER.info("[seedqueue] keeping {} (played or contains player data)", path2.getFileName());
                     } else {
                        try (java.util.stream.Stream<java.nio.file.Path> stream2 = Files.walk(path2)) {
                            stream2.sorted(Comparator.reverseOrder()).forEach(arg -> {
                               try {
                                  Files.deleteIfExists(arg);
                               } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }
                            });
                        }

                        count++;
                     }
                  }
               }
            }

            if (count > 0) {
               LOGGER.info("[seedqueue] pruned {} stale queue worlds from saves/", count);
            }
         } catch (Throwable error) {
            LOGGER.warn("[seedqueue] failed to prune stale queue worlds", error);
         }
      }
   }
   /**
    * 清理无用的存档残留（SeedQueue 临时世界等）。
    */
   static void cleanupStaleSaves() {
      try {
         Path path = FabricLoader.getInstance().getGameDir().resolve("saves");
         if (!Files.isDirectory(path)) {
            return;
         }

         long now = System.currentTimeMillis() - 300000L;
         int count = 0;

         try (java.util.stream.Stream<java.nio.file.Path> stream = Files.list(path)) {
            for (Path path2 : stream.filter(arg -> Files.isDirectory(arg)).toList()) {
               String text = path2.getFileName().toString();
               if (text.startsWith("SeedQueue-")
                  && !Files.exists(path2.resolve("seedqueue_played.marker"))
                  && Files.getLastModifiedTime(path2).toMillis() <= now
                  && Files.exists(path2.resolve("level.dat"))) {
                  try (java.util.stream.Stream<java.nio.file.Path> stream2 = Files.walk(path2)) {
                     stream2.sorted(Comparator.reverseOrder()).forEach(arg -> {
                        try {
                           Files.deleteIfExists(arg);
                        } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }
                     });
                  }

                  count++;
               }
            }
         }

         if (count > 0) {
            LOGGER.info("[seedqueue] cleaned {} stale queue saves", count);
         }
      } catch (Throwable error2) {
         LOGGER.warn("[seedqueue] failed to clean stale queue saves", error2);
      }
   }
}
