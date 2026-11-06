package me.contaria.seedqueue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * SeedQueue settings, persisted to config/seedqueue.json.
 *
 * <p>The field set follows the 1.21.4 mod where it makes sense for this port: queue capacity, wall
 * grid, reset cooldown and preview frame budgets. Unknown keys in an existing file are preserved by
 * the JSON round-trip only for the fields above; everything else falls back to the defaults.</p>
 */
public final class QueueConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   public static int queueSize = 3;
   public static int chunkRadius = 4;
   /** Shows "generated/total" chunk progress on the cards; hidden by default. */
   public static boolean showChunkProgress = false;
   /** Preview render resolution; 0 = use the real window size. */
   public static int simulatedWidth = 0;
   public static int simulatedHeight = 0;
   public static boolean useWall = true;
   public static boolean autoQueue = true;
   public static boolean showHud = true;
   /** Preview camera perspective: "first_person" or "third_person". */
   public static String perspective = "first_person";
   /** Wall grid, mirroring the 1.21.4 rows/columns options. */
   public static int rows = 1;
   public static int columns = 3;
   /** Minimum time between two wall resets, like the 1.21.4 resetCooldown. */
   public static int resetCooldownMs = 400;
   /** Frame budget hints for the wall (0 = unlimited). */
   public static int wallFps = 60;
   public static int previewFps = 20;
   /** Render every card into its own target each frame (requires the Sodium multi-manager patch). */
   public static boolean multiView = false;
   public static boolean deleteDiscardedWorlds = true;
   /** 1.21.4 parity options. */
   public static int soundVolume = 100;
   public static boolean bypassWall = false;
   public static boolean smartSwitch = false;
   public static boolean waitForPreviewSetup = false;
   public static int benchmarkResets = 1000;
   public static boolean reduceLevelList = true;
   public static boolean freezeLockedPreviews = false;
   public static int backgroundExecutorThreadPriority = 5;
   public static int serverThreadPriority = 5;
   /** Max Generating Seeds (0 = bounded by the queue size). */
   public static int maxConcurrently = 0;
   /** Max Generating Seeds on the wall screen. */
   public static int maxConcurrentlyOnWall = 4;
   /** Stop generating at this percentage until the world is loaded / the queue is full. */
   public static int maxWorldGenerationPercentage = 100;
   /** Resume paused generation once every queued world reached the generation limit. */
   public static boolean resumeOnFilledQueue = false;
   /** Background Previews: how many preparing cards the wall shows (-1 = auto rows*columns). */
   public static int preparingPreviews = -1;
   /** Threading options (0 = automatic). */
   public static int backgroundExecutorThreads = 0;
   public static int wallExecutorThreads = 0;
   public static int wallExecutorThreadPriority = 4;
   public static int chunkUpdateThreads = 0;
   public static int chunkUpdateThreadPriority = 3;
   public static int seedQueueThreadPriority = 5;
   /** Debug options. */
   public static boolean showDebugMenu = false;
   public static boolean useWatchdog = false;
   /** Chunkmap overlay while in a world with queued worlds loaded. */
   public static boolean showChunkMaps = false;
   /** How many integrated servers may start at the same time (1.21.4 Concurrent Server Starts). */
   public static int startConcurrently = 2;
   /** How many worlds are started while sitting on the title screen (0 = only when the wall opens). */
   public static int prewarmWorlds = 9;
   /** Chunk radius cap for wall previews. */
   public static int wallChunkDistance = 12;
   /** Max chunk packets applied per preview update while on the wall. */
   public static int wallDataLimit = 100;
   /** Framerate for ready, non-hovered previews (0 = use previewFps). */
   public static int idlePreviewFPS = 15;
   public static boolean joinTransition = true;
   /** Freeze chunkmap captures after this many ms of generation (-1 = never). */
   public static long chunkMapFreezing = -1L;
   public static boolean checkMinMemory = true;
   private static long lastResetAt;

   private QueueConfig() {
   }

   public static Path path() {
      return FabricLoader.getInstance().getConfigDir().resolve("seedqueue.json");
   }

   public static void load() {
      try {
         Path path = path();

         if (!Files.exists(path)) {
            save();
            return;
         }

         JsonObject json = GSON.fromJson(Files.readString(path), JsonObject.class);
         queueSize = clamp(json.has("queueSize") ? json.get("queueSize").getAsInt() : queueSize, 1, 6);
         chunkRadius = clamp(json.has("chunkRadius") ? json.get("chunkRadius").getAsInt() : chunkRadius, 0, 8);
      showChunkProgress = json.has("showChunkProgress") ? json.get("showChunkProgress").getAsBoolean() : showChunkProgress;
      simulatedWidth = clamp(json.has("simulatedWidth") ? json.get("simulatedWidth").getAsInt() : simulatedWidth, 0, 16384);
      simulatedHeight = clamp(json.has("simulatedHeight") ? json.get("simulatedHeight").getAsInt() : simulatedHeight, 0, 16384);
         useWall = !json.has("useWall") || json.get("useWall").getAsBoolean();
         autoQueue = !json.has("autoQueue") || json.get("autoQueue").getAsBoolean();
         showHud = !json.has("showHud") || json.get("showHud").getAsBoolean();
         perspective = json.has("perspective") ? json.get("perspective").getAsString() : perspective;
         rows = clamp(json.has("rows") ? json.get("rows").getAsInt() : rows, 1, 4);
         columns = clamp(json.has("columns") ? json.get("columns").getAsInt() : columns, 1, 4);
         resetCooldownMs = clamp(json.has("resetCooldownMs") ? json.get("resetCooldownMs").getAsInt() : resetCooldownMs, 0, 5000);
         wallFps = clamp(json.has("wallFps") ? json.get("wallFps").getAsInt() : wallFps, 0, 480);
         previewFps = clamp(json.has("previewFps") ? json.get("previewFps").getAsInt() : previewFps, 5, 120);
         multiView = json.has("multiView") && json.get("multiView").getAsBoolean();
         deleteDiscardedWorlds = !json.has("deleteDiscardedWorlds") || json.get("deleteDiscardedWorlds").getAsBoolean();
         soundVolume = clamp(json.has("soundVolume") ? json.get("soundVolume").getAsInt() : soundVolume, 0, 100);
         bypassWall = json.has("bypassWall") && json.get("bypassWall").getAsBoolean();
         smartSwitch = json.has("smartSwitch") && json.get("smartSwitch").getAsBoolean();
         waitForPreviewSetup = json.has("waitForPreviewSetup") && json.get("waitForPreviewSetup").getAsBoolean();
         benchmarkResets = clamp(json.has("benchmarkResets") ? json.get("benchmarkResets").getAsInt() : benchmarkResets, 1, 2000);
         reduceLevelList = !json.has("reduceLevelList") || json.get("reduceLevelList").getAsBoolean();
         freezeLockedPreviews = json.has("freezeLockedPreviews") && json.get("freezeLockedPreviews").getAsBoolean();
         backgroundExecutorThreadPriority = clamp(
            json.has("backgroundExecutorThreadPriority")
               ? json.get("backgroundExecutorThreadPriority").getAsInt()
               : backgroundExecutorThreadPriority,
            1,
            10
         );
         serverThreadPriority = clamp(
            json.has("serverThreadPriority") ? json.get("serverThreadPriority").getAsInt() : serverThreadPriority, 1, 10
         );
         maxConcurrently = clamp(
            json.has("maxConcurrently") ? json.get("maxConcurrently").getAsInt() : maxConcurrently, 0, 30
         );
         maxConcurrentlyOnWall = clamp(
            json.has("maxConcurrently_onWall")
               ? json.get("maxConcurrently_onWall").getAsInt()
               : json.has("maxConcurrentlyOnWall") ? json.get("maxConcurrentlyOnWall").getAsInt() : maxConcurrentlyOnWall,
            1,
            30
         );
         maxWorldGenerationPercentage = clamp(
            json.has("maxWorldGenerationPercentage")
               ? json.get("maxWorldGenerationPercentage").getAsInt()
               : maxWorldGenerationPercentage,
            0,
            100
         );
         resumeOnFilledQueue = json.has("resumeOnFilledQueue") && json.get("resumeOnFilledQueue").getAsBoolean();
         preparingPreviews = clamp(json.has("preparingPreviews") ? json.get("preparingPreviews").getAsInt() : preparingPreviews, -1, 30);
         backgroundExecutorThreads = clamp(
            json.has("backgroundExecutorThreads") ? json.get("backgroundExecutorThreads").getAsInt() : backgroundExecutorThreads, 0, 256
         );
         wallExecutorThreads = clamp(
            json.has("wallExecutorThreads") ? json.get("wallExecutorThreads").getAsInt() : wallExecutorThreads, 0, 256
         );
         wallExecutorThreadPriority = clamp(
            json.has("wallExecutorThreadPriority") ? json.get("wallExecutorThreadPriority").getAsInt() : wallExecutorThreadPriority, 1, 10
         );
         chunkUpdateThreads = clamp(
            json.has("chunkUpdateThreads") ? json.get("chunkUpdateThreads").getAsInt() : chunkUpdateThreads, 0, 64
         );
         chunkUpdateThreadPriority = clamp(
            json.has("chunkUpdateThreadPriority") ? json.get("chunkUpdateThreadPriority").getAsInt() : chunkUpdateThreadPriority, 1, 10
         );
         seedQueueThreadPriority = clamp(
            json.has("seedQueueThreadPriority") ? json.get("seedQueueThreadPriority").getAsInt() : seedQueueThreadPriority, 1, 10
         );
         showDebugMenu = json.has("showDebugMenu") && json.get("showDebugMenu").getAsBoolean();
         useWatchdog = json.has("useWatchdog") && json.get("useWatchdog").getAsBoolean();
         showChunkMaps = json.has("showChunkMaps") && json.get("showChunkMaps").getAsBoolean();
         startConcurrently = clamp(json.has("startConcurrently") ? json.get("startConcurrently").getAsInt() : startConcurrently, 1, 30);
         prewarmWorlds = clamp(json.has("prewarmWorlds") ? json.get("prewarmWorlds").getAsInt() : prewarmWorlds, 0, 30);
         wallChunkDistance = clamp(
            json.has("wallChunkDistance") ? json.get("wallChunkDistance").getAsInt() : wallChunkDistance, 2, 16
         );
         wallDataLimit = clamp(json.has("wallDataLimit") ? json.get("wallDataLimit").getAsInt() : wallDataLimit, 1, 100);
         System.setProperty("seedqueue.wallDataLimit", String.valueOf(wallDataLimit));
         idlePreviewFPS = clamp(json.has("idlePreviewFPS") ? json.get("idlePreviewFPS").getAsInt() : idlePreviewFPS, 0, 60);
         joinTransition = !json.has("joinTransition") || json.get("joinTransition").getAsBoolean();
         chunkMapFreezing = json.has("chunkMapFreezing") ? json.get("chunkMapFreezing").getAsLong() : chunkMapFreezing;
         checkMinMemory = !json.has("checkMinMemory") || json.get("checkMinMemory").getAsBoolean();
         WallKeybindings.load(json.has("keyBindings") ? json.getAsJsonObject("keyBindings") : null);
         if (!"third_person".equals(perspective)) {
            perspective = "first_person";
         }

         // The wall can only show rows * columns cards at once; keep the queue within that budget.
         queueSize = Math.min(queueSize, rows * columns);
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to load config", t);
      }
   }

   public static void save() {
      try {
         JsonObject json = new JsonObject();
         json.addProperty("queueSize", queueSize);
         json.addProperty("chunkRadius", chunkRadius);
      json.addProperty("showChunkProgress", showChunkProgress);
      json.addProperty("simulatedWidth", simulatedWidth);
      json.addProperty("simulatedHeight", simulatedHeight);
         json.addProperty("useWall", useWall);
         json.addProperty("autoQueue", autoQueue);
         json.addProperty("showHud", showHud);
         json.addProperty("perspective", perspective);
         json.addProperty("rows", rows);
         json.addProperty("columns", columns);
         json.addProperty("resetCooldownMs", resetCooldownMs);
         json.addProperty("wallFps", wallFps);
         json.addProperty("previewFps", previewFps);
         json.addProperty("multiView", multiView);
         json.addProperty("deleteDiscardedWorlds", deleteDiscardedWorlds);
         json.addProperty("soundVolume", soundVolume);
         json.addProperty("bypassWall", bypassWall);
         json.addProperty("smartSwitch", smartSwitch);
         json.addProperty("waitForPreviewSetup", waitForPreviewSetup);
         json.addProperty("benchmarkResets", benchmarkResets);
         json.addProperty("reduceLevelList", reduceLevelList);
         json.addProperty("freezeLockedPreviews", freezeLockedPreviews);
         json.addProperty("backgroundExecutorThreadPriority", backgroundExecutorThreadPriority);
         json.addProperty("serverThreadPriority", serverThreadPriority);
         json.addProperty("maxConcurrently", maxConcurrently);
         json.addProperty("maxConcurrently_onWall", maxConcurrentlyOnWall);
         json.addProperty("maxWorldGenerationPercentage", maxWorldGenerationPercentage);
         json.addProperty("resumeOnFilledQueue", resumeOnFilledQueue);
         json.addProperty("preparingPreviews", preparingPreviews);
         json.addProperty("backgroundExecutorThreads", backgroundExecutorThreads);
         json.addProperty("wallExecutorThreads", wallExecutorThreads);
         json.addProperty("wallExecutorThreadPriority", wallExecutorThreadPriority);
         json.addProperty("chunkUpdateThreads", chunkUpdateThreads);
         json.addProperty("chunkUpdateThreadPriority", chunkUpdateThreadPriority);
         json.addProperty("seedQueueThreadPriority", seedQueueThreadPriority);
         json.addProperty("showDebugMenu", showDebugMenu);
         json.addProperty("useWatchdog", useWatchdog);
         json.addProperty("showChunkMaps", showChunkMaps);
         json.addProperty("startConcurrently", startConcurrently);
         json.addProperty("prewarmWorlds", prewarmWorlds);
         json.addProperty("wallChunkDistance", wallChunkDistance);
         json.addProperty("wallDataLimit", wallDataLimit);
         json.addProperty("idlePreviewFPS", idlePreviewFPS);
         json.addProperty("joinTransition", joinTransition);
         json.addProperty("chunkMapFreezing", chunkMapFreezing);
         json.addProperty("checkMinMemory", checkMinMemory);
         json.add("keyBindings", WallKeybindings.save());
         Path path = path();
         Files.createDirectories(path.getParent());
         Files.writeString(path, GSON.toJson(json));
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to save config", t);
      }
   }

   /** Wall reset throttle (1.21.4 resetCooldown). Returns true when a reset may run now. */
   public static boolean tryResetCooldown() {
      long now = System.currentTimeMillis();

      if (now - lastResetAt < resetCooldownMs) {
         return false;
      }

      lastResetAt = now;
      return true;
   }

   /** How many worlds may generate at the same time (0 in the config means "the whole queue"). */
   public static int concurrency(boolean wall) {
      int value = wall ? maxConcurrentlyOnWall : maxConcurrently;
      return value <= 0 ? Math.max(1, queueSize) : value;
   }

   /** Size of the background world-preparation executor (0 = automatic). */
   public static int workers(boolean wall) {
      int configured = wall ? wallExecutorThreads : backgroundExecutorThreads;

      if (configured > 0) {
         return configured;
      }

      int processors = Runtime.getRuntime().availableProcessors();
      return wall
         ? Math.max(1, Math.min(processors, processors))
         : Math.max(1, Math.min(processors, maxConcurrently + 1));
   }

   /** Chunk builder thread count for previews (0 = automatic, matches 1.21.4 chunkWorkers). */
   public static int chunkWorkers() {
      if (chunkUpdateThreads > 0) {
         return chunkUpdateThreads;
      }

      int cells = Math.max(1, rows * columns);
      int anticipated = Math.max(1, Math.min(Math.max(1, queueSize), cells + (preparingPreviews < 0 ? cells : preparingPreviews)));
      int allocated = Math.max(1, SodiumLevelCache.size() + 1);
      return Math.max(1, Runtime.getRuntime().availableProcessors() / Math.max(anticipated, allocated));
   }

   public static int wallSlots() {
      return Math.max(1, Math.min(queueSize, rows * columns));
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }
}
