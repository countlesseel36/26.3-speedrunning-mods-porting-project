package me.contaria.seedqueue;

import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.Minecraft;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;

import static me.contaria.seedqueue.SeedQueue.*;

/**
 * 生命周期：关服流程、退出看门狗与卡顿看门狗。
 */
public final class LifecycleManager {
   private LifecycleManager() {
   }

   /**
    * 关闭所有队列服务端并保存状态。
    */
   public static void shutdown() {
      /**
       * 退出看门狗：超时后强制结束 JVM。
       */
      startExitWatchdog();
      shuttingDown = true;
      boolean flag = Minecraft.getInstance().level != null;

      for (QueueEntry entry : flag ? new java.util.ArrayList<QueueEntry>() : new java.util.ArrayList<>(ENTRIES)) {
         if (!entry.selected && !entry.transferred && entry != liveEntry) {
            try {
               // 先 discard：它会置为 DISCARDED、唤醒 park 中的服务端线程，再 halt；
               // 反过来先 halt(false) 会一直等待仍在 park 的服务端线程，导致关机卡住。
               entry.parked = false;
               entry.discard();
            } catch (Throwable error) {
               LOGGER.warn("[seedqueue] failed to discard queued entry #{} during shutdown", entry.number, error);
            }
         }
      }

      if (liveEntry != null) {
         WorldPreview.deactivateExternal();
         liveEntry = null;
      }

      ENTRIES.clear();
      QueueExecutors.shutdown();
      startExitWatchdog();
      LOGGER.info("[seedqueue] shut down background queue");
   }
   static void startExitWatchdog() {
      if (Boolean.parseBoolean(System.getProperty("seedqueue.exitWatchdog", "true"))) {
         long value = Math.max(1000L, Long.getLong("seedqueue.exitWatchdogMs", 4000L));
         Thread thread = new Thread(() -> {
            try {
               Thread.sleep(value);
            } catch (InterruptedException interruptedException) {
               return;
            }

            try {
               for (Entry entry : Thread.getAllStackTraces().entrySet()) {
                  Thread thread2 = (Thread)entry.getKey();
                  if (!thread2.isDaemon() && thread2.isAlive()) {
                     LOGGER.warn("[seedqueue] still alive: {} ({})", thread2.getName(), thread2.getState());
                     StackTraceElement[] elements = (StackTraceElement[])entry.getValue();

                     for (int i = 0; i < Math.min(18, elements.length); i++) {
                        LOGGER.warn("[seedqueue]   at {}", elements[i]);
                     }
                  }
               }
            } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }

            LOGGER.warn("[seedqueue] the client is still running {}ms after shutdown, halting the JVM", value);
            Runtime.getRuntime().halt(0);
         }, "SeedQueue exit watchdog");
         thread.setDaemon(true);
         thread.start();
      }
   }
   /**
    * 运行期看门狗：检测卡顿并输出线程栈。
    */
   static void startWatchdog() {
      Thread thread = new Thread(
         () -> {
            while (true) {
               try {
                  Thread.sleep(10000L);
               } catch (InterruptedException interruptedException) {
                  return;
               }

               try {
                  java.util.Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();
                  Thread thread2 = null;

                  for (Thread thread3 : map.keySet()) {
                     if ("Render thread".equals(thread3.getName())) {
                        thread2 = thread3;
                        break;
                     }
                  }

                  if (thread2 != null) {
                     StringBuilder builder = new StringBuilder();

                     for (StackTraceElement stackTraceElement : (StackTraceElement[])map.get(thread2)) {
                        builder.append("\n\tat ").append(stackTraceElement);
                     }

                     LOGGER.warn("[seedqueue] watchdog: render thread state={} stack:{}", thread2.getState(), builder);
                  }

                  for (Entry entry : map.entrySet()) {
                     if (((Thread)entry.getKey()).getName().startsWith("SeedQueue server")) {
                        LOGGER.warn(
                           "[seedqueue] watchdog: {} state={} top={}",
                           ((Thread)entry.getKey()).getName(),
                           ((Thread)entry.getKey()).getState(),
                           ((StackTraceElement[])entry.getValue()).length == 0 ? "n/a" : ((StackTraceElement[])entry.getValue())[0]
                        );
                     }
                  }
               } catch (Throwable error) {
                  LOGGER.warn("[seedqueue] watchdog failed", error);
               }
            }
         },
         "SeedQueue Watchdog"
      );
      thread.setDaemon(true);
      thread.setPriority(1);
      thread.start();
      LOGGER.info("[seedqueue] watchdog started");
   }
}
