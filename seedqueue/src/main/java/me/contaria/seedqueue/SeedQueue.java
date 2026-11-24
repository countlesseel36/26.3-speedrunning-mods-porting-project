package me.contaria.seedqueue;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;
import me.voidxwalker.worldpreview.WorldPreview;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * SeedQueue 门面类。
 * 
 * 对外暴露 Fabric 入口、Mixin 钩子与跨模块调用的静态方法；具体实现拆分到各管理器：
 * QueueManager（队列生命周期）、PreviewManager（预览循环）、WallManager（墙界面）、
 * SessionManager（会话交接）、GenerationManager（生成调度）、CaptureManager（抓帧）、
 * TickManager（主循环）、LifecycleManager（关服/看门狗）、EntryRegistry（实例集合）、
 * DiagnosticsManager / BenchmarkManager / QueueOpsManager / RenderManager / SettingsManager。
 */
public final class SeedQueue implements ClientModInitializer {



   public static final Logger LOGGER = LogManager.getLogger("SeedQueue");
   public static final String BUILD = "seedqueue-26.3-build-" + Integer.getInteger("seedqueue.build", 928);
   public static final boolean ENABLED = !Boolean.getBoolean("seedqueue.disable");
   static final boolean WALL_PROPERTY = Boolean.getBoolean("seedqueue.wall");
   static final List<QueueEntry> ENTRIES = new ArrayList<>();
   static final Random RANDOM = new Random();
   static int refillCooldown;
   static int nextEntryNumber;
   static boolean wallShown;
   static Screen wallReturnScreen;
   static int wallTicks;
   static boolean joinSent;
   static int joinTicks;
   static boolean joinShotDone;
   static boolean escSent;
   static Screen blankCaptureSavedWall;
   static boolean blankCaptureHudHidden;
   static QueueEntry liveEntry;
   static boolean frameTestActive;
   static QueueEntry frameCaptureEntry;
   static int frameCaptureTicks;
   static boolean frameCaptureActive;
   static int previewCycleIndex;
   static boolean wallOpenedInWorld;
   public static boolean joinInProgress;
   static int wallOpenTicks;
   public static boolean pendingGrab;
   public static long grabStartedAt;
   static int grabHoldTicks;
   static QueueEntry previewGrabEntry;
   static boolean frameCaptureWaiting;
   public static int renderedFrames;
   static boolean offscreenPending;
   public static volatile QueueEntry joining;
   public static volatile boolean suppressLoadingScreens;
   static int suppressTicks;
   public static volatile String lastPlayedFolder;
   static boolean levelListLogged;
   public static volatile boolean benchmarking;
   public static volatile int benchmarkCount;
   public static volatile long benchmarkStarted;
   public static volatile long benchmarkFinishedAt;
   public static volatile int benchmarkResultResets;
   public static volatile long benchmarkResultMs;
   static int handoffTicks;
   public static int frameSwapIndex;
   public static long nextFrameSwapAt;
   static final int PREVIEW_DWELL_MS = Math.max(10, Integer.getInteger("seedqueue.previewDwellMs", 30));
   public static final int PREVIEW_GAP_MS = Math.max(0, Integer.getInteger("seedqueue.previewGapMs", 0));
   public static final boolean CAMERA_LOG = Boolean.getBoolean("seedqueue.cameraLog");
   static final boolean FPS_LOG = Boolean.getBoolean("seedqueue.fpsLog");
   static long fpsWindowStart;
   static int fpsFrames;
   public static final List<QueueEntry> FRAME_SWAP_READY = new ArrayList<>();
   static final int PREVIEW_REFRESH_MS = Integer.getInteger("seedqueue.previewRefreshMs", 3000);
   public static volatile boolean shuttingDown;
   static boolean joinedFromWall;
   static Set<ResourceKey<?>> syncedDamageTypes;
   static boolean pendingWallReturn;
   static int wallReturnTicks;
   static long lastWallReturnAt;
   static CameraType wallSavedCameraType;
   static Screen shotWall;
   static int shotPhase;
   static QueueEntry freezeEntry;
   static int freezePhase;
   static int freezeWait;
   static long freezeGrabAt;
   static Screen freezeWall;
   static boolean freezeHudHidden;
   static int timerLogTicks = -1;
   static int previewRadiusBuilt = -1;
   static int timerFinishTicks = -1;
   static boolean wallScreenshotDone;
   static int wallScreenshotTicks;
   static boolean configSmokeDone;
   static long wallDelayStartedAt;
   static boolean configListSmokeDone;
   static int configSmokeTicks;
   static long lastSessionLogAt;
   static Object lastPauseLevel;
   static Field nextTickTimeField;
   static boolean nextTickTimeResolved;
   static long lastGenDiagAt;
   static volatile boolean heapPressure;
   static long lastHeapCheckAt;
   static final ConcurrentHashMap<Integer, long[]> GENERATION_PROGRESS = new ConcurrentHashMap<>();
   static long lastQueueAt;
   static final ConcurrentLinkedQueue<Path> PENDING_DELETES = new ConcurrentLinkedQueue<>();
   static final ConcurrentHashMap<Path, Long> DELETE_ENQUEUED_AT = new ConcurrentHashMap<>();
   static long lastPruneAt;
   static boolean pendingPlayNextLock;
   static long pendingPlayNextLockAt;
   private static long lastPlayNextLockAt;
   static final Object generatingLock = new Object();
   static int generatingNow;
   static int stuckLoadingTicks;
   static int resourceSampleTicks;
   static long benchmarkLastResetAt;
   static QueueEntry spawnAlignEntry;
   static BlockPos spawnAlignPos;
   static long lastHandoffAt;

