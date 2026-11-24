package me.contaria.seedqueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;

import static me.contaria.seedqueue.SeedQueue.*;

/**
 * 队列条目集合：快照、计数、驻留上限与锁定候选。
 */
public final class EntryRegistry {
   private EntryRegistry() {
   }

   /**
    * 队列实例的只读视图。
    */
   public static List<QueueEntry> entries() {
      return Collections.unmodifiableList(ENTRIES);
   }
   /**
    * 队列实例的快照。
    */
   public static List<QueueEntry> entriesSnapshot() {
      return List.copyOf(ENTRIES);
   }
   /**
    * 实例数量。
    */
   public static int entriesCount() {
      return ENTRIES.size();
   }
   /**
    * 已冻结（parked）的实例数量。
    */
   static int parkedCount() {
      int count = 0;

      for (QueueEntry entry : ENTRIES) {
         if (entry.parked) {
            count++;
         }
      }

      return count;
   }
   /**
    * 按上限回收多余的冻结实例。
    */
   static void enforceParkedLimit() {
      int count = Math.max(1, queueSize());
      int count2 = parkedCount();
      if (count2 > count) {
         java.util.ArrayList<QueueEntry> list = new java.util.ArrayList<>();

         for (QueueEntry entry : ENTRIES) {
            if (entry.parked && !entry.locked && !entry.selected && !entry.transferred && entry != liveEntry) {
               list.add(entry);
            }
         }

         list.sort(Comparator.comparingLong((QueueEntry entry) -> entry.readyAtMillis));

         for (QueueEntry entry2 : list) {
            if (count2 <= count) {
               break;
            }

            LOGGER.warn("[seedqueue] parked servers {} > queue size {}, discarding entry #{}", count2, count, entry2.number);
            ENTRIES.remove(entry2);
            discardAsync(entry2);
            count2--;
         }
      }
   }
   /**
    * 是否存在可加入的锁定实例。
    */
   public static boolean hasLockedCandidate() {
      for (QueueEntry entry : ENTRIES) {
         if (entry.locked && !entry.transferred) {
            return true;
         }
      }

      return false;
   }
}
