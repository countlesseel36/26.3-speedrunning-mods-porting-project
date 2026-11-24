package me.contaria.seedqueue.queue;
import me.contaria.seedqueue.SeedQueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.config.*;

public final class QueueExecutors {
   private static ThreadPoolExecutor prepareExecutor;
   private static boolean wall;

   private QueueExecutors() {
   }

   public static synchronized ExecutorService prepare() {
      ensureExecutor();
      return prepareExecutor;
   }

   public static synchronized void update(boolean flag) {
      wall = flag;
      ensureExecutor();
      int count = QueueConfig.workers(wall);
      if (count < prepareExecutor.getCorePoolSize()) {
         prepareExecutor.setCorePoolSize(count);
         prepareExecutor.setMaximumPoolSize(count);
      } else {
         prepareExecutor.setMaximumPoolSize(count);
         prepareExecutor.setCorePoolSize(count);
      }
   }

   private static void ensureExecutor() {
      if (prepareExecutor == null) {
         int count = QueueConfig.workers(wall);
         prepareExecutor = new ThreadPoolExecutor(count, count, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), arg -> {
            Thread thread = new Thread(arg, "SeedQueue prepare");
            thread.setDaemon(true);
            thread.setPriority(Math.max(1, Math.min(10, wall ? QueueConfig.wallExecutorThreadPriority : QueueConfig.backgroundExecutorThreadPriority)));
            return thread;
         });
      }
   }

   public static synchronized void shutdown() {
      if (prepareExecutor != null) {
         prepareExecutor.shutdownNow();
         prepareExecutor = null;
      }
   }
}
