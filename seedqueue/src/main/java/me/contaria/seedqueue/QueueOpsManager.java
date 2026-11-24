package me.contaria.seedqueue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.config.*;

import static me.contaria.seedqueue.SeedQueue.*;

/**
 * 队列条目运维：重置、丢弃、待删除世界处理与队列排空清理。
 */
public final class QueueOpsManager {
   private QueueOpsManager() {
   }

   /**
    * 重置单个实例。
    */
   public static void resetEntry(QueueEntry entry) {
      if (entry != null) {
         /**
          * 批量重置实例。
          */
         resetEntries(List.of(entry));
      }
   }
   public static void resetEntries(Collection<QueueEntry> items) {
      if (QueueConfig.tryResetCooldown()) {
         Minecraft minecraft = Minecraft.getInstance();
         int count = 0;

         for (QueueEntry entry : new java.util.ArrayList<QueueEntry>(items)) {
            if (entry != null && !entry.locked && ENTRIES.contains(entry)) {
               ENTRIES.remove(entry);
               if (PreviewState.frameSwapEntry == entry) {
                  PreviewState.unbind();
               }

               discardAsync(entry);
               count++;
            }
         }

         if (count > 0) {
            playSound("reset_instance");
            LOGGER.info("[seedqueue] reset {} entries", count);
            refillQueue(minecraft);
         }
      }
   }
   /**
    * 丢弃实例并安排删除其世界目录。
    */
   public static void discardEntry(QueueEntry entry) {
      if (entry != null) {
         ENTRIES.remove(entry);
         discardAsync(entry);
         LOGGER.info("[seedqueue] deleted entry #{}", entry.number);
      }
   }
   /**
    * 把世界目录加入待删除队列。
    */
   public static void enqueueWorldDeletion(Path path) {
      if (path != null && DELETE_ENQUEUED_AT.putIfAbsent(path, System.currentTimeMillis()) == null) {
         PENDING_DELETES.add(path);
      }
   }
   /**
    * 处理所有待删除世界。
    */
   static void processPendingDeletions() {
      for (int i = 0; i < 4; i++) {
         /**
          * 处理一个待删除世界。
          */
         processOnePendingDeletion();
      }
   }
   static void processOnePendingDeletion() {
      Path path = PENDING_DELETES.poll();
      if (path != null) {
         Long enqueuedAt = DELETE_ENQUEUED_AT.get(path);
         long value = System.currentTimeMillis();
         if (enqueuedAt != null && value - enqueuedAt < 3000L) {
            PENDING_DELETES.add(path);
         } else {
            DELETE_ENQUEUED_AT.remove(path);

            try {
               QueueExecutors.prepare().execute(() -> {
                  if (QueueEntry.hasPlayerData(path)) {
                     LOGGER.info("[seedqueue] keeping {} (played or contains player data)", path.getFileName());
                  } else {
                     try {
                        try (java.util.stream.Stream<java.nio.file.Path> stream = Files.walk(path)) {
                           stream.sorted(Comparator.reverseOrder()).forEach(var0xx -> {
                              try {
                                 Files.deleteIfExists(var0xx);
                              } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }
                           });
                        }

                        LOGGER.info("[seedqueue] deleted world folder {}", path.getFileName());
                     } catch (Throwable error) {
                        LOGGER.warn("[seedqueue] could not delete {}, leaving it for the next launch", path.getFileName());
                     }
                  }
               });
            } catch (Throwable error2) {
               LOGGER.warn("[seedqueue] could not schedule deletion of {}", path.getFileName(), error2);
            }
         }
      }
   }
   /**
    * 队列排空后清理残留世界。
    */
   static void pruneWhenQueueDrained() {
      if (ENTRIES.isEmpty()) {
         long value = System.currentTimeMillis();
         if (value - lastPruneAt >= 30000L) {
            lastPruneAt = value;
            pruneStaleQueueWorlds();
         }
      }
   }
   /**
    * 当前仍被占用的世界目录集合。
    */
   public static Set<String> activeWorldFolders() {
      HashSet hashSet = new HashSet();

      for (QueueEntry entry : ENTRIES) {
         if (entry.folder != null) {
            hashSet.add(entry.folder);
         }
      }

      return hashSet;
   }
}
