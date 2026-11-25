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
   /**
    * 退出看门狗：关机后若仍有线程存活则兜底结束 JVM。
    * 只剩空闲的第三方/原版线程池线程时不再刷 WARN 与堆栈，只记录一条 INFO。
    */
   static void startExitWatchdog() {
      if (!Boolean.parseBoolean(System.getProperty("seedqueue.exitWatchdog", "true"))) {
         return;
      }

      long value = Math.max(1000L, Long.getLong("seedqueue.exitWatchdogMs", 4000L));
      Thread thread = new Thread(() -> {
         try {
            Thread.sleep(value);
         } catch (InterruptedException interruptedException) {
            return;
         }

         java.util.List<Thread> critical = new java.util.ArrayList<>();
         java.util.List<Thread> idlePools = new java.util.ArrayList<>();
         java.util.Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();

         for (var entry : traces.entrySet()) {
            Thread candidate = entry.getKey();

            if (!candidate.isAlive() || candidate.isDaemon() || "DestroyJavaVM".equals(candidate.getName())) {
               continue;
            }

            if (isIdleWorkerPool(candidate, entry.getValue())) {
               idlePools.add(candidate);
            } else {
               critical.add(candidate);
            }
         }

         if (critical.isEmpty()) {
            // 给空闲线程池一点时间自行退出（例如 Sodium 正在停止 worker）
            try {
               Thread.sleep(2000L);
            } catch (InterruptedException ignored) {
               return;
            }

            java.util.List<Thread> stillIdle = new java.util.ArrayList<>();

            for (var entry : Thread.getAllStackTraces().entrySet()) {
               Thread candidate = entry.getKey();

               if (candidate.isAlive() && !candidate.isDaemon()
                  && !"DestroyJavaVM".equals(candidate.getName())
                  && isIdleWorkerPool(candidate, entry.getValue())) {
                  stillIdle.add(candidate);
               }
            }

            if (!stillIdle.isEmpty()) {
               LOGGER.info("[seedqueue] shutdown complete; only idle worker pools remain ({}), halting quietly", stillIdle.size());

               if (Boolean.parseBoolean(System.getProperty("seedqueue.exitWatchdogHalt", "true"))) {
                  Runtime.getRuntime().halt(0);
               }
            }

            return;
         }

         for (Thread candidate : critical) {
            LOGGER.warn("[seedqueue] still alive: {} ({})", candidate.getName(), candidate.getState());
            StackTraceElement[] elements = traces.get(candidate);

            if (elements != null) {
               for (int i = 0; i < Math.min(18, elements.length); i++) {
                  LOGGER.warn("[seedqueue]   at {}", elements[i]);
               }
            }
         }

         LOGGER.warn("[seedqueue] the client is still running {}ms after shutdown, halting the JVM", value);
         Runtime.getRuntime().halt(0);
      }, "SeedQueue exit watchdog");
      thread.setDaemon(true);
      thread.start();
   }
   /**
    * 判断线程是否只是空闲的线程池 worker（LinkedBlockingQueue.take / ForkJoinPool 等待任务）。
    */
   private static boolean isIdleWorkerPool(Thread thread, StackTraceElement[] stack) {
      String name = thread.getName();

      if (!name.startsWith("pool-") && !name.startsWith("ForkJoinPool")) {
         return false;
      }

      Thread.State state = thread.getState();

      if (state != Thread.State.WAITING && state != Thread.State.TIMED_WAITING) {
         return false;
      }

      for (StackTraceElement element : stack) {
         String className = element.getClassName();
         String methodName = element.getMethodName();

         if ("java.util.concurrent.LinkedBlockingQueue".equals(className) && "take".equals(methodName)) {
            return true;
         }

         if (className.startsWith("java.util.concurrent.ForkJoinPool")
            && ("awaitWork".equals(methodName) || "getTask".equals(methodName) || "managedBlock".equals(methodName))) {
            return true;
         }

         if ("java.util.concurrent.ThreadPoolExecutor".equals(className) && "getTask".equals(methodName)) {
            return true;
         }
      }

      return false;
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