   /**
    * 当前队列实例的只读列表。
    */
   public static List<QueueEntry> entries() {
      return EntryRegistry.entries();
   }

   private static int previewDwellMs() {
      return PreviewManager.previewDwellMs();
   }

   private static void applyThirdPerson() {
      PreviewManager.applyThirdPerson();
   }

   /**
    * 世界渲染前的预览帧调度（由 WorldPreview 的帧钩子调用）。
    */
   private static void frameSwapRenderTick(Minecraft minecraft) {
      PreviewManager.frameSwapRenderTick(minecraft);
   }

   public static int previewRadius(QueueEntry entry) {
      return PreviewManager.previewRadius(entry);
   }

   private static void frameSwapTick(Minecraft minecraft) {
      PreviewManager.frameSwapTick(minecraft);
   }

   /**
    * 把主渲染目标中的预览画面拷贝到当前卡片的 displayTarget。
    */
   public static void captureWallFrame(Minecraft minecraft) {
      PreviewManager.captureWallFrame(minecraft);
   }

   static void pruneStaleQueueWorlds() {
      QueueManager.pruneStaleQueueWorlds();
   }

   /**
    * 客户端初始化入口：注册 Mixin 钩子、启动看门狗、预热队列并恢复设置。
    */
   public void onInitializeClient() {
      QueueConfig.load();
      pruneStaleQueueWorlds();
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
         try {
            pruneStaleQueueWorlds();
         } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }
      }, "SeedQueue world cleanup"));
      if (QueueConfig.useWatchdog || Boolean.getBoolean("seedqueue.watchdog")) {
         startWatchdog();
      }

      if (QueueConfig.checkMinMemory) {
         long maxMemoryMb = Runtime.getRuntime().maxMemory() / 1048576L;
         long requiredMemoryMb = 1800L + (long)QueueConfig.queueSize * 180L;
         if (maxMemoryMb < requiredMemoryMb) {
            LOGGER.warn(
               "[seedqueue] only {} MB of heap allocated, {} MB recommended for {} queued worlds; increase -Xmx or reduce Max Queued Seeds",
               maxMemoryMb,
               requiredMemoryMb,
               QueueConfig.queueSize
            );
         }
      }

      WorldPreview.frameTickHook = () -> {
         try {
            frameSwapRenderTick(Minecraft.getInstance());
         } catch (Throwable error) {
            LOGGER.warn("[seedqueue] frame swap tick failed", error);
         }
      };
      WorldPreview.instanceActivator = renderProperties -> {
         for (QueueEntry entry : ENTRIES) {
            if (entry.renderProperties == renderProperties && entry.externalPreview != null) {
               try {
                  entry.externalPreview.activateSections();
               } catch (Throwable error) {
                  LOGGER.warn("[seedqueue] failed to activate sections for #{}", entry.number, error);
               }
               break;
            }
         }
      };
      cleanupStaleSaves();
      logMemory("before queue");
      LOGGER.info("[seedqueue] build {}", BUILD);
      LOGGER.info("SeedQueue 26.3 port initialised (test={}, integrate={}, queueSize={}, chunkRadius={})", integrate(), queueSize(), QueueConfig.chunkRadius);
   }


   /**
    * 队列实例的种子来源：-Dseedqueue.fixedSeed > Atum 设置的种子 > 随机。
    * Atum 里填了固定种子时，队列世界会使用同一个种子，方便复现。
    */
   static long nextQueueSeed() {
      String fixed = System.getProperty("seedqueue.fixedSeed");

      if (fixed != null && !fixed.isBlank()) {
         return net.minecraft.world.level.levelgen.WorldOptions.parseSeed(fixed).orElseGet(RANDOM::nextLong);
      }

      try {
         Class<?> atum = Class.forName("me.voidxwalker.autoreset.Atum");
         Object value = atum.getField("seed").get(null);

         if (value instanceof String seedText && !seedText.isBlank()) {
            var parsed = net.minecraft.world.level.levelgen.WorldOptions.parseSeed(seedText);

            if (parsed.isPresent()) {
               return parsed.getAsLong();
            }
         }
      } catch (Throwable ignored) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", ignored);
      }

      return RANDOM.nextLong();
   }

   static void cleanupStaleSaves() {
      QueueManager.cleanupStaleSaves();
   }

   public static void saveAllSettings() {
      SettingsManager.saveAllSettings();
   }

   /**
    * 关服流程：停止预览、保存设置、关闭所有队列服务端。
    */
   public static void shutdown() {
      LifecycleManager.shutdown();
   }

   static void startExitWatchdog() {
      LifecycleManager.startExitWatchdog();
   }


   static void previewCycleTick(Minecraft minecraft) {
      PreviewManager.previewCycleTick(minecraft);
   }




   static void stopPreviewCycle(Minecraft minecraft) {
      PreviewManager.stopPreviewCycle(minecraft);
   }



   public static void afterRenderFrame(Minecraft minecraft) {
      PreviewManager.afterRenderFrame(minecraft);
   }


   /**
    * 当前配置下是否启用墙模式。
    */
   static boolean wallEnabled() {
      return WallManager.wallEnabled();
   }

   static boolean integrate() {
      return TickManager.integrate();
   }

   static int queueSize() {
      return QueueManager.queueSize();
   }

   public static void scheduleAll() {
      WallManager.scheduleAll();
   }

   static void cancelScheduledJoin() {
      WallManager.cancelScheduledJoin();
   }

   static void wallScheduledTick(Minecraft minecraft) {
      WallManager.wallScheduledTick(minecraft);
   }

   public static void playSound(String text) {
      WallManager.playSound(text);
   }


   public static boolean isDamageTypeRegistered(MinecraftServer minecraftServer, ClientboundDamageEventPacket clientboundDamageEventPacket) {
      return TickManager.isDamageTypeRegistered(minecraftServer, clientboundDamageEventPacket);
   }

   public static QueueEntry liveEntry() {
      return SessionManager.liveEntry();
   }

   public static boolean isPlayNextLockPending() {
      return SessionManager.isPlayNextLockPending();
   }

   public static boolean isTransitionPending() {
      return SessionManager.isTransitionPending();
   }

   public static boolean returnToWallFromJoinedWorld(Minecraft minecraft) {
      return WallManager.returnToWallFromJoinedWorld(minecraft);
   }

   public static void prepareForVanillaWorldCreation(Minecraft minecraft) {
      WallManager.prepareForVanillaWorldCreation(minecraft);
   }

   public static boolean openWall(Minecraft minecraft) {
      return WallManager.openWall(minecraft);
   }

   public static void onWallClosed(Minecraft minecraft) {
      WallManager.onWallClosed(minecraft);
   }

   static boolean shotPreviewEnabled() {
      return CaptureManager.shotPreviewEnabled();
   }





   static void tickShotTeardown(Minecraft minecraft) {
      CaptureManager.tickShotTeardown(minecraft);
   }



   public static boolean wallPreviewEnabled() {
      return WallManager.wallPreviewEnabled();
   }

   static void freezeReadyEntries(Minecraft minecraft) {
      QueueManager.freezeReadyEntries(minecraft);
   }

   static boolean freezeCaptureEnabled() {
      return CaptureManager.freezeCaptureEnabled();
   }

   static void tickFreezeCapture(Minecraft minecraft) {
      CaptureManager.tickFreezeCapture(minecraft);
   }

   static void finishFreezeCapture(Minecraft minecraft, QueueEntry entry) {
      CaptureManager.finishFreezeCapture(minecraft, entry);
   }

   static void handoffTick(Minecraft minecraft) {
      SessionManager.handoffTick(minecraft);
   }

   static void restoreRespawnRadius(Minecraft minecraft) {
      SessionManager.restoreRespawnRadius(minecraft);
   }

   static void alignPlayerToPreviewedSpawn(QueueEntry entry, Minecraft minecraft) {
      SessionManager.alignPlayerToPreviewedSpawn(entry, minecraft);
   }

   static void sessionDiagnostics() {
      DiagnosticsManager.sessionDiagnostics();
   }

   public static void logMemory(String text) {
      DiagnosticsManager.logMemory(text);
   }

   public static void resetTickTiming(Object obj) {
      SessionManager.resetTickTiming(obj);
   }

   /**
    * 客户端每 tick 的主入口：推进队列、墙状态、预览与加入流程。
    */
   public static void tick(Minecraft minecraft) {
      TickManager.tick(minecraft);
   }

   static void startWatchdog() {
      LifecycleManager.startWatchdog();
   }

   static void applyGenerationPermits(Minecraft minecraft) {
      GenerationManager.applyGenerationPermits(minecraft);
   }

   static boolean canStartMoreEntries(Minecraft minecraft) {
      return GenerationManager.canStartMoreEntries(minecraft);
   }

   static boolean prewarmAllowsStart(Minecraft minecraft) {
      return GenerationManager.prewarmAllowsStart(minecraft);
   }

   static void memoryGuard() {
      DiagnosticsManager.memoryGuard();
   }

   static void logGenerationState() {
      DiagnosticsManager.logGenerationState();
   }

   public static void discardAndRebuildForSettingsChange() {
      SettingsManager.discardAndRebuildForSettingsChange();
   }

   static void keepWallQueueFilled(Minecraft minecraft) {
      TickManager.keepWallQueueFilled(minecraft);
   }

   static void integrateTick(Minecraft minecraft) {
      TickManager.integrateTick(minecraft);
   }

   static void evictPreviewCaches(String text) {
      int count = 0;

      for (QueueEntry entry : ENTRIES) {
         if (entry.externalPreview != null && entry.externalPreview.level != null) {
            SodiumLevelCache.evictLater(entry.externalPreview.level);
            entry.externalPreview.invalidateSections();
            count++;
         }
      }

      LOGGER.info("[seedqueue] evicting {} preview section managers ({})", count, text);
   }

   static boolean isResetInProgress() {
      return SessionManager.isResetInProgress();
   }

   public static boolean consumeForReset(Minecraft minecraft) {
      return SessionManager.consumeForReset(minecraft);
   }

   static int resetAllUnlocked() {
      return SessionManager.resetAllUnlocked();
   }

   static boolean canQueueAnother() {
      return GenerationManager.canQueueAnother();
   }

   public static void enqueueWorldDeletion(Path path) {
      QueueOpsManager.enqueueWorldDeletion(path);
   }

   static void resumePausedGeneration() {
      GenerationManager.resumePausedGeneration();
   }

   static void processPendingDeletions() {
      QueueOpsManager.processPendingDeletions();
   }

   static void processOnePendingDeletion() {
      QueueOpsManager.processOnePendingDeletion();
   }

   static void pruneWhenQueueDrained() {
      QueueOpsManager.pruneWhenQueueDrained();
   }

   static void discardAsync(QueueEntry entry) {
      QueueManager.discardAsync(entry);
   }

   public static int generatingCount() {
      return GenerationManager.generatingCount();
   }

   public static int entriesCount() {
      return EntryRegistry.entriesCount();
   }

   public static Set<String> activeWorldFolders() {
      return QueueOpsManager.activeWorldFolders();
   }

   static boolean ensurePreview(Minecraft minecraft, QueueEntry entry) {
      return RenderManager.ensurePreview(minecraft, entry);
   }

   public static boolean queueIsFull() {
      return GenerationManager.queueIsFull();
   }

   public static List<QueueEntry> entriesSnapshot() {
      return EntryRegistry.entriesSnapshot();
   }

   static int parkedCount() {
      return EntryRegistry.parkedCount();
   }

   static void enforceParkedLimit() {
      EntryRegistry.enforceParkedLimit();
   }

   public static void refillQueue(Minecraft minecraft) {
      QueueManager.refillQueue(minecraft);
   }

   public static void resetEntry(QueueEntry entry) {
      QueueOpsManager.resetEntry(entry);
   }

   public static void resetEntries(Collection<QueueEntry> items) {
      QueueOpsManager.resetEntries(items);
   }

   public static void discardEntry(QueueEntry entry) {
      QueueOpsManager.discardEntry(entry);
   }

   public static void onWindowResize(int count, int count2) {
      RenderManager.onWindowResize(count, count2);
   }

   static void resizeTarget(RenderTarget target, int count, int count2) {
      RenderManager.resizeTarget(target, count, count2);
   }

   static boolean stopWallPreview(Minecraft minecraft, QueueEntry entry) {
      return CaptureManager.stopWallPreview(minecraft, entry);
   }

   public static void onLevelSet() {
      SessionManager.onLevelSet();
   }

   public static void logEntryDebug(QueueEntry entry) {
      TickManager.logEntryDebug(entry);
   }

   public static void logServerThreadStack(QueueEntry entry) {
      TickManager.logServerThreadStack(entry);
   }

   public static void requestPlayNextLock() {
      pendingPlayNextLock = true;
      pendingPlayNextLockAt = System.currentTimeMillis();
   }

   public static boolean hasLockedCandidate() {
      return EntryRegistry.hasLockedCandidate();
   }

   public static boolean isBackgroundGenerationPaused() {
      return GenerationManager.isBackgroundGenerationPaused();
   }

   static int effectiveGeneratingLimit() {
      return GenerationManager.effectiveGeneratingLimit();
   }

   public static void acquireGeneratingPermit() {
      GenerationManager.acquireGeneratingPermit();
   }

   public static void releaseGeneratingPermit() {
      GenerationManager.releaseGeneratingPermit();
   }

   static String sodiumVersion() {
      return TickManager.sodiumVersion();
   }

   static void resourceSampleTick(Minecraft minecraft) {
      BenchmarkManager.resourceSampleTick(minecraft);
   }

   public static void startBenchmark() {
      BenchmarkManager.startBenchmark();
   }

   public static void cancelBenchmark() {
      BenchmarkManager.cancelBenchmark();
   }

   public static boolean playNextLock(Minecraft minecraft) {
      if (System.currentTimeMillis() - lastPlayNextLockAt < 750L) {
         LOGGER.info("[seedqueue] play next lock: ignored (debounce)");
         return false;
      } else {
         lastPlayNextLockAt = System.currentTimeMillis();
         QueueEntry entry = null;

         for (QueueEntry candidate : ENTRIES) {
            if (candidate.locked && !candidate.transferred && (entry == null || candidate.lockedAt < entry.lockedAt)) {
               entry = candidate;
            }
         }

         if (entry == null) {
            int count = 0;
            int count2 = 0;
            StringBuilder builder = new StringBuilder();

            for (QueueEntry entry3 : ENTRIES) {
               if (entry3.locked) {
                  count++;
                  if (entry3.state == QueueEntry.State.READY && !entry3.transferred) {
                     count2++;
                  }

                  builder.append(" #").append(entry3.number).append(':').append(entry3.state).append(entry3.transferred ? "(transferred)" : "");
               }
            }

            LOGGER.warn("[seedqueue] play next lock: no locked ready entry (entries={}, locked={}, lockedReady={} ->{})", ENTRIES.size(), count, count2, builder);
            return false;
         } else {
            LOGGER.info("[seedqueue] play next lock: entry #{}", entry.number);
            if (entry.state != QueueEntry.State.READY) {
               entry.selected = true;
               LOGGER.info("[seedqueue] play next lock: entry #{} is {} - selected until ready", entry.number, entry.state);
               return true;
            } else {
               /**
                * 进入指定队列实例（必要时先排队等待其就绪）。
                */
               return playEntry(entry);
            }
         }
      }
   }

   public static boolean playEntry(QueueEntry entry) {
      return QueueManager.playEntry(entry);
   }


}
