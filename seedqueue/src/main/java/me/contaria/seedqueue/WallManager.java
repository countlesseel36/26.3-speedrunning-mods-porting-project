package me.contaria.seedqueue;

import fast_reset.client.FastReset;
import java.util.ArrayList;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

import static me.contaria.seedqueue.SeedQueue.*;

/**
 * 墙界面与预览开关：墙的打开/关闭、定时加入、返回墙与音效。
 * 
 * 负责墙与原版界面之间的切换，并在关闭墙时恢复玩家状态与预览缓存。
 */
public final class WallManager {
   private WallManager() {
   }

   /**
    * 墙模式是否启用。
    */
   static boolean wallEnabled() {
      return WALL_PROPERTY || QueueConfig.useWall;
   }
   /**
    * 处理墙上的定时加入/智能切换逻辑。
    */
   static void wallScheduledTick(Minecraft minecraft) {
      if (PreviewState.handoffPreviewEntry == null) {
         for (QueueEntry entry : new ArrayList<>(ENTRIES)) {
            if (entry.scheduledJoin && System.currentTimeMillis() - entry.scheduledJoinAt > 3000L) {
               entry.scheduledJoin = false;
               LOGGER.info("[seedqueue] expired scheduled join for #{}", entry.number);
            } else if (entry.scheduledJoin && entry.state == QueueEntry.State.READY && !entry.transferred) {
               LOGGER.info("[seedqueue] scheduled join for #{}", entry.number);
               if (playEntry(entry)) {
                  minecraft.gui.setScreen(null);
               }

               return;
            }
         }

         if (benchmarking) {
            for (QueueEntry entry2 : new ArrayList<>(ENTRIES)) {
               if (entry2.state == QueueEntry.State.READY && !entry2.locked) {
                  ENTRIES.remove(entry2);
                  if (PreviewState.frameSwapEntry == entry2) {
                     PreviewState.frameSwapEntry = null;
                  }

                  discardAsync(entry2);
                  if (benchmarkCount == 0) {
                     benchmarkLastResetAt = System.currentTimeMillis();
                  }

                  benchmarkCount++;
                  long value = System.currentTimeMillis();
                  long value2 = benchmarkLastResetAt == 0L ? 0L : value - benchmarkLastResetAt;
                  benchmarkLastResetAt = value;
                  if (entry2.discardStartAt == 0L) {
                     long value3 = 0L;
                  } else {
                     long value4 = System.currentTimeMillis() - entry2.discardStartAt;
                  }

                  long value5 = entry2.readyAt == 0L ? -1L : entry2.readyAt - entry2.createdAt;
                  LOGGER.info(
                     "[seedqueue] benchmark reset {} took {} ms (build {} ms, queued {} ms)",
                     benchmarkCount,
                     value2,
                     value5,
                     entry2.readyAt == 0L ? -1L : System.currentTimeMillis() - entry2.readyAt
                  );
                  if (benchmarkCount >= Integer.getInteger("seedqueue.benchmarkResets", QueueConfig.benchmarkResets)) {
                     benchmarking = false;
                     benchmarkFinishedAt = System.currentTimeMillis();
                     benchmarkResultResets = benchmarkCount;
                     benchmarkResultMs = Math.max(1L, benchmarkFinishedAt - benchmarkStarted);
                     /**
                      * 播放 SeedQueue 音效。
                      */
                     playSound("finish_benchmark");
                     LOGGER.info("[seedqueue] SEEDQUEUE_BENCHMARK {} resets in {} ms", benchmarkCount, benchmarkFinishedAt - benchmarkStarted);
                  }
                  break;
               }
            }
         }

         if (--refillCooldown <= 0) {
            refillCooldown = Math.max(1, Integer.getInteger("seedqueue.refillIntervalTicks", 10));
            refillQueue(minecraft);
         }
      }
   }
   /**
    * 打开墙界面并启动预览循环。
    */
   public static boolean openWall(Minecraft minecraft) {
      if (wallSavedCameraType == null) {
         wallSavedCameraType = minecraft.options.getCameraType();
      }

      minecraft.options.setCameraType(CameraType.FIRST_PERSON);
      if (minecraft.gui.screen() instanceof WallScreen) {
         return false;
      } else {
         playSound("open_wall");
         if (Boolean.getBoolean("seedqueue.openWallTrace")) {
            StringBuilder builder = new StringBuilder();

            for (StackTraceElement stackTraceElement : new Throwable().getStackTrace()) {
               builder.append(" <- ").append(stackTraceElement.getClassName()).append('.').append(stackTraceElement.getMethodName());
            }

            LOGGER.info("[seedqueue] openWall called{}", builder);
         }

         if (QueueConfig.bypassWall || Boolean.getBoolean("seedqueue.bypassWall")) {
            QueueEntry entry = null;

            for (QueueEntry entry2 : ENTRIES) {
               if (entry2.locked && entry2.state == QueueEntry.State.READY && !entry2.transferred) {
                  entry = entry2;
                  break;
               }
            }

            if (entry != null && playEntry(entry)) {
               LOGGER.info("[seedqueue] bypassed the wall, joined locked entry #{}", entry.number);
               return false;
            }

            for (QueueEntry entry3 : ENTRIES) {
               if (entry3.locked) {
                  entry3.scheduledJoin = true;
                  entry3.scheduledJoinAt = System.currentTimeMillis();
               }
            }
         }

         wallReturnScreen = minecraft.gui.screen();
         wallOpenedInWorld = minecraft.level != null;
         wallShown = true;
         wallTicks = 0;
         minecraft.setScreenAndShow(new WallScreen(() -> ENTRIES));
         LOGGER.info("[seedqueue] wall opened from key with {} entries", ENTRIES.size());
         return true;
      }
   }
   /**
    * 关闭墙界面：停止预览、恢复缓存与玩家状态。
    */
   public static void onWallClosed(Minecraft minecraft) {
      if (wallSavedCameraType != null) {
         minecraft.options.setCameraType(wallSavedCameraType);
         wallSavedCameraType = null;
      }

      if (benchmarking) {
         LOGGER.info("[seedqueue] onWallClosed ignored while benchmarking");
      } else {
         LOGGER.info("[seedqueue] onWallClosed");
         joinInProgress = false;
         wallShown = false;

         try {
            SodiumLevelCache.evictAllExcept(Minecraft.getInstance().level);
            SodiumLevelCache.sweep();
         } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }

         /**
          * 取消所有排队的加入计划。
          */
         cancelScheduledJoin();
         benchmarking = false;
         benchmarkFinishedAt = 0L;
         stopPreviewCycle(minecraft);
         WorldPreview.hideDebugOverlay = false;
         int count = resetAllUnlocked();
         SodiumLevelCache.evictAllExcept(minecraft.level);
         if (count > 0) {
            LOGGER.info("[seedqueue] wall closed: discarded {} previewed worlds", count);
         }

         Screen screen = wallReturnScreen;
         wallReturnScreen = null;
         if (screen instanceof TitleScreen) {
            minecraft.setScreenAndShow(new TitleScreen());
         } else {
            minecraft.gui.setScreen(screen);
         }
      }
   }
   /**
    * 是否应显示墙上的实时预览。
    */
   public static boolean wallPreviewEnabled() {
      return Boolean.parseBoolean(System.getProperty("seedqueue.wallPreview", "true"));
   }
   /**
    * 从已加入的世界返回墙界面。
    */
   public static boolean returnToWallFromJoinedWorld(Minecraft minecraft) {
      if (joinedFromWall
         && minecraft.level != null
         && minecraft.player != null
         && !(minecraft.gui.screen() instanceof WallScreen)
         && !(minecraft.gui.screen() instanceof LevelLoadingScreen)) {
         long value = System.currentTimeMillis();
         if (value - lastWallReturnAt < 1000L) {
            LOGGER.info("[seedqueue] return-to-wall ignored (already returning)");
            return false;
         } else {
            lastWallReturnAt = value;
            joinedFromWall = false;
            liveEntry = null;

            try {
               Class clazz = Class.forName("me.voidxwalker.autoreset.Atum");
               clazz.getField("shouldReset").setBoolean(null, false);
            } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }

            LOGGER.info("[seedqueue] returning to the wall from a joined world");
            boolean flag = false;

            try {
               flag = FastReset.skipSavesNow;
               FastReset.skipSavesNow = true;
            } catch (Throwable error2) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error2);
      }

            WorldPreview.releasePreviewCameraAndEntities(minecraft);
            TimerCompat.stop();
            stopPreviewCycle(minecraft);
            evictPreviewCaches("returned to the wall");
            SodiumLevelCache.evictAllExcept(minecraft.level);

            for (QueueEntry entry : ENTRIES) {
               if (entry.externalPreview != null) {
                  entry.externalPreview.invalidateSections();

                  try {
                     if (entry.externalPreview.level != null) {
                        SodiumLevelCache.evictLater(entry.externalPreview.level);
                     }
                  } catch (Throwable ignored) {
                  }

                  entry.externalPreview = null;
               }
            }

            wallReturnScreen = null;
            wallShown = true;
            wallTicks = 0;
            WallScreen wallScreen = new WallScreen(() -> ENTRIES);

            try {
               minecraft.disconnect(wallScreen, false);
            } catch (Throwable error3) {
               LOGGER.warn("[seedqueue] disconnect while returning to the wall failed", error3);

               try {
                  minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
               } catch (Throwable error4) {
                  LOGGER.warn("[seedqueue] direct disconnect failed", error4);
               }
            } finally {
               if (!flag) {
                  try {
                     FastReset.skipSavesNow = false;
                  } catch (Throwable error5) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error5);
      }
               }
            }

            minecraft.setScreenAndShow(wallScreen);
            pendingWallReturn = true;
            wallReturnTicks = 0;
            return true;
         }
      } else {
         return false;
      }
   }
   /**
    * 为原版新建世界流程做清理（退出预览、保存设置）。
    */
   public static void prepareForVanillaWorldCreation(Minecraft minecraft) {
      LOGGER.info("[seedqueue] vanilla world creation requested: releasing the wall");
      joinInProgress = false;
      joinedFromWall = false;
      liveEntry = null;
      wallShown = false;

      try {
         SodiumLevelCache.evictAllExcept(Minecraft.getInstance().level);
         SodiumLevelCache.sweep();
      } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }

      PreviewState.unbind();

      try {
         WorldPreview.keepSodiumBound = false;
         stopPreviewCycle(minecraft);
         WorldPreview.deactivateExternal();
         WorldPreview.releasePreviewCameraAndEntities(minecraft);
      } catch (Throwable error2) {
         LOGGER.warn("[seedqueue] failed to release the wall for vanilla world creation", error2);
      }

      if (minecraft.gui.screen() instanceof WallScreen || minecraft.gui.screen() instanceof LevelLoadingScreen) {
         minecraft.gui.setScreen(null);
      }
   }
   /**
    * 安排加入所有锁定的实例。
    */
   public static void scheduleAll() {
      for (QueueEntry entry : ENTRIES) {
         if (entry.locked) {
            entry.scheduledJoin = true;
            entry.scheduledJoinAt = System.currentTimeMillis();
         }
      }

      LOGGER.info("[seedqueue] scheduled join for all locked entries");
   }
   static void cancelScheduledJoin() {
      for (QueueEntry entry : ENTRIES) {
         entry.scheduledJoin = false;
      }
   }
   public static void playSound(String text) {
      if (QueueConfig.soundVolume > 0) {
         try {
            SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("seedqueue", text));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, 1.0F, (float)QueueConfig.soundVolume / 100.0F));
         } catch (Throwable error) {
            LOGGER.debug("[seedqueue] failed to play sound {}", text, error);
         }
      }
   }
}
