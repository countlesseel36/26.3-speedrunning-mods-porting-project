package me.contaria.seedqueue;

import java.util.ArrayList;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;

import static me.contaria.seedqueue.SeedQueue.*;

/**
 * 诊断与内存监控：会话统计、内存日志、堆压力保护与生成状态日志。
 */
public final class DiagnosticsManager {
   private DiagnosticsManager() {
   }

   /**
    * 定期输出会话内存与队列统计。
    */
   static void sessionDiagnostics() {
      if (Boolean.getBoolean("seedqueue.memLog")) {
         long value = System.currentTimeMillis();
         if (value - lastSessionLogAt >= 60000L) {
            lastSessionLogAt = value;
            Runtime runtime = Runtime.getRuntime();
            long value2 = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
            long value3 = runtime.maxMemory() / 1048576L;
            LOGGER.info(
               "[seedqueue] session: heap={}MB/{}MB entries={} generating={} parked={} pendingDeletes={}",
               value2,
               value3,
               ENTRIES.size(),
               generatingCount(),
               parkedCount(),
               PENDING_DELETES.size()
            );
         }
      }
   }

   /** 上一次堆趋势日志时间。 */
   private static long lastHeapTrendAt;

   /**
    * 每 60 秒记录一次堆占用，覆盖世界内/墙上两种状态，便于判断是否持续增长。
    */
   static void logHeapTrend() {
      if (!Boolean.parseBoolean(System.getProperty("seedqueue.heapLog", "true"))) {
         return;
      }

      long now = System.currentTimeMillis();

      if (now - lastHeapTrendAt < 60000L) {
         return;
      }

      lastHeapTrendAt = now;
      Runtime runtime = Runtime.getRuntime();
      long used = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
      long max = runtime.maxMemory() / 1048576L;
      LOGGER.info(
         "[seedqueue] heap trend: {}MB/{}MB ({}%), entries={}, parked={}, inWorld={}",
         used,
         max,
         max > 0 ? used * 100L / max : 0L,
         ENTRIES.size(),
         parkedCount(),
         net.minecraft.client.Minecraft.getInstance().level != null
      );
   }

   /**
    * 输出当前堆内存使用情况。
    */
   public static void logMemory(String text) {
      if (Boolean.getBoolean("seedqueue.memLog")) {
         Runtime runtime = Runtime.getRuntime();
         long value = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
         long value2 = runtime.totalMemory() / 1048576L;
         long value3 = runtime.maxMemory() / 1048576L;
         LOGGER.info("[seedqueue] memory {}: heapUsed={}MB committed={}MB max={}MB", text, value, value2, value3);
      }
   }
   /**
    * 堆压力保护：内存紧张时驱逐缓存/预览。
    */
   static void memoryGuard() {
      long value = System.currentTimeMillis();
      if (value - lastHeapCheckAt >= 10000L) {
         lastHeapCheckAt = value;
         Runtime runtime = Runtime.getRuntime();
         long value2 = runtime.totalMemory() - runtime.freeMemory();
         long value3 = runtime.maxMemory();
         double ratio = value3 > 0L ? (double)value2 / (double)value3 : 0.0;
         long usedMb = value2 / 1048576L;
         long maxMb = value3 / 1048576L;
         int percent = (int)Math.round(ratio * 100.0);

         if (!heapPressure && ratio > 0.75) {
            heapPressure = true;
            refillCooldownUntil = 0L;
            LOGGER.warn("[seedqueue] heap pressure {}% ({}MB/{}MB), releasing previews and parked servers", percent, usedMb, maxMb);

            try {
               PreviewManager.releaseHiddenPreviews("heap pressure");
               evictPreviewCaches("heap pressure");
            } catch (Throwable error) {
               LOGGER.warn("[seedqueue] preview eviction under heap pressure failed", error);
            }

            int count = parkedCount();

            for (QueueEntry entry : new ArrayList<>(ENTRIES)) {
               if (count <= 3) {
                  break;
               }

               if (entry.parked && !entry.locked && !entry.selected && !entry.transferred && entry != liveEntry) {
                  ENTRIES.remove(entry);
                  discardAsync(entry);
                  count--;
               }
            }
         } else if (heapPressure && ratio < 0.6) {
            heapPressure = false;
            refillCooldownUntil = System.currentTimeMillis() + 60000L;
            LOGGER.info(
               "[seedqueue] heap pressure eased ({}%, {}MB/{}MB), refill cooldown 60s (effective queue size {})",
               percent,
               usedMb,
               maxMb,
               effectiveQueueSize()
            );
         }
      }
   }
   /**
    * 输出生成进度与卡住检测。
    */
   static void logGenerationState() {
      long value = System.currentTimeMillis();
      if (value - lastGenDiagAt >= 5000L) {
         lastGenDiagAt = value;
         StringBuilder builder = new StringBuilder();

         for (QueueEntry entry : ENTRIES) {
            long[] values = GENERATION_PROGRESS.computeIfAbsent(entry.number, arg -> new long[]{(long)entry.chunksGenerated, value});
            if (values[0] != (long)entry.chunksGenerated) {
               values[0] = (long)entry.chunksGenerated;
               values[1] = value;
            } else if (entry.state != QueueEntry.State.READY && value - values[1] > 15000L) {
               builder.append(" #")
                  .append(entry.number)
                  .append(':')
                  .append(entry.state)
                  .append("/paused=")
                  .append(entry.genPausedForPlay)
                  .append('/')
                  .append(entry.chunksGenerated)
                  .append('/')
                  .append(entry.chunksTotal)
                  .append('/')
                  .append((value - values[1]) / 1000L)
                  .append("s");
            }
         }

         if (builder.length() > 0) {
            LOGGER.warn("[seedqueue] generation stalled for >15s:{}", builder);
         }

         GENERATION_PROGRESS.keySet().removeIf(key -> ENTRIES.stream().noneMatch(candidate -> candidate.number == key));
      }
   }
}
