package me.contaria.seedqueue;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import net.minecraft.client.Minecraft;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.config.*;

import static me.contaria.seedqueue.SeedQueue.*;

/**
 * 基准测试：开始/取消基准与资源采样。
 */
public final class BenchmarkManager {
   private BenchmarkManager() {
   }

   /**
    * 开始基准测试。
    */
   public static void startBenchmark() {
      if (benchmarking) {
         LOGGER.info("[seedqueue] benchmark already running, ignoring start");
      } else {
         benchmarking = true;
         benchmarkCount = 0;
         benchmarkStarted = System.currentTimeMillis();
         benchmarkLastResetAt = 0L;
         LOGGER.info(
            "[seedqueue] benchmark env: java={} os={} cores={} heap={}MB window={}x{} queue={} gen={}% concurrency={} sodium={}",
            System.getProperty("java.version"),
            System.getProperty("os.name") + " " + System.getProperty("os.arch"),
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().maxMemory() / 1048576L,
            Minecraft.getInstance().getWindow().getWidth(),
            Minecraft.getInstance().getWindow().getHeight(),
            queueSize(),
            QueueConfig.maxWorldGenerationPercentage,
            Integer.getInteger("seedqueue.benchmarkConcurrency", QueueConfig.benchmarkConcurrency > 0 ? QueueConfig.benchmarkConcurrency : queueSize()),
            sodiumVersion()
         );
         LOGGER.info("[seedqueue] benchmark started ({} resets)", Integer.getInteger("seedqueue.benchmarkResets", QueueConfig.benchmarkResets));
      }
   }
   /**
    * 取消基准测试。
    */
   public static void cancelBenchmark() {
      if (benchmarking) {
         benchmarking = false;
         benchmarkFinishedAt = System.currentTimeMillis();
         benchmarkResultResets = benchmarkCount;
         benchmarkResultMs = Math.max(1L, benchmarkFinishedAt - benchmarkStarted);
         playSound("finish_benchmark");
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft.gui.screen() != null && !(minecraft.gui.screen() instanceof WallScreen)) {
            minecraft.gui.setScreen(new WallScreen(() -> ENTRIES));
         }

         LOGGER.info("[seedqueue] benchmark cancelled after {} resets", benchmarkCount);
      }
   }
   /**
    * 基准测试期间的资源采样。
    */
   static void resourceSampleTick(Minecraft minecraft) {
      int count = QueueConfig.benchmarkSampleTicks > 0 ? QueueConfig.benchmarkSampleTicks : Integer.getInteger("seedqueue.resourceSampleTicks", 100);
      if (count > 0 && ++resourceSampleTicks >= count) {
         resourceSampleTicks = 0;
         Runtime runtime = Runtime.getRuntime();
         long value = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
         long value2 = runtime.maxMemory() / 1048576L;
         double ratio = -1.0;
         double ratio2 = -1.0;
         long value3 = -1L;

         try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean operatingSystemMXBean) {
               double ratio3 = operatingSystemMXBean.getProcessCpuLoad();
               double ratio4 = operatingSystemMXBean.getCpuLoad();
               ratio = ratio3 > 1.0 ? ratio3 : ratio3 * 100.0;
               ratio2 = ratio4 > 1.0 ? ratio4 : ratio4 * 100.0;
               value3 = operatingSystemMXBean.getCommittedVirtualMemorySize() / 1048576L;
            }
         } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }

         long value4 = 0L;
         LOGGER.info(
            "[seedqueue] bench resources: concurrency={} entries={} heap={}/{}MB committed={}MB cpu={}% sys={}%",
            Integer.getInteger(
               "seedqueue.benchmarkConcurrency",
               QueueConfig.benchmarkConcurrency > 0 ? QueueConfig.benchmarkConcurrency : Integer.getInteger("seedqueue.maxParkedServers", 8)
            ),
            ENTRIES.size(),
            value,
            value2,
            value3,
            String.format("%.1f", ratio),
            String.format("%.1f", ratio2)
         );
      }
   }
}
