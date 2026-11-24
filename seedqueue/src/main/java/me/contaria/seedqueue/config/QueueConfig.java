package me.contaria.seedqueue.config;
import me.contaria.seedqueue.SeedQueue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

public final class QueueConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   public static int queueSize = 3;
   public static int chunkRadius = 4;
   public static boolean showChunkProgress = false;
   public static int simulatedWidth = 0;
   public static int simulatedHeight = 0;
   public static boolean useWall = true;
   public static boolean autoQueue = true;
   public static boolean showHud = true;
   public static int rows = 1;
   public static int columns = 3;
   public static int resetCooldownMs = 400;
   public static int wallFps = 60;
   public static int previewFps = 20;
   public static boolean multiView = false;
   public static boolean deleteDiscardedWorlds = true;
   public static int soundVolume = 100;
   public static boolean bypassWall = false;
   public static boolean smartSwitch = false;
   public static boolean waitForPreviewSetup = false;
   public static int benchmarkResets = 1000;
   public static int benchmarkConcurrency = 0;
   public static int benchmarkSampleTicks = 100;
   public static int maxGeneratingCount = 0;
   public static int benchmarkChunkRadius = 2;
   public static boolean reduceLevelList = true;
   public static boolean freezeLockedPreviews = false;
   public static int backgroundExecutorThreadPriority = 5;
   public static int serverThreadPriority = 5;
   public static int maxConcurrently = 0;
   public static int maxConcurrentlyOnWall = 4;
   public static int maxWorldGenerationPercentage = 100;
   public static boolean resumeOnFilledQueue = false;
   public static int preparingPreviews = -1;
   public static int backgroundExecutorThreads = 0;
   public static int wallExecutorThreads = 0;
   public static int wallExecutorThreadPriority = 4;
   public static int chunkUpdateThreads = 0;
   public static int chunkUpdateThreadPriority = 3;
   public static int seedQueueThreadPriority = 5;
   public static boolean showDebugMenu = false;
   public static boolean useWatchdog = false;
   public static boolean showChunkMaps = false;
   public static int startConcurrently = 2;
   public static int prewarmWorlds = 9;
   public static int wallChunkDistance = 12;
   public static int wallDataLimit = 100;
   public static int idlePreviewFPS = 15;
   public static boolean joinTransition = true;
   public static long chunkMapFreezing = -1L;
   public static boolean checkMinMemory = true;
   private static long lastResetAt;
   private static boolean loaded;

   private QueueConfig() {
   }

   public static Path path() {
      return FabricLoader.getInstance().getConfigDir().resolve("seedqueue.json");
   }

   public static void load() {
      loaded = true;

      try {
         Path path = path();
         if (!Files.exists(path)) {
            save();
            return;
         }

         JsonObject jsonObject = (JsonObject)GSON.fromJson(Files.readString(path), JsonObject.class);
         queueSize = clamp(jsonObject.has("queueSize") ? jsonObject.get("queueSize").getAsInt() : queueSize, 1, 64);
         chunkRadius = clamp(jsonObject.has("chunkRadius") ? jsonObject.get("chunkRadius").getAsInt() : chunkRadius, 0, 8);
         showChunkProgress = jsonObject.has("showChunkProgress") ? jsonObject.get("showChunkProgress").getAsBoolean() : showChunkProgress;
         int count = clamp(jsonObject.has("simulatedWidth") ? jsonObject.get("simulatedWidth").getAsInt() : 0, 0, 16384);
         int count2 = clamp(jsonObject.has("simulatedHeight") ? jsonObject.get("simulatedHeight").getAsInt() : 0, 0, 16384);
         simulatedWidth = 0;
         simulatedHeight = 0;
         useWall = !jsonObject.has("useWall") || jsonObject.get("useWall").getAsBoolean();
         autoQueue = !jsonObject.has("autoQueue") || jsonObject.get("autoQueue").getAsBoolean();
         showHud = !jsonObject.has("showHud") || jsonObject.get("showHud").getAsBoolean();
         rows = clamp(jsonObject.has("rows") ? jsonObject.get("rows").getAsInt() : rows, 1, 4);
         columns = clamp(jsonObject.has("columns") ? jsonObject.get("columns").getAsInt() : columns, 1, 4);
         resetCooldownMs = clamp(jsonObject.has("resetCooldownMs") ? jsonObject.get("resetCooldownMs").getAsInt() : resetCooldownMs, 0, 5000);
         wallFps = clamp(jsonObject.has("wallFps") ? jsonObject.get("wallFps").getAsInt() : wallFps, 0, 480);
         previewFps = clamp(jsonObject.has("previewFps") ? jsonObject.get("previewFps").getAsInt() : previewFps, 5, 120);
         multiView = jsonObject.has("multiView") && jsonObject.get("multiView").getAsBoolean();
         deleteDiscardedWorlds = !jsonObject.has("deleteDiscardedWorlds") || jsonObject.get("deleteDiscardedWorlds").getAsBoolean();
         soundVolume = clamp(jsonObject.has("soundVolume") ? jsonObject.get("soundVolume").getAsInt() : soundVolume, 0, 100);
         bypassWall = jsonObject.has("bypassWall") && jsonObject.get("bypassWall").getAsBoolean();
         smartSwitch = jsonObject.has("smartSwitch") && jsonObject.get("smartSwitch").getAsBoolean();
         waitForPreviewSetup = jsonObject.has("waitForPreviewSetup") && jsonObject.get("waitForPreviewSetup").getAsBoolean();
         benchmarkResets = clamp(jsonObject.has("benchmarkResets") ? jsonObject.get("benchmarkResets").getAsInt() : benchmarkResets, 1, 1000);
         reduceLevelList = !jsonObject.has("reduceLevelList") || jsonObject.get("reduceLevelList").getAsBoolean();
         freezeLockedPreviews = jsonObject.has("freezeLockedPreviews") && jsonObject.get("freezeLockedPreviews").getAsBoolean();
         backgroundExecutorThreadPriority = clamp(
            jsonObject.has("backgroundExecutorThreadPriority") ? jsonObject.get("backgroundExecutorThreadPriority").getAsInt() : backgroundExecutorThreadPriority, 1, 10
         );
         serverThreadPriority = clamp(jsonObject.has("serverThreadPriority") ? jsonObject.get("serverThreadPriority").getAsInt() : serverThreadPriority, 1, 10);
         maxConcurrently = clamp(jsonObject.has("maxConcurrently") ? jsonObject.get("maxConcurrently").getAsInt() : maxConcurrently, 0, 30);
         maxConcurrentlyOnWall = clamp(
            jsonObject.has("maxConcurrently_onWall")
               ? jsonObject.get("maxConcurrently_onWall").getAsInt()
               : (jsonObject.has("maxConcurrentlyOnWall") ? jsonObject.get("maxConcurrentlyOnWall").getAsInt() : maxConcurrentlyOnWall),
            1,
            30
         );
         maxWorldGenerationPercentage = clamp(
            jsonObject.has("maxWorldGenerationPercentage") ? jsonObject.get("maxWorldGenerationPercentage").getAsInt() : maxWorldGenerationPercentage, 0, 100
         );
         resumeOnFilledQueue = jsonObject.has("resumeOnFilledQueue") && jsonObject.get("resumeOnFilledQueue").getAsBoolean();
         preparingPreviews = clamp(jsonObject.has("preparingPreviews") ? jsonObject.get("preparingPreviews").getAsInt() : preparingPreviews, -1, 30);
         backgroundExecutorThreads = clamp(
            jsonObject.has("backgroundExecutorThreads") ? jsonObject.get("backgroundExecutorThreads").getAsInt() : backgroundExecutorThreads, 0, 256
         );
         wallExecutorThreads = clamp(jsonObject.has("wallExecutorThreads") ? jsonObject.get("wallExecutorThreads").getAsInt() : wallExecutorThreads, 0, 256);
         wallExecutorThreadPriority = clamp(
            jsonObject.has("wallExecutorThreadPriority") ? jsonObject.get("wallExecutorThreadPriority").getAsInt() : wallExecutorThreadPriority, 1, 10
         );
         chunkUpdateThreads = clamp(jsonObject.has("chunkUpdateThreads") ? jsonObject.get("chunkUpdateThreads").getAsInt() : chunkUpdateThreads, 0, 64);
         chunkUpdateThreadPriority = clamp(
            jsonObject.has("chunkUpdateThreadPriority") ? jsonObject.get("chunkUpdateThreadPriority").getAsInt() : chunkUpdateThreadPriority, 1, 10
         );
         seedQueueThreadPriority = clamp(jsonObject.has("seedQueueThreadPriority") ? jsonObject.get("seedQueueThreadPriority").getAsInt() : seedQueueThreadPriority, 1, 10);
         showDebugMenu = jsonObject.has("showDebugMenu") && jsonObject.get("showDebugMenu").getAsBoolean();
         useWatchdog = jsonObject.has("useWatchdog") && jsonObject.get("useWatchdog").getAsBoolean();
         showChunkMaps = jsonObject.has("showChunkMaps") && jsonObject.get("showChunkMaps").getAsBoolean();
         startConcurrently = clamp(jsonObject.has("startConcurrently") ? jsonObject.get("startConcurrently").getAsInt() : startConcurrently, 1, 30);
         prewarmWorlds = clamp(jsonObject.has("prewarmWorlds") ? jsonObject.get("prewarmWorlds").getAsInt() : prewarmWorlds, 0, 30);
         wallChunkDistance = clamp(jsonObject.has("wallChunkDistance") ? jsonObject.get("wallChunkDistance").getAsInt() : wallChunkDistance, 2, 16);
         wallDataLimit = clamp(jsonObject.has("wallDataLimit") ? jsonObject.get("wallDataLimit").getAsInt() : wallDataLimit, 1, 100);
         System.setProperty("seedqueue.wallDataLimit", String.valueOf(wallDataLimit));
         idlePreviewFPS = clamp(jsonObject.has("idlePreviewFPS") ? jsonObject.get("idlePreviewFPS").getAsInt() : idlePreviewFPS, 0, 60);
         joinTransition = !jsonObject.has("joinTransition") || jsonObject.get("joinTransition").getAsBoolean();
         chunkMapFreezing = jsonObject.has("chunkMapFreezing") ? jsonObject.get("chunkMapFreezing").getAsLong() : chunkMapFreezing;
         checkMinMemory = !jsonObject.has("checkMinMemory") || jsonObject.get("checkMinMemory").getAsBoolean();
         WallKeybindings.load(jsonObject.has("keyBindings") ? jsonObject.getAsJsonObject("keyBindings") : null);
         queueSize = Math.min(queueSize, rows * columns);
      } catch (Throwable error) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to load config", error);
      }
   }

   public static void save() {
      if (!loaded) {
         load();
      }

      try {
         JsonObject jsonObject = new JsonObject();
         jsonObject.addProperty("queueSize", queueSize);
         jsonObject.addProperty("chunkRadius", chunkRadius);
         jsonObject.addProperty("showChunkProgress", showChunkProgress);
         jsonObject.addProperty("simulatedWidth", simulatedWidth);
         jsonObject.addProperty("simulatedHeight", simulatedHeight);
         jsonObject.addProperty("useWall", useWall);
         jsonObject.addProperty("autoQueue", autoQueue);
         jsonObject.addProperty("showHud", showHud);
         jsonObject.addProperty("rows", rows);
         jsonObject.addProperty("columns", columns);
         jsonObject.addProperty("resetCooldownMs", resetCooldownMs);
         jsonObject.addProperty("wallFps", wallFps);
         jsonObject.addProperty("previewFps", previewFps);
         jsonObject.addProperty("multiView", multiView);
         jsonObject.addProperty("deleteDiscardedWorlds", deleteDiscardedWorlds);
         jsonObject.addProperty("soundVolume", soundVolume);
         jsonObject.addProperty("bypassWall", bypassWall);
         jsonObject.addProperty("smartSwitch", smartSwitch);
         jsonObject.addProperty("waitForPreviewSetup", waitForPreviewSetup);
         jsonObject.addProperty("benchmarkResets", benchmarkResets);
         jsonObject.addProperty("benchmarkConcurrency", benchmarkConcurrency);
         jsonObject.addProperty("benchmarkSampleTicks", benchmarkSampleTicks);
         jsonObject.addProperty("maxGeneratingCount", maxGeneratingCount);
         jsonObject.addProperty("benchmarkChunkRadius", benchmarkChunkRadius);
         jsonObject.addProperty("reduceLevelList", reduceLevelList);
         jsonObject.addProperty("freezeLockedPreviews", freezeLockedPreviews);
         jsonObject.addProperty("backgroundExecutorThreadPriority", backgroundExecutorThreadPriority);
         jsonObject.addProperty("serverThreadPriority", serverThreadPriority);
         jsonObject.addProperty("maxConcurrently", maxConcurrently);
         jsonObject.addProperty("maxConcurrently_onWall", maxConcurrentlyOnWall);
         jsonObject.addProperty("maxWorldGenerationPercentage", maxWorldGenerationPercentage);
         jsonObject.addProperty("resumeOnFilledQueue", resumeOnFilledQueue);
         jsonObject.addProperty("preparingPreviews", preparingPreviews);
         jsonObject.addProperty("backgroundExecutorThreads", backgroundExecutorThreads);
         jsonObject.addProperty("wallExecutorThreads", wallExecutorThreads);
         jsonObject.addProperty("wallExecutorThreadPriority", wallExecutorThreadPriority);
         jsonObject.addProperty("chunkUpdateThreads", chunkUpdateThreads);
         jsonObject.addProperty("chunkUpdateThreadPriority", chunkUpdateThreadPriority);
         jsonObject.addProperty("seedQueueThreadPriority", seedQueueThreadPriority);
         jsonObject.addProperty("showDebugMenu", showDebugMenu);
         jsonObject.addProperty("useWatchdog", useWatchdog);
         jsonObject.addProperty("showChunkMaps", showChunkMaps);
         jsonObject.addProperty("startConcurrently", startConcurrently);
         jsonObject.addProperty("prewarmWorlds", prewarmWorlds);
         jsonObject.addProperty("wallChunkDistance", wallChunkDistance);
         jsonObject.addProperty("wallDataLimit", wallDataLimit);
         jsonObject.addProperty("idlePreviewFPS", idlePreviewFPS);
         jsonObject.addProperty("joinTransition", joinTransition);
         jsonObject.addProperty("chunkMapFreezing", chunkMapFreezing);
         jsonObject.addProperty("checkMinMemory", checkMinMemory);
         JsonObject jsonObject2 = WallKeybindings.save();
         jsonObject.add("keyBindings", jsonObject2);
         if (Boolean.getBoolean("seedqueue.keysLog")) {
            SeedQueue.LOGGER.info("[seedqueue] saving wall keys: {}", jsonObject2);
         }

         Path path = path();
         Files.createDirectories(path.getParent());
         Files.writeString(path, GSON.toJson(jsonObject));
      } catch (Throwable error) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to save config", error);
      }
   }

   public static boolean tryResetCooldown() {
      long value = System.currentTimeMillis();
      if (value - lastResetAt < (long)resetCooldownMs) {
         return false;
      } else {
         lastResetAt = value;
         return true;
      }
   }

   public static int concurrency(boolean flag) {
      int count = flag ? maxConcurrentlyOnWall : maxConcurrently;
      return count <= 0 ? Math.max(1, queueSize) : count;
   }

   public static int workers(boolean flag) {
      int count = flag ? wallExecutorThreads : backgroundExecutorThreads;
      if (count > 0) {
         return count;
      } else {
         int count2 = Runtime.getRuntime().availableProcessors();
         return flag ? Math.max(1, Math.min(count2, count2)) : Math.max(1, Math.min(count2, maxConcurrently + 1));
      }
   }

   public static int chunkWorkers() {
      if (chunkUpdateThreads > 0) {
         return chunkUpdateThreads;
      } else {
         int count = Math.max(1, rows * columns);
         int count2 = Math.max(1, Math.min(Math.max(1, queueSize), count + (preparingPreviews < 0 ? count : preparingPreviews)));
         int count3 = Math.max(1, SodiumLevelCache.size() + 1);
         return Math.max(1, Runtime.getRuntime().availableProcessors() / Math.max(count2, count3));
      }
   }

   public static int wallSlots() {
      return Math.max(1, Math.min(queueSize, rows * columns));
   }

   private static int clamp(int count, int count2, int count3) {
      return Math.max(count2, Math.min(count3, count));
   }
}
