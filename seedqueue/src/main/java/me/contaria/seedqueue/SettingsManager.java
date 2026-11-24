package me.contaria.seedqueue;

import java.util.ArrayList;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.config.*;

import static me.contaria.seedqueue.SeedQueue.*;

/**
 * 设置持久化与设置变更后的重建。
 */
public final class SettingsManager {
   private SettingsManager() {
   }

   /**
    * 保存所有配置（队列、按键、SpeedRunIGT 设置）。
    */
   public static void saveAllSettings() {
      try {
         QueueConfig.save();
         LOGGER.info("[seedqueue] settings saved on shutdown");
      } catch (Throwable error) {
         LOGGER.warn("[seedqueue] failed to save settings on shutdown", error);
      }
   }
   /**
    * 设置变更后丢弃并重建队列。
    */
   public static void discardAndRebuildForSettingsChange() {
      int count = 0;

      for (QueueEntry entry : new ArrayList<>(ENTRIES)) {
         if (entry != liveEntry && !entry.transferred && !entry.selected) {
            ENTRIES.remove(entry);
            discardAsync(entry);
            count++;
         }
      }

      if (count > 0) {
         LOGGER.info("[seedqueue] Atum settings changed, discarded {} queued entries for a rebuild", count);
      }
   }
}
