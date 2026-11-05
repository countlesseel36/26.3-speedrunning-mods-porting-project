package me.contaria.seedqueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Background executor used to prepare queued worlds off the render thread. */
public final class QueueExecutors {
   private static ThreadPoolExecutor prepareExecutor;
   private static boolean wall;

   private QueueExecutors() {
   }

   public static synchronized ExecutorService prepare() {
      ensureExecutor();
      return prepareExecutor;
   }

   /** Re-sizes the pool for the current screen (Background/Wall Worker Threads options). */
   public static synchronized void update(boolean wallMode) {
      wall = wallMode;
      ensureExecutor();

      int workers = QueueConfig.workers(wall);

      // ThreadPoolExecutor requires core <= max, so shrink the core first when reducing the pool.
      if (workers < prepareExecutor.getCorePoolSize()) {
         prepareExecutor.setCorePoolSize(workers);
         prepareExecutor.setMaximumPoolSize(workers);
      } else {
         prepareExecutor.setMaximumPoolSize(workers);
         prepareExecutor.setCorePoolSize(workers);
      }
   }

   private static void ensureExecutor() {
      if (prepareExecutor != null) {
         return;
      }

      int workers = QueueConfig.workers(wall);
      prepareExecutor = new ThreadPoolExecutor(
         workers,
         workers,
         30L,
         TimeUnit.SECONDS,
         new LinkedBlockingQueue<>(),
         runnable -> {
            Thread thread = new Thread(runnable, "SeedQueue prepare");
            thread.setDaemon(true);
            thread.setPriority(
               Math.max(
                  1,
                  Math.min(
                     10,
                     wall ? QueueConfig.wallExecutorThreadPriority : QueueConfig.backgroundExecutorThreadPriority
                  )
               )
            );
            return thread;
         }
      );
   }

   public static synchronized void shutdown() {
      if (prepareExecutor != null) {
         prepareExecutor.shutdownNow();
         prepareExecutor = null;
      }
   }
}
