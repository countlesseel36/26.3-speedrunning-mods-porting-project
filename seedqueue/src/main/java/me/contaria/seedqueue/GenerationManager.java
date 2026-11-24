package me.contaria.seedqueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.config.*;

import static me.contaria.seedqueue.SeedQueue.*;

/**
 * 生成调度：并发许可、生成节流、队列容量判断与暂停/恢复。
 * 
 * 以许可（permit）限制同时生成的实例数量，玩家进入世界时暂停后台生成。
 */
public final class GenerationManager {
   private GenerationManager() {
   }

   /**
    * 按并发上限分配生成许可。
    */
   static void applyGenerationPermits(Minecraft minecraft) {
      boolean flag = minecraft.gui.screen() instanceof WallScreen;
      QueueExecutors.update(flag);
      boolean flag2 = QueueConfig.resumeOnFilledQueue
         && !ENTRIES.isEmpty()
         && ENTRIES.size() >= queueSize()
         && ENTRIES.stream()
            .allMatch(
               arg -> arg.state == QueueEntry.State.READY
                     || arg.reachedLimit
                     || arg.state == QueueEntry.State.FAILED
                     || arg.state == QueueEntry.State.DISCARDED
            );
      int count = QueueConfig.concurrency(flag);

      for (QueueEntry entry : ENTRIES) {
         if (entry.locked && entry.state != QueueEntry.State.READY && entry.state != QueueEntry.State.FAILED) {
            entry.permit(true, true);
         }
      }

      for (QueueEntry entry2 : ENTRIES) {
         if (!entry2.locked) {
            if (entry2.state != QueueEntry.State.READY && entry2.state != QueueEntry.State.FAILED && entry2.state != QueueEntry.State.DISCARDED) {
               boolean flag3 = !entry2.reachedLimit || flag2 || entry2.scheduledJoin;
               boolean flag4 = flag3 && (count > 0 || flag2 || entry2.scheduledJoin);
               entry2.permit(flag4, flag2 || entry2.scheduledJoin);
               if (flag4) {
                  count--;
               }
            } else {
               entry2.permit(false, false);
            }
         }
      }
   }
   /**
    * 是否还能再启动新的实例生成。
    */
   static boolean canStartMoreEntries(Minecraft minecraft) {
      int count = QueueConfig.concurrency(minecraft.gui.screen() instanceof WallScreen);
      long value = ENTRIES.stream()
         .filter(arg -> (arg.startQueued || arg.state == QueueEntry.State.STARTING || arg.state == QueueEntry.State.GENERATING) && !arg.reachedLimit)
         .count();
      if (value >= (long)count) {
         return false;
      } else {
         long value2 = ENTRIES.stream().filter(arg -> arg.startQueued || arg.state == QueueEntry.State.STARTING).count();
         return value2 < (long)Math.max(1, QueueConfig.startConcurrently);
      }
   }
   /**
    * 预热策略是否允许启动下一个实例。
    */
   static boolean prewarmAllowsStart(Minecraft minecraft) {
      if (!(minecraft.gui.screen() instanceof WallScreen) && minecraft.gui.screen() instanceof TitleScreen) {
         int count = Math.min(QueueConfig.prewarmWorlds, queueSize());
         if (count <= 0) {
            return false;
         } else {
            long value = ENTRIES.stream()
               .filter(
                  arg -> arg.state != QueueEntry.State.CREATED && arg.state != QueueEntry.State.PREPARING && arg.state != QueueEntry.State.PREPARED
               )
               .count();
            return value < (long)count;
         }
      } else {
         return true;
      }
   }
   /**
    * 获取一个生成许可（可能阻塞）。
    */
   public static void acquireGeneratingPermit() {
      synchronized (generatingLock) {
         /**
          * 当前生效的并发生成上限。
          */
         int count = effectiveGeneratingLimit();
         /**
          * 后台生成是否因玩家进入世界而暂停。
          */
         if (isBackgroundGenerationPaused()) {
            count = count > 0 ? Math.min(count, 1) : 1;
         }

         for (; count > 0 && generatingNow >= count; count = effectiveGeneratingLimit()) {
            try {
               generatingLock.wait(50L);
            } catch (InterruptedException interruptedException) {
               Thread.currentThread().interrupt();
               break;
            }
         }

         generatingNow++;
      }
   }
   /**
    * 释放生成许可。
    */
   public static void releaseGeneratingPermit() {
      synchronized (generatingLock) {
         if (generatingNow > 0) {
            generatingNow--;
         }

         generatingLock.notifyAll();
      }
   }
   /**
    * 当前正在生成的实例数。
    */
   public static int generatingCount() {
      int count = 0;

      for (QueueEntry entry : ENTRIES) {
         if (entry.state != QueueEntry.State.READY) {
            count++;
         }
      }

      return count;
   }
   static int effectiveGeneratingLimit() {
      return Integer.getInteger("seedqueue.maxGeneratingCount", QueueConfig.maxGeneratingCount);
   }
   public static boolean isBackgroundGenerationPaused() {
      try {
         Minecraft minecraft = Minecraft.getInstance();
         return minecraft.player != null && minecraft.level != null && !wallShown && !benchmarking;
      } catch (Throwable error) {
         return false;
      }
   }
   /**
    * 恢复被暂停的后台生成。
    */
   static void resumePausedGeneration() {
      if (!isBackgroundGenerationPaused()) {
         for (QueueEntry entry : ENTRIES) {
            if (entry.genPausedForPlay) {
               entry.resumeGeneration();
            }
         }
      }
   }
   /**
    * 队列是否已满。
    */
   public static boolean queueIsFull() {
      return !ENTRIES.isEmpty() && ENTRIES.size() >= queueSize();
   }
   /**
    * 是否还可以再排入一个实例。
    */
   static boolean canQueueAnother() {
      long value = System.currentTimeMillis();
      if (value - lastQueueAt < 500L) {
         return false;
      } else {
         lastQueueAt = value;
         return true;
      }
   }
}
