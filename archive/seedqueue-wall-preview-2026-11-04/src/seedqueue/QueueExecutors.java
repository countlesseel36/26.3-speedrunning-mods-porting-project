package me.contaria.seedqueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Background executor used to prepare queued worlds off the render thread. */
public final class QueueExecutors {
   private static ExecutorService prepareExecutor;

   private QueueExecutors() {
   }

   public static synchronized ExecutorService prepare() {
      if (prepareExecutor == null) {
         prepareExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "SeedQueue prepare");
            thread.setDaemon(true);
            return thread;
         });
      }

      return prepareExecutor;
   }

   public static synchronized void shutdown() {
      if (prepareExecutor != null) {
         prepareExecutor.shutdownNow();
         prepareExecutor = null;
      }
   }
}