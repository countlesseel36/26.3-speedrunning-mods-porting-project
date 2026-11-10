package me.contaria.seedqueue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** SeedQueue 26.3 port (work in progress). */
public final class SeedQueue implements ClientModInitializer {
   public static final Logger LOGGER = LogManager.getLogger("SeedQueue");
   public static final String BUILD = "seedqueue-26.3-build-" + Integer.getInteger("seedqueue.build", 928);
   public static final boolean ENABLED = !Boolean.getBoolean("seedqueue.disable");
   private static final boolean TEST = Boolean.getBoolean("seedqueue.test");
   private static final boolean HANDOFF = Boolean.getBoolean("seedqueue.handoff");
   private static final boolean WALL_PROPERTY = Boolean.getBoolean("seedqueue.wall");
   private static final List<QueueEntry> ENTRIES = new ArrayList<>();

   /** Read-only view of the queue for the extracted preview pipeline. */
   static List<QueueEntry> entries() {
      return java.util.Collections.unmodifiableList(ENTRIES);
   }
   private static final Random RANDOM = new Random();
   /** Ticks until the next refill check; keeps the queue from re-scanning every single tick. */
   private static int refillCooldown;
   private static int nextEntryNumber;
   private static boolean testStarted;
   private static boolean testDiscarded;
   private static boolean wallShown;
   private static net.minecraft.client.gui.screens.Screen wallReturnScreen;
   private static int wallTicks;
   private static int wallKeyTicks;
   private static boolean wallKeySent;
   private static int f6Ticks;
   private static boolean f6Sent;
   private static boolean joinSent;
   private static boolean wallF6Sent;
   private static int joinSmokeCount;
   private static int joinTicks;
   private static boolean joinShotDone;
   private static boolean escSent;
   private static int previewSettleTicks;
   private static boolean previewShotDone;
   private static boolean f3Sent;
   private static boolean lockSent;
   private static boolean deleteSent;
   private static boolean previewShot;
   private static int previewShotTicks;
   private static net.minecraft.client.gui.screens.Screen blankCaptureSavedWall;
   private static boolean blankCaptureHudHidden;
   private static int restoreTicks = -1;
   private static boolean frameCaptured;
   private static QueueEntry liveEntry;
   private static final java.util.concurrent.atomic.AtomicInteger RENDER_LOG_COUNTER = new java.util.concurrent.atomic.AtomicInteger();
   private static final java.util.concurrent.atomic.AtomicInteger SUCCESS_LOG_COUNTER = new java.util.concurrent.atomic.AtomicInteger();
   private static final java.util.concurrent.atomic.AtomicInteger READY_LOG_COUNTER = new java.util.concurrent.atomic.AtomicInteger();
   private static int renderCursor;
   private static QueueEntry frameTestEntry;
   private static boolean frameTestArmed;
   private static boolean frameTestActive;
   private static boolean frameTestDone;
   private static boolean frameTestDumpPending;
   private static QueueEntry frameCaptureEntry;
   private static int frameCaptureTicks;
   private static boolean frameCaptureActive;
   static int previewFramesLeft;
   static boolean previewCaptured;
   private static int previewCycleIndex;
   /** True when the wall was opened from a loaded world, so joining from it is expected. */
   private static boolean wallOpenedInWorld;

   /**
    * True from the moment a queued world starts joining until the client owns it.
    *
    * <p>The wall must not arm a single preview in that window: it would swap a preview level into
    * {@code Minecraft.level} right after the join and leave it there (the world then renders through
    * the preview camera/level - wrong distant terrain, low fps, and a stuck shutdown).</p>
    */
   static boolean joinInProgress;
   private static boolean copyDumped;
   private static int wallOpenTicks;
   static boolean pendingGrab;
   static long grabStartedAt;
   private static int grabHoldTicks;
   private static QueueEntry previewGrabEntry;
   private static boolean pendingPreviewCapture;
   private static QueueEntry pendingPreviewEntry;
   private static boolean frameCaptureDone;
   private static long frameCaptureStartMillis;
   private static boolean frameCaptureWaiting;
   private static int frameCaptureCleanupTicks;
   static int renderedFrames;
   private static int offscreenTicks;
   private static boolean offscreenDone;
   private static int wallShotTicks;
   private static boolean wallShotDone;
   private static int wallShotPending;
   private static boolean offscreenPending;
   private static boolean offscreenSwapped;
   private static boolean lockResetDone;
   private static boolean previewSmokeDone;
   private static int discordCountdown = -1;
   private static boolean selfTestDone;

   /** Set while a queued world is being handed to the client; read by MinecraftDoWorldLoadMixin. */
   public static volatile QueueEntry joining;
   /** While true, the title/loading screens of a world hand-over are suppressed (wall stays visible). */
   public static volatile boolean suppressLoadingScreens;
   private static int suppressTicks;
   public static volatile String lastPlayedFolder;
   private static boolean levelListLogged;
   /** 1.21.4 benchmark mode: repeatedly discard ready unlocked worlds and time the throughput. */
   public static volatile boolean benchmarking;
   public static volatile int benchmarkCount;
   public static volatile long benchmarkStarted;
   public static volatile long benchmarkFinishedAt;
   public static volatile int benchmarkResultResets;
   public static volatile long benchmarkResultMs;
   private static int handoffTicks;

   // ------------------------------------------------ per-card frame capture
   static int frameSwapIndex;
   static long nextFrameSwapAt;

   private static final int PREVIEW_DWELL_MS = Math.max(10, Integer.getInteger("seedqueue.previewDwellMs", 30));
   static final int PREVIEW_GAP_MS = Math.max(0, Integer.getInteger("seedqueue.previewGapMs", 0));
   static final boolean CARD_READBACK = Boolean.getBoolean("seedqueue.cardReadback");
   static final boolean CAMERA_LOG = Boolean.getBoolean("seedqueue.cameraLog");
   private static final boolean RENDER_LOG = Boolean.getBoolean("seedqueue.renderLog");
   private static final boolean FPS_LOG = Boolean.getBoolean("seedqueue.fpsLog");
   private static long fpsWindowStart;
   private static int fpsFrames;
   static final java.util.List<QueueEntry> FRAME_SWAP_READY = new java.util.ArrayList<>();
   private static Boolean lastThirdPerson;

   private static int previewDwellMs() {
      return PREVIEW_DWELL_MS;
   }

   private static final int PREVIEW_REFRESH_MS = Integer.getInteger("seedqueue.previewRefreshMs", 3000);

   /** Avoids a synchronized System.setProperty on every preview arm; the value rarely changes. */
   private static void applyThirdPerson() {
      PreviewPipeline.applyPerspective();
   }

   /**
    * Lets one queued world become {@code Minecraft.level} for a short dwell so the regular render
    * pipeline (frame graph included, Sodium terrain included) draws it into the main render target.
    * Every frame of the dwell is copied into the card texture by
    * {@link #captureWallFrame(Minecraft)}; after the dwell the next card gets its turn.
    *
    * <p>The dwell matters: Sodium compiles sections asynchronously, and rebinding its renderer
    * destroys the compiled geometry, so a card needs a moment on screen before its pixels exist.</p>
    */
   /** Per-frame entry point: switches the wall's preview at frame rate (one world per frame). */
   private static void frameSwapRenderTick(Minecraft minecraft) {
      if (!(minecraft.gui.screen() instanceof WallScreen)) {
         return;
      }

      if (FPS_LOG) {
         fpsFrames++;
         long now = System.currentTimeMillis();
         long elapsed = now - fpsWindowStart;

         if (elapsed >= 2000) {
            LOGGER.info("[seedqueue] wall fps ~{} ({} frames / {} ms)", fpsFrames * 1000 / elapsed, fpsFrames, elapsed);
            fpsWindowStart = now;
            fpsFrames = 0;
         }
      }

      frameSwapTick(minecraft);
   }

   /**
    * Radius used for preview levels. It must not exceed the generated chunk radius, otherwise the
    * preview renders a large band of empty chunks (the "world not fully loaded" look). Wall Preview
    * Distance can only lower it.
    */
   /** Render distance used while a preview level is bound, or -1 when the real level is rendered. */

   public static int previewRadius(QueueEntry entry) {
      int generated = Math.max(1, entry == null ? QueueConfig.chunkRadius : entry.chunkRadius);
      return Math.max(1, Math.min(QueueConfig.wallChunkDistance, generated));
   }

   private static void frameSwapTick(Minecraft minecraft) {
      PreviewPipeline.frameSwapTick(minecraft);
   }

   public static void captureWallFrame(Minecraft minecraft) {
      PreviewPipeline.captureWallFrame(minecraft);
   }

   @Override
   public void onInitializeClient() {
      QueueConfig.load();

      if (QueueConfig.useWatchdog || Boolean.getBoolean("seedqueue.watchdog")) {
         startWatchdog();
      }

      if (QueueConfig.checkMinMemory) {
         long maxMegabytes = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
         long recommended = 1800L + QueueConfig.queueSize * 180L;

         if (maxMegabytes < recommended) {
            LOGGER.warn(
               "[seedqueue] only {} MB of heap allocated, {} MB recommended for {} queued worlds; increase -Xmx or reduce Max Queued Seeds",
               maxMegabytes,
               recommended,
               QueueConfig.queueSize
            );
         }
      }

      me.voidxwalker.worldpreview.WorldPreview.frameTickHook = () -> {
         try {
            frameSwapRenderTick(Minecraft.getInstance());
         } catch (Throwable t) {
            LOGGER.warn("[seedqueue] frame swap tick failed", t);
         }
      };
      me.voidxwalker.worldpreview.WorldPreview.instanceActivator = props -> {
         for (QueueEntry entry : ENTRIES) {
            if (entry.renderProperties == props && entry.externalPreview != null) {
               try {
                  entry.externalPreview.activateSections();
               } catch (Throwable t) {
                  LOGGER.warn("[seedqueue] failed to activate sections for #{}", entry.number, t);
               }

               break;
            }
         }
      };
      cleanupStaleSaves();
      logMemory("before queue");
      LOGGER.info("[seedqueue] build {}", BUILD);
      LOGGER.info(
         "SeedQueue 26.3 port initialised (test={}, integrate={}, queueSize={}, chunkRadius={})",
         TEST,
         integrate(),
         queueSize(),
         QueueConfig.chunkRadius
      );
   }

   /** Deletes queue worlds left behind by a crashed or killed session. */
   private static void cleanupStaleSaves() {
      try {
         Path saves = FabricLoader.getInstance().getGameDir().resolve("saves");

         if (!Files.isDirectory(saves)) {
            return;
         }

         long cutoff = System.currentTimeMillis() - 300_000L;
         int removed = 0;

         try (var stream = Files.list(saves)) {
            for (Path path : stream.filter(Files::isDirectory).toList()) {
               String name = path.getFileName().toString();

               if (!name.startsWith("SeedQueue-")) {
                  continue;
               }

               // Worlds that were played from the wall belong to the player now; never delete them.
               if (Files.exists(path.resolve("seedqueue_played.marker"))) {
                  continue;
               }

               if (Files.getLastModifiedTime(path).toMillis() > cutoff || !Files.exists(path.resolve("level.dat"))) {
                  continue;
               }

               try (var files = Files.walk(path)) {
                  files.sorted(java.util.Comparator.reverseOrder()).forEach(file -> {
                     try {
                        Files.deleteIfExists(file);
                     } catch (Throwable ignored) {
                     }
                  });
               }

               removed++;
            }
         }

         if (removed > 0) {
            LOGGER.info("[seedqueue] cleaned {} stale queue saves", removed);
         }
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] failed to clean stale queue saves", t);
      }
   }

   /** Stops all background servers and removes their worlds when the client exits. */
   /** True once the client is exiting: GPU work must be skipped, the context is going away. */
   public static volatile boolean shuttingDown;

   public static void shutdown() {
      // Arm the watchdog *first*: halting hundreds of queued servers can take far longer than 4s, and
      // while this loop runs the old code had not started the watchdog yet, so the window froze with
      // no way out (the "large batch of seeds then exit hangs" report).
      startExitWatchdog();

      shuttingDown = true;
      for (QueueEntry entry : new ArrayList<>(ENTRIES)) {
         // Wake and halt the background server first: its thread is not a daemon, so a parked server
         // kept the JVM alive after the window closed (the exit watchdog then had to kill the process).
         try {
            entry.parked = false;

            if (entry.server != null) {
               entry.server.halt(false);
            }
         } catch (Throwable t) {
            LOGGER.warn("[seedqueue] failed to halt queued server #{}", entry.number, t);
         }

         entry.discard();
      }

      if (liveEntry != null) {
         me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
         liveEntry.previewActive = false;
         liveEntry = null;
      }

      ENTRIES.clear();
      QueueExecutors.shutdown();
      startExitWatchdog();
      LOGGER.info("[seedqueue] shut down background queue");
   }

   /**
    * Last resort for "the game does not close": some other mod keeps a non-daemon thread pool alive,
    * so the JVM stays up after {@code Minecraft.close()} has already saved and closed the world. The
    * client shows its window gone but the process never ends. Halt the JVM if it is still alive a
    * few seconds later. Disable with {@code -Dseedqueue.exitWatchdog=false}.
    */
   private static void startExitWatchdog() {
      if (!Boolean.parseBoolean(System.getProperty("seedqueue.exitWatchdog", "true"))) {
         return;
      }

      long timeoutMs = Math.max(1000L, Long.getLong("seedqueue.exitWatchdogMs", 4000L));

      Thread watchdog = new Thread(() -> {
         try {
            Thread.sleep(timeoutMs);
         } catch (InterruptedException ignored) {
            return;
         }

         // Diagnose the hang: list the non-daemon threads that keep the JVM alive.
         try {
            for (java.util.Map.Entry<Thread, StackTraceElement[]> entry
                  : Thread.getAllStackTraces().entrySet()) {
               Thread thread = entry.getKey();

               if (thread.isDaemon() || !thread.isAlive()) {
                  continue;
               }

               LOGGER.warn("[seedqueue] still alive: {} ({})", thread.getName(), thread.getState());

               // Top frames tell which library created the pool that keeps the JVM alive.
               StackTraceElement[] stack = entry.getValue();

               for (int frame = 0; frame < Math.min(18, stack.length); frame++) {
                  LOGGER.warn("[seedqueue]   at {}", stack[frame]);
               }
            }
         } catch (Throwable ignored) {
         }

         LOGGER.warn(
            "[seedqueue] the client is still running {}ms after shutdown, halting the JVM", timeoutMs
         );
         Runtime.getRuntime().halt(0);
      }, "SeedQueue exit watchdog");
      watchdog.setDaemon(true);
      watchdog.start();
   }

   /** Renders the active instance world into the off-screen target via the level renderer. */
   private static void renderOffscreen(Minecraft minecraft) {
      try {
         com.mojang.blaze3d.pipeline.RenderTarget target = me.voidxwalker.worldpreview.WorldPreview.captureTarget(minecraft);

         if (target == null) {
            return;
         }

         me.voidxwalker.worldpreview.WorldPreview.setTargetOverride(target);
         me.voidxwalker.worldpreview.WorldPreview.swapIn(minecraft);

         try {
            minecraft.gameRenderer.extract(minecraft.getDeltaTracker(), false);
            minecraft.gameRenderer.renderLevel();
         } finally {
            me.voidxwalker.worldpreview.WorldPreview.swapOut(minecraft);
            me.voidxwalker.worldpreview.WorldPreview.setTargetOverride(null);
         }

         net.minecraft.client.Screenshot.grab(
            new java.io.File("seedqueue_offscreen.png"), target, component -> { }
         );
         LOGGER.info("[seedqueue] offscreen render written");
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] offscreen render failed", t);
      }
   }

   /** Loads the screenshot produced by the grab-based preview capture into the entry texture. */
   private static void consumeGrab(Minecraft minecraft) {
      if (grabStartedAt <= 0 || previewGrabEntry == null) {
         return;
      }

      java.io.File dir = new java.io.File(minecraft.gameDirectory, "screenshots");

      if (!dir.isDirectory()) {
         return;
      }

      java.io.File newest = null;

      for (java.io.File file : dir.listFiles()) {
         if (!file.isFile() || !file.getName().endsWith(".png")) {
            continue;
         }

         if (file.lastModified() >= grabStartedAt - 1000 && file.length() > 1000) {
            if (newest == null || file.lastModified() > newest.lastModified()) {
               newest = file;
            }
         }
      }

      if (newest == null) {
         if (System.currentTimeMillis() - grabStartedAt > 3000) {
            grabStartedAt = 0;
            previewGrabEntry = null;
         }
         return;
      }

      QueueEntry entry = previewGrabEntry;
      previewGrabEntry = null;
      grabStartedAt = 0;

      try {
         entry.nativePreview = com.mojang.blaze3d.platform.NativeImage.read(
            java.nio.file.Files.newInputStream(newest.toPath())
         );
         java.nio.file.Files.deleteIfExists(newest.toPath());
         entry.uploadNativePreview();
         entry.lastPreviewAt = System.currentTimeMillis();
         LOGGER.info("[seedqueue] captured live preview for #{}", entry.number);
      } catch (Throwable t) {
         if (System.currentTimeMillis() - grabStartedAt > 3000) {
            grabStartedAt = 0;
            previewGrabEntry = null;
            try {
               java.nio.file.Files.deleteIfExists(newest.toPath());
            } catch (Throwable ignored) {
            }
         }
         LOGGER.warn("[seedqueue] failed to load grabbed preview", t);
      }
   }
   /** Activates the external preview for one queued instance at a time and captures its frame. */
   private static void previewCycleTick(Minecraft minecraft) {
      if (shotPhase != 0 || joinInProgress || PreviewState.handoffPreviewEntry != null) {
         return;
      }

      if (PreviewState.activePreviewEntry != null) {
         return;
      }

      if (!(minecraft.gui.screen() instanceof WallScreen) || ENTRIES.isEmpty()) {
         return;
      }

      long now = System.currentTimeMillis();
      long refresh = PREVIEW_REFRESH_MS;
      int size = ENTRIES.size();
      me.voidxwalker.worldpreview.WorldPreview.hideDebugOverlay = true;

      for (int i = 0; i < size; i++) {
         QueueEntry entry = ENTRIES.get(previewCycleIndex++ % size);

         if (entry.state != QueueEntry.State.READY || entry.server == null) {
            continue;
         }

         // Entries without a preview are always captured first; existing ones refresh on a timer.
         if (entry.previewTexture != null && now - entry.lastPreviewAt < refresh) {
            continue;
         }

         applyThirdPerson();

         if (entry.externalPreview == null) {
            entry.externalPreview = me.voidxwalker.worldpreview.ExternalPreview.build(minecraft, entry.server, previewRadius(entry));
         }

         applyThirdPerson();

         if (entry.externalPreview == null) {
            continue;
         }

         if (entry.renderProperties == null) {
            entry.renderProperties = new me.voidxwalker.worldpreview.WorldPreviewProperties(
               entry.externalPreview.level,
               entry.externalPreview.listener,
               entry.externalPreview.gameMode,
               entry.externalPreview.player,
               entry.externalPreview.spawn
            ).withCamera(entry.externalPreview.cameraAnchor);
         }

         if (Boolean.parseBoolean(System.getProperty("seedqueue.cpuPreview", "false"))) {
            try {
               long started = System.nanoTime();
               com.mojang.blaze3d.platform.NativeImage image = SpawnViewRenderer.render(
                  entry.externalPreview.level,
                  entry.externalPreview.spawn,
                  0.0,
                  0.0,
                  false
               );
               entry.nativePreview = image;
               entry.uploadNativePreview();
               entry.lastPreviewAt = System.currentTimeMillis();
               LOGGER.info("[seedqueue] cpu preview #{} in {}ms", entry.number, (System.nanoTime() - started) / 1_000_000L);
            } catch (Throwable t) {
               LOGGER.warn("[seedqueue] cpu preview failed", t);
            }

            return;
         }

         me.voidxwalker.worldpreview.WorldPreview.activateExternal(entry.renderProperties);
         me.voidxwalker.worldpreview.WorldPreview.prepareExternal(minecraft);
         // The wall screen pauses the level; force the preview level to render into the main target.
         me.voidxwalker.worldpreview.WorldPreview.forceLevelRender = true;

         if (entry.renderTarget == null) {
            entry.renderTarget = me.voidxwalker.worldpreview.WorldPreview.createTarget(
               minecraft, "seedqueue card " + entry.number, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()
            );
         }

         if (entry.displayTarget == null) {
            entry.displayTarget = me.voidxwalker.worldpreview.WorldPreview.createTarget(
               minecraft, "seedqueue display " + entry.number, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()
            );
         }

         me.voidxwalker.worldpreview.WorldPreview.setTargetOverride(entry.renderTarget);
         PreviewState.activePreviewEntry = entry;
         previewFramesLeft = 10;
         previewCaptured = false;
         return;
      }
   }

   /** Advances an active preview cycle: request the capture, then deactivate after a few frames. */
   private static void previewCycleFrame(Minecraft minecraft) {
      if (PreviewState.activePreviewEntry == null) {
         return;
      }

      if (!(minecraft.gui.screen() instanceof WallScreen) && blankCaptureSavedWall == null) {
         stopPreviewCycle(minecraft);
         return;
      }

      previewFramesLeft--;

      if (!previewCaptured && previewFramesLeft <= 4) {
         previewCaptured = true;
         QueueEntry entry = PreviewState.activePreviewEntry;
         previewGrabEntry = entry;
         grabHoldTicks = 4;
         pendingGrab = true;
         me.voidxwalker.worldpreview.WorldPreview.suppressDebugOverlay = true;

         // Render the preview world without any screen so the frame is world-only and grab-able.
         blankCaptureSavedWall = minecraft.gui.screen();
         minecraft.gui.setScreen(new BlankScreen());

         if (!minecraft.gui.hud.isHidden()) {
            blankCaptureHudHidden = true;
            minecraft.gui.hud.toggle();
         }
      }

      if (previewFramesLeft <= 0 && !pendingGrab && grabStartedAt == 0 && grabHoldTicks <= 0) {
         stopPreviewCycle(minecraft);
      }
   }

   /** Fully restores the game state used for preview rendering. */
   public static QueueEntry activePreviewEntryForDebug() {
      return PreviewState.activePreviewEntry;
   }

   public static boolean isCaptureFrameActive() {
      return PreviewCapture.active;
   }

   public static void stopPreviewCycle(Minecraft minecraft) {
      PreviewPipeline.stop(minecraft);
   }
   /** Renders up to N ready instances into their own targets, once per frame. */
   public static void renderInstances(Minecraft minecraft) {
      if (Boolean.parseBoolean(System.getProperty("seedqueue.livePreview", "false")) && !Boolean.getBoolean("seedqueue.manualPreview")) {
         previewCycleFrame(minecraft);
         return;
      }

      if (frameCaptureActive) {
         frameCaptureTicks++;
         me.voidxwalker.worldpreview.WorldPreview.instanceRendering = !Boolean.getBoolean("seedqueue.blankPreview");

         me.voidxwalker.worldpreview.WorldPreview.populateVisibleSections(minecraft);

         if (frameCaptureTicks == 30) {
            frameCaptureStartMillis = System.currentTimeMillis();
            java.io.File dump = new java.io.File("seedqueue_spawncheck.png").getAbsoluteFile();
            me.voidxwalker.worldpreview.WorldPreview.requestCapture(image -> {
               try {
                  image.writeToFile(dump);
                  LOGGER.info("[seedqueue] wrote spawn frame {}", dump);
               } catch (Throwable t) {
                  LOGGER.warn("[seedqueue] failed to write spawn frame", t);
               }
            });
         }
      }

      if (frameTestDumpPending && frameTestEntry != null && frameTestEntry.renderTarget != null) {
         frameTestDumpPending = false;
         java.io.File dump = new java.io.File("seedqueue_frame_target.png").getAbsoluteFile();
         me.voidxwalker.worldpreview.WorldPreview.captureImage(frameTestEntry.renderTarget, image -> {
            try {
               image.writeToFile(dump);
               LOGGER.info("[seedqueue] wrote frame override target {}", dump);
            } catch (Throwable t) {
               LOGGER.warn("[seedqueue] failed to write frame override target", t);
            }
         });
      }

      if (frameTestArmed && !frameTestActive && frameTestEntry != null && frameTestEntry.renderTarget != null) {
         frameTestArmed = false;
         frameTestActive = true;
         me.voidxwalker.worldpreview.WorldPreview.setTargetOverride(frameTestEntry.renderTarget);
         LOGGER.info("[seedqueue] frame override active for #{}", frameTestEntry.number);
         return;
      }

      if (!(minecraft.gui.screen() instanceof WallScreen) || ENTRIES.isEmpty()) {
         return;
      }

      if (!Boolean.getBoolean("seedqueue.offscreenPreview")) {
         return;
      }

      int budget = Math.max(1, Integer.getInteger("seedqueue.renderBudget", 1));
      if (Boolean.getBoolean("seedqueue.renderLog") && RENDER_LOG_COUNTER.getAndIncrement() < 5) {
         LOGGER.info("[seedqueue] renderInstances called, entries={}", ENTRIES.size());
      }

      for (int attempt = 0; attempt < ENTRIES.size() * 2 && budget > 0; attempt++) {
         QueueEntry entry = ENTRIES.get(renderCursor++ % ENTRIES.size());

         if (Boolean.getBoolean("seedqueue.renderLog") && entry.state == QueueEntry.State.READY
            && READY_LOG_COUNTER.getAndIncrement() < 20) {
            LOGGER.info(
               "[seedqueue] render attempt: state={} server={} preview={} props={} target={}",
               entry.state,
               entry.server != null,
               entry.externalPreview != null,
               entry.renderProperties != null,
               entry.renderTarget != null
            );
         }

         if (entry.state != QueueEntry.State.READY || entry.server == null) {
            continue;
         }

         try {
            // Wall previews are always rendered first person: the third-person camera sat several blocks
      // behind the player and the extra frustum area was never generated, which left a black band at
      // the bottom of the cards. The player's own perspective no longer affects the wall.
      System.setProperty("worldpreview.thirdPerson", "false");
      // Simulated window size for previews (0 = use the real window).
      System.setProperty("worldpreview.simulatedWidth", String.valueOf(QueueConfig.simulatedWidth));
      System.setProperty("worldpreview.simulatedHeight", String.valueOf(QueueConfig.simulatedHeight));

         if (entry.externalPreview == null) {
               entry.externalPreview = me.voidxwalker.worldpreview.ExternalPreview.build(minecraft, entry.server, previewRadius(entry));
            }

            if (entry.externalPreview != null && entry.renderProperties == null && Boolean.getBoolean("seedqueue.renderLog")
               && READY_LOG_COUNTER.getAndIncrement() < 40) {
               LOGGER.info("[seedqueue] built external preview for #{}", entry.number);
            }

            if (entry.externalPreview != null && entry.renderProperties == null) {
               entry.renderProperties = new me.voidxwalker.worldpreview.WorldPreviewProperties(
                  entry.externalPreview.level,
                  entry.externalPreview.listener,
                  entry.externalPreview.gameMode,
                  entry.externalPreview.player,
                  entry.externalPreview.spawn
               );
            }

            if (entry.renderProperties != null && entry.renderTarget == null) {
               entry.renderTarget = me.voidxwalker.worldpreview.WorldPreview.createTarget(
                  minecraft, "seedqueue instance " + entry.number,
                  minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()
               );
            }

            if (Boolean.getBoolean("seedqueue.renderLog") && entry.renderTarget != null) {
               LOGGER.info("[seedqueue] invoking renderInstance for #{}", entry.number);
            }

            if (entry.renderTarget != null) {
               // The card samples a second target: a texture that was a render attachment earlier in
               // the same frame cannot be sampled (framebuffer feedback), so publish a copy.
               if (entry.displayTarget == null) {
                  entry.displayTarget = me.voidxwalker.worldpreview.WorldPreview.createTarget(
                     minecraft, "seedqueue display " + entry.number,
                     minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()
                  );
               }

               boolean painted = Boolean.getBoolean("seedqueue.paintTest");

               if (painted) {
                  WallPreview.paint(entry.renderTarget, 0.0F, 1.0F, 0.0F, 1.0F);
                  WallPreview.snapshotNow("pre" + entry.number, entry.renderTarget);
               }

               boolean rendered = false;

               if (Boolean.getBoolean("seedqueue.previewDebug")) {
                  var cam = minecraft.gameRenderer.mainCamera().position();
                  var area = minecraft.levelRenderer.viewArea();
                  LOGGER.info(
                     "[seedqueue] pre-render: level={} camera=({},{},{}) viewArea={} visible={} sodium={}",
                     minecraft.level == (entry.externalPreview == null ? null : entry.externalPreview.level),
                     String.format("%.1f", cam.x),
                     String.format("%.1f", cam.y),
                     String.format("%.1f", cam.z),
                     area == null ? -1 : area.size(),
                     minecraft.levelRenderer.visibleSections().size(),
                     me.voidxwalker.worldpreview.SodiumCompat.stats()
                  );
               }

               if (!Boolean.getBoolean("worldpreview.instanceMainTarget")) {
                  WallPreview.begin(entry.renderTarget);
               }

               try {
                  rendered = me.voidxwalker.worldpreview.WorldPreview.renderInstance(
                     minecraft, entry.renderProperties, entry.renderTarget
                  );
               } finally {
                  WallPreview.end();
               }

               if (painted) {
                  WallPreview.snapshotNow("post" + entry.number, entry.renderTarget);
               }

               if (Boolean.getBoolean("worldpreview.instanceMainTarget")) {
                  WallPreview.snapshotNow("main", minecraft.gameRenderer.mainRenderTarget());
               }

               if (Boolean.getBoolean("seedqueue.previewDebug")) {
                  var cam = minecraft.gameRenderer.mainCamera().position();
                  LOGGER.info(
                     "[seedqueue] post-render: camera=({},{},{}) visible={} sodium={}",
                     String.format("%.1f", cam.x),
                     String.format("%.1f", cam.y),
                     String.format("%.1f", cam.z),
                     minecraft.levelRenderer.visibleSections().size(),
                     me.voidxwalker.worldpreview.SodiumCompat.stats()
                  );
               }

               if (rendered) {
                  entry.lastRenderedAt = System.currentTimeMillis();
                  renderedFrames++;
                  budget--;

                  if (entry.displayTarget != null) {
                     WallPreview.publish(entry.renderTarget, entry.displayTarget);
                  }

                  if (Boolean.getBoolean("seedqueue.renderLog") && SUCCESS_LOG_COUNTER.getAndIncrement() < 10) {
                     LOGGER.info("[seedqueue] rendered instance #{} (frame {})", entry.number, renderedFrames);
                  }

                  if (Boolean.getBoolean("seedqueue.dumpTargets") && renderedFrames < 6) {
                     WallPreview.dump("render" + entry.number, entry.renderTarget);
                     WallPreview.dump("display" + entry.number, entry.displayTarget);
                  }
               }
            }
         } catch (Throwable t) {
            LOGGER.warn("[seedqueue] failed to render instance #{}", entry.number, t);
            budget--;
         }
      }
   }
   /** Renders the hovered instance live behind the wall (WorldPreview style, no screenshots). */
   private static void livePreviewTick(Minecraft minecraft, QueueEntry hovered) {
      if (hovered == null && Boolean.getBoolean("seedqueue.liveSmoke")) {
         for (QueueEntry candidate : ENTRIES) {
            if (candidate.state == QueueEntry.State.READY) {
               hovered = candidate;
               break;
            }
         }
      }
      if (!(minecraft.gui.screen() instanceof WallScreen)) {
         if (liveEntry != null) {
            me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
            liveEntry.previewActive = false;
            liveEntry = null;
         }

         return;
      }

      if (hovered == null || hovered.state != QueueEntry.State.READY || hovered.server == null) {
         return;
      }

      if (hovered == liveEntry && hovered.previewActive) {
         if (Boolean.getBoolean("seedqueue.offscreenTest") && ++offscreenTicks > 20 && !offscreenDone) {
            offscreenDone = true;
            renderOffscreen(minecraft);
         }

         if (Boolean.getBoolean("seedqueue.wallShot") && ++wallShotTicks == 60 && !wallShotDone) {
            wallShotDone = true;
            net.minecraft.client.Screenshot.grab(minecraft, false);
            LOGGER.info("[seedqueue] captured live wall screenshot");
         }

         return;
      }

      if (liveEntry != null) {
         me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
         liveEntry.previewActive = false;
      }

      if (hovered.externalPreview == null) {
         hovered.externalPreview = me.voidxwalker.worldpreview.ExternalPreview.build(minecraft, hovered.server, previewRadius(hovered));
      }

      if (hovered.externalPreview != null) {
         me.voidxwalker.worldpreview.WorldPreview.activateExternal(
            new me.voidxwalker.worldpreview.WorldPreviewProperties(
               hovered.externalPreview.level,
               hovered.externalPreview.listener,
               hovered.externalPreview.gameMode,
               hovered.externalPreview.player,
               hovered.externalPreview.spawn
            )
         );
         hovered.previewActive = true;
         liveEntry = hovered;
         LOGGER.info("[seedqueue] live preview for entry #{}", hovered.number);
      }
   }

   /** Called at the end of a frame: grabs the preview window image and keeps the wall hidden. */
   private static int wallDumpTicks;

   /** Dumps the finished frame (GUI included) and the card texture for visual verification. */
   private static void dumpWallFrame(Minecraft minecraft) {
      if (!Boolean.getBoolean("seedqueue.wallDump") || !(minecraft.gui.screen() instanceof WallScreen)) {
         return;
      }

      if (++wallDumpTicks % 40 != 0) {
         return;
      }

      WallPreview.writeFrame("wall", minecraft.gameRenderer.mainRenderTarget());

      for (QueueEntry entry : ENTRIES) {
         if (entry.displayTarget != null) {
            WallPreview.writeFrame("card" + entry.number, entry.displayTarget);
            break;
         }
      }
   }

   public static void afterRenderFrame(Minecraft minecraft) {
      // Vanilla may swap in a "Saving world" screen while the integrated server shuts down after the
      // F6 return to the wall; re-assert the wall once the frame has been rendered as well, so the
      // saving screen never stays visible.


      if (PreviewCapture.active && --PreviewCapture.hold <= 0) {
         PreviewCapture.active = false;
         me.voidxwalker.worldpreview.WorldPreview.instanceRendering = false;
      }

      if (pendingGrab) {
         pendingGrab = false;
         grabStartedAt = System.currentTimeMillis();
         net.minecraft.client.Screenshot.grab(minecraft, false);
         LOGGER.info("[seedqueue] grabbed preview frame for #{}", previewGrabEntry == null ? -1 : previewGrabEntry.number);
      }

      if (frameCaptureActive && frameCaptureTicks >= 40) {
         me.voidxwalker.worldpreview.WorldPreview.instanceRendering = false;
         me.voidxwalker.worldpreview.WorldPreview.forceLevelRender = false;
         me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
         frameCaptureCleanupTicks = 20;
         frameCaptureActive = false;
         frameCaptureWaiting = true;
         LOGGER.info("[seedqueue] frame capture finished for #{}", frameCaptureEntry == null ? -1 : frameCaptureEntry.number);
         return;
      }

      if (frameTestActive) {
         me.voidxwalker.worldpreview.WorldPreview.setTargetOverride(null);
         me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
         frameTestActive = false;
         frameTestDumpPending = true;
         LOGGER.info("[seedqueue] frame override finished, dump pending");
         return;
      }

      if (!offscreenPending) {
         return;
      }

      offscreenPending = false;
      QueueEntry entry = PreviewCapture.entry;
      me.voidxwalker.worldpreview.WorldPreview.captureTargetImage(image -> {
         if (entry != null) {
            entry.nativePreview = image;
         }
      });
      me.voidxwalker.worldpreview.WorldPreview.restoreMainTarget(minecraft);
   }
   public static void restoreAfterCapture(Minecraft minecraft) {
      PreviewCapture.restore(minecraft);
   }

   private static boolean wallEnabled() {
      return WALL_PROPERTY || QueueConfig.useWall;
   }

   /** Integration is on by default when autoQueue is enabled; a Java property can override it. */
   private static boolean integrate() {
      String property = System.getProperty("seedqueue.integrate");
      return property != null ? Boolean.parseBoolean(property) : QueueConfig.autoQueue;
   }

   private static int queueSize() {
      return Integer.getInteger("seedqueue.queueSize", QueueConfig.queueSize);
   }

   private static boolean titleShotDone;
   private static int titleTicks;

   /** 1.21.4 "schedule join": the entry joins automatically as soon as it is ready. */
   public static void scheduleAll() {
      for (QueueEntry entry : ENTRIES) {
         if (entry.locked) {
            entry.scheduledJoin = true;
         }
      }

      LOGGER.info("[seedqueue] scheduled join for all locked entries");
   }

   public static void cancelScheduledJoin() {
      for (QueueEntry entry : ENTRIES) {
         entry.scheduledJoin = false;
      }
   }

   /** Runs while the wall is open: auto-join scheduled worlds and drive the benchmark mode. */
   private static void wallScheduledTick(Minecraft minecraft) {
      if (PreviewState.handoffPreviewEntry != null) {
         return;
      }

      for (QueueEntry entry : new ArrayList<>(ENTRIES)) {
         if (entry.scheduledJoin && entry.state == QueueEntry.State.READY && !entry.transferred) {
            LOGGER.info("[seedqueue] scheduled join for #{}", entry.number);

            if (playEntry(entry)) {
               minecraft.gui.setScreen(null);
            }

            return;
         }
      }

      if (benchmarking) {
         for (QueueEntry entry : new ArrayList<>(ENTRIES)) {
            if (entry.state == QueueEntry.State.READY && !entry.locked) {
               ENTRIES.remove(entry);

               // Deliberately no PreviewState.unbind() here: unbinding during a benchmark removed the
               // only thing rendering behind the overlay (black screen). The next frame simply binds
               // another entry.
               if (PreviewState.frameSwapEntry == entry) {
                  PreviewState.frameSwapEntry = null;
               }

               discardAsync(entry);
               if (benchmarkCount == 0) {
                  benchmarkStarted = System.currentTimeMillis();
               }
               benchmarkCount++;
               // The benchmark consumes READY entries and refills the queue; that loop normally runs from the
      // wall update, which stops as soon as the benchmark overlay closes the wall. Drive it from the
      // tick instead so a benchmark keeps making progress outside the wall screen.
      if (benchmarking && !(minecraft.gui.screen() instanceof WallScreen)) {
         wallScheduledTick(minecraft);
      }


      if (--refillCooldown <= 0) {
         refillCooldown = Math.max(1, Integer.getInteger("seedqueue.refillIntervalTicks", 10));
         refillQueue(minecraft);
      }

               if (benchmarkCount >= Integer.getInteger("seedqueue.benchmarkResets", QueueConfig.benchmarkResets)) {
                  benchmarking = false;
                  benchmarkFinishedAt = System.currentTimeMillis();
                  benchmarkResultResets = benchmarkCount;
                  benchmarkResultMs = Math.max(1L, benchmarkFinishedAt - benchmarkStarted);
                  playSound("finish_benchmark");
                  LOGGER.info(
                     "[seedqueue] SEEDQUEUE_BENCHMARK {} resets in {} ms",
                     benchmarkCount,
                     System.currentTimeMillis() - benchmarkStarted
                  );
               }

               break;
            }
         }
      }
   }

   /** Plays a SeedQueue sound (assets/seedqueue/sounds.json), scaled by the soundVolume option. */
   public static void playSound(String name) {
      if (QueueConfig.soundVolume <= 0) {
         return;
      }

      try {
         var event = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("seedqueue", name)
         );
         Minecraft.getInstance()
            .getSoundManager()
            .play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(event, 1.0F, QueueConfig.soundVolume / 100.0F));
      } catch (Throwable t) {
         LOGGER.debug("[seedqueue] failed to play sound {}", name, t);
      }
   }

   private static boolean joinedFromWall;

   /**
    * F6 while playing in a world: cancel Atum's reset and go straight back to the wall.
    *
    * <p>Only used when the player is actually in a world (any world, not just one joined from the
    * wall); in every other situation F6 is left to Atum so it starts a new world as usual. The wall
    * is put on screen immediately and re-asserted for a few ticks so the vanilla "Saving world"
    * screen never shows.</p>
    *
    * @return true when the key press was handled
    */
   /** Set while a wall return is waiting for the client to finish leaving the world. */
   private static boolean pendingWallReturn;
   private static int wallReturnTicks;

   public static boolean returnToWallFromJoinedWorld(Minecraft minecraft) {
      // "Playing in a world" means: this session joined a queued world from the wall and has not
      // left it yet. Everything else (title screen, wall, loading, preview levels bound to
      // Minecraft.level by the wall) must leave F6 completely alone.
      if (!joinedFromWall
         || minecraft.level == null
         || minecraft.player == null
         || minecraft.gui.screen() instanceof WallScreen
         || minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) {
         return false;
      }

      joinedFromWall = false;

      try {
         Class<?> atum = Class.forName("me.voidxwalker.autoreset.Atum");
         atum.getField("shouldReset").setBoolean(null, false);
      } catch (Throwable ignored) {
      }

      LOGGER.info("[seedqueue] returning to the wall from a joined world");
      me.voidxwalker.worldpreview.WorldPreview.releasePreviewCameraAndEntities(minecraft);
      TimerCompat.stop();
      stopPreviewCycle(minecraft);
      // The cached preview section graphs were built before this world was bound to the client; they
      // no longer match the chunks, which showed up as cards with missing terrain after F6.
      evictPreviewCaches("returned to the wall");
      // Entries that were just joined no longer hold an externalPreview, so the loop above can miss
      // their manager. Clear everything that is not the world we are leaving: those graphs belong to
      // a client state that no longer exists and render as holes / flickering sections.
      SodiumLevelCache.evictAllExcept(minecraft.level);

      for (QueueEntry entry : ENTRIES) {
         if (entry.externalPreview != null) {
            entry.externalPreview.invalidateSections();
         }
      }

      wallReturnScreen = null;
      wallShown = true;
      wallTicks = 0;

      // Hand the wall itself to disconnect(): passing a TitleScreen let vanilla put the title back
      // over the wall afterwards, which is why F6 from the F3+Esc pause ended up at the title screen.
      WallScreen wallScreen = new WallScreen(() -> ENTRIES);

      try {
         minecraft.disconnect(wallScreen, false);
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] disconnect while returning to the wall failed", t);

         try {
            minecraft.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
         } catch (Throwable inner) {
            LOGGER.warn("[seedqueue] direct disconnect failed", inner);
         }
      }

      // Set it immediately (the normal pause return relies on this), and keep a pending flag so the
      // tick can put the wall back if vanilla's disconnect completion replaced it.
      minecraft.setScreenAndShow(wallScreen);
      pendingWallReturn = true;
      wallReturnTicks = 0;
      return true;
   }



   /**
    * Called right before vanilla creates a world on its own (no queued world was consumed).
    *
    * <p>The wall binds preview levels to {@code Minecraft.level}; if that keeps running while the
    * client loads a new world, Sodium's section manager is destroyed and recreated with the preview
    * level over and over, the vanilla "Preparing world generation" screen never gets dismissed and the
    * game looks frozen. Drop the preview, close the wall and let vanilla have the client.</p>
    */
   public static void prepareForVanillaWorldCreation(Minecraft minecraft) {
      LOGGER.info("[seedqueue] vanilla world creation requested: releasing the wall");
      joinInProgress = false;
      joinedFromWall = false;
      wallShown = false;

      // Leaving the wall frees every preview level's Sodium managers: their arenas stay referenced
      // otherwise and a later 256 MiB shared-arena allocation fails with GpuOutOfMemoryException.
      try {
         SodiumLevelCache.evictAllExcept(net.minecraft.client.Minecraft.getInstance().level);
         SodiumLevelCache.sweep();
      } catch (Throwable ignored) {
      }
      PreviewState.unbind();

      try {
         me.voidxwalker.worldpreview.WorldPreview.keepSodiumBound = false;
         stopPreviewCycle(minecraft);
         me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
         me.voidxwalker.worldpreview.WorldPreview.releasePreviewCameraAndEntities(minecraft);
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] failed to release the wall for vanilla world creation", t);
      }

      if (minecraft.gui.screen() instanceof WallScreen
         || minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) {
         minecraft.gui.setScreen(null);
      }
   }

   /** Opens the wall. Returns true when it was actually opened (false when it was already up). */
   public static boolean openWall(Minecraft minecraft) {

      // Cards must always be first person: the third-person-back camera sits behind the player and
      // looks at world that was never generated, leaving a black band. The preview renders through the
      // global camera type, so it has to be forced while the wall owns the screen.
      if (wallSavedCameraType == null) {
         wallSavedCameraType = minecraft.options.getCameraType();
      }

      minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);

      if (minecraft.gui.screen() instanceof WallScreen) {
         return false;
      }

      playSound("open_wall");

      // Diagnostic: record who opened the wall (the "wall opened from key" line was ambiguous and we
      // need the caller to fix the stuck "Preparing world generation" report).
      if (Boolean.getBoolean("seedqueue.openWallTrace")) {
         StringBuilder trace = new StringBuilder();

         for (StackTraceElement element : new Throwable().getStackTrace()) {
            trace.append(" <- ").append(element.getClassName()).append('.').append(element.getMethodName());
         }

         LOGGER.info("[seedqueue] openWall called{}", trace);
      }

      if (QueueConfig.bypassWall || Boolean.getBoolean("seedqueue.bypassWall")) {
         QueueEntry locked = null;

         for (QueueEntry entry : ENTRIES) {
            if (entry.locked && entry.state == QueueEntry.State.READY && !entry.transferred) {
               locked = entry;
               break;
            }
         }

         if (locked != null && playEntry(locked)) {
            LOGGER.info("[seedqueue] bypassed the wall, joined locked entry #{}", locked.number);
            return false;
         }

         for (QueueEntry entry : ENTRIES) {
            if (entry.locked) {
               entry.scheduledJoin = true;
            }
         }
      }

      wallReturnScreen = minecraft.gui.screen();
      wallOpenedInWorld = minecraft.level != null;
      wallShown = true;
      wallTicks = 0;
      minecraft.setScreenAndShow(new WallScreen(() -> ENTRIES));
      LOGGER.info("[seedqueue] wall opened from key with {} entries", ENTRIES.size());
      return true;
   }

   /** Called when the wall is closed: restores the game state and the screen it was opened from. */
   /** Camera type stored when the wall was opened, restored when it closes. */
   private static net.minecraft.client.CameraType wallSavedCameraType;

   public static void onWallClosed(Minecraft minecraft) {
      if (wallSavedCameraType != null) {
         minecraft.options.setCameraType(wallSavedCameraType);
         wallSavedCameraType = null;
      }

      // A benchmark closes the wall to show its own overlay; closing the wall must not cancel the run
      // (it used to: "benchmark cancelled after 4 resets" happened right after onWallClosed).
      if (benchmarking) {
         LOGGER.info("[seedqueue] onWallClosed ignored while benchmarking");
         return;
      }

      LOGGER.info("[seedqueue] onWallClosed");
      joinInProgress = false;
      wallShown = false;

      // Leaving the wall frees every preview level's Sodium managers: their arenas stay referenced
      // otherwise and a later 256 MiB shared-arena allocation fails with GpuOutOfMemoryException.
      try {
         SodiumLevelCache.evictAllExcept(net.minecraft.client.Minecraft.getInstance().level);
         SodiumLevelCache.sweep();
      } catch (Throwable ignored) {
      }
      cancelScheduledJoin();
      benchmarking = false;
      benchmarkFinishedAt = 0;
      stopPreviewCycle(minecraft);
      me.voidxwalker.worldpreview.WorldPreview.hideDebugOverlay = false;
      // Leaving the wall drops the queued worlds: their preview levels and Sodium arenas are the
      // biggest resident allocations, and reusing them after a join produced the unrendered cards.
      int dropped = resetAllUnlocked();
      SodiumLevelCache.evictAllExcept(minecraft.level);

      if (dropped > 0) {
         LOGGER.info("[seedqueue] wall closed: discarded {} previewed worlds", dropped);
      }
      net.minecraft.client.gui.screens.Screen back = wallReturnScreen;
      wallReturnScreen = null;

      if (back instanceof TitleScreen) {
         minecraft.setScreenAndShow(new TitleScreen());
      } else {
         minecraft.gui.setScreen(back);
      }
   }

   private static int blankTicks;
   private static boolean blankShot;

   // ---------------------------------------------------------------- screenshot preview pass
   // Opens a freshly generated instance once, screenshots the real game screen at its spawn and
   // returns to the wall with that image on the card.

   private static QueueEntry shotEntry;
   private static net.minecraft.client.gui.screens.Screen shotWall;
   private static int shotPhase;
   private static int shotWait;
   private static long shotStartedAt;
   private static long shotGrabAt;
   private static boolean shotHudHidden;
   private static boolean shotPauseOptionSaved;
   private static net.minecraft.world.entity.player.ChatVisiblity shotChatSaved;
   private static net.minecraft.client.CameraType shotCameraSaved;
   private static boolean shotPauseOptionValue;

   private static boolean shotPreviewEnabled() {
      return Boolean.parseBoolean(System.getProperty("seedqueue.shotPreview", "false"));
   }

   private static QueueEntry nextEntryNeedingShot() {
      for (QueueEntry entry : ENTRIES) {
         if (entry.state == QueueEntry.State.READY && entry.previewTexture == null && !entry.previewActive
            && !entry.shotCaptured) {
            return entry;
         }
      }

      return null;
   }

   private static void tickScreenshotPreview(Minecraft minecraft) {
      if (!shotPreviewEnabled()) {
         return;
      }

      if (shotPhase == 0) {
         if (!(minecraft.gui.screen() instanceof WallScreen)) {
            return;
         }

         QueueEntry entry = nextEntryNeedingShot();

         if (entry == null) {
            return;
         }

         shotEntry = entry;
         shotWall = minecraft.gui.screen();
         shotPhase = 1;
         shotWait = 40;
         shotStartedAt = System.currentTimeMillis();
         me.voidxwalker.worldpreview.WorldPreview.hideDebugOverlay = true;
         shotPauseOptionValue = minecraft.options.pauseOnLostFocus;
         shotPauseOptionSaved = true;
         minecraft.options.pauseOnLostFocus = false;
         shotCameraSaved = minecraft.options.getCameraType();
         minecraft.options.setCameraType(
            false
               ? net.minecraft.client.CameraType.THIRD_PERSON_BACK
               : net.minecraft.client.CameraType.FIRST_PERSON
         );

         shotChatSaved = minecraft.options.chatVisibility().get();
         minecraft.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
         LOGGER.info("[seedqueue] shot preview: releasing #{}", entry.number);

         if (!entry.releaseForScreenshot()) {
            entry.shotCaptured = true;
            shotEntry = null;
            shotWall = null;
            shotPhase = 0;
         }

         return;
      }

      if (System.currentTimeMillis() - shotStartedAt > 30000L) {
         LOGGER.warn("[seedqueue] shot preview: timed out in phase {}", shotPhase);
         finishShotPass(minecraft);
         return;
      }

      switch (shotPhase) {
         case 1 -> {
            // Give the background server a moment to save and release the world folder.
            if (shotWait > 0) {
               shotWait--;
            } else {
               QueueEntry entry = shotEntry;
               LOGGER.info("[seedqueue] shot preview: opening #{}", entry.number);

               try {
                  minecraft.createWorldOpenFlows().openWorld(entry.folder, () -> { });
                  shotPhase = 2;
                  shotStartedAt = System.currentTimeMillis();
               } catch (Throwable t) {
                  LOGGER.warn("[seedqueue] shot preview: open failed", t);
                  finishShotPass(minecraft);
               }
            }
         }
         case 2 -> {
            if (minecraft.level != null && minecraft.isGameLoadFinished()) {
               if (minecraft.gui.screen() != null) {
                  LOGGER.info("[seedqueue] shot preview: closing screen {}", minecraft.gui.screen());
                  minecraft.gui.setScreen(null);
               }

               if (!minecraft.gui.hud.isHidden()) {
                  shotHudHidden = true;
                  minecraft.gui.hud.toggle();
               }

               shotPhase = 3;
               shotWait = 30;
            }
         }
         case 3 -> {
            if (shotWait > 0) {
               shotWait--;

               if (shotWait == 3) {
                  if (minecraft.gui.screen() != null) {
                     LOGGER.info("[seedqueue] shot preview: closing screen {}", minecraft.gui.screen());
                     minecraft.gui.setScreen(null);
                  }

                  if (!minecraft.gui.hud.isHidden()) {
                     shotHudHidden = true;
                     minecraft.gui.hud.toggle();
                  }
               }

               if (shotWait == 0) {
                  shotGrabAt = System.currentTimeMillis();
                  net.minecraft.client.Screenshot.grab(minecraft, false);

                  if (shotHudHidden) {
                     shotHudHidden = false;
                     minecraft.gui.hud.toggle();
                  }

                  LOGGER.info("[seedqueue] shot preview: grabbed #{}", shotEntry == null ? -1 : shotEntry.number);
                  shotPhase = 4;
               }
            }
         }
         case 4 -> {
            java.io.File dir = new java.io.File(minecraft.gameDirectory, "screenshots");

            if (dir.isDirectory()) {
               for (java.io.File file : dir.listFiles()) {
                  if (!file.isFile() || !file.getName().endsWith(".png")) {
                     continue;
                  }

                  if (file.lastModified() >= shotGrabAt - 1000 && file.length() > 1000) {
                     try {
                        com.mojang.blaze3d.platform.NativeImage image =
                           com.mojang.blaze3d.platform.NativeImage.read(java.nio.file.Files.newInputStream(file.toPath()));
                        QueueEntry entry = shotEntry;

                        if (Boolean.getBoolean("seedqueue.dumpRaw")) {
                           try {
                              image.writeToFile(new java.io.File("seedqueue_raw.png"));
                           } catch (Throwable ignored) {
                           }
                        }

                        entry.nativePreview = makeThumbnail(image, 320, 138);
                        image.close();
                        entry.uploadNativePreview();
                        entry.lastPreviewAt = System.currentTimeMillis();
                        entry.shotCaptured = true;
                        java.nio.file.Files.deleteIfExists(file.toPath());
                        LOGGER.info("[seedqueue] shot preview: captured #{}", entry.number);
                     } catch (Throwable t) {
                        LOGGER.warn("[seedqueue] shot preview: upload failed", t);
                     }

                     finishShotPass(minecraft);
                     return;
                  }
               }
            }
         }
         default -> {
         }
      }
   }


   /** Scales and centre-crops a full game screenshot down to a card thumbnail. */
   private static com.mojang.blaze3d.platform.NativeImage makeThumbnail(
      com.mojang.blaze3d.platform.NativeImage source, int targetWidth, int targetHeight
   ) {
      int sourceWidth = source.getWidth();
      int sourceHeight = source.getHeight();
      // Fit the whole frame into the card (letterbox) instead of cropping it.
      double scale = Math.min((double)targetWidth / sourceWidth, (double)targetHeight / sourceHeight);
      int drawWidth = Math.max(1, (int)Math.round(sourceWidth * scale));
      int drawHeight = Math.max(1, (int)Math.round(sourceHeight * scale));
      int drawX = (targetWidth - drawWidth) / 2;
      int drawY = (targetHeight - drawHeight) / 2;
      com.mojang.blaze3d.platform.NativeImage thumbnail =
         new com.mojang.blaze3d.platform.NativeImage(targetWidth, targetHeight, false);

      for (int y = 0; y < targetHeight; y++) {
         for (int x = 0; x < targetWidth; x++) {
            if (x < drawX || x >= drawX + drawWidth || y < drawY || y >= drawY + drawHeight) {
               thumbnail.setPixel(x, y, 0xFF000000);
               continue;
            }

            int sourceX = (x - drawX) * sourceWidth / drawWidth;
            int sourceY = (y - drawY) * sourceHeight / drawHeight;
            int pixel = source.getPixel(
               Math.min(sourceWidth - 1, Math.max(0, sourceX)),
               Math.min(sourceHeight - 1, Math.max(0, sourceY))
            );
            thumbnail.setPixel(x, y, 0xFF000000 | pixel & 0xFFFFFF);
         }
      }

      return thumbnail;
   }

   /** Leaves the instance world, restores the wall and goes back to idle. */
   private static void finishShotPass(Minecraft minecraft) {
      QueueEntry entry = shotEntry;
      shotEntry = null;
      shotPhase = 4;
      shotWait = 0;
      me.voidxwalker.worldpreview.WorldPreview.hideDebugOverlay = false;

      if (shotPauseOptionSaved) {
         shotPauseOptionSaved = false;
         minecraft.options.pauseOnLostFocus = shotPauseOptionValue;
      }

      if (shotChatSaved != null) {
         minecraft.options.chatVisibility().set(shotChatSaved);
         shotChatSaved = null;
      }

      if (shotCameraSaved != null) {
         minecraft.options.setCameraType(shotCameraSaved);
         shotCameraSaved = null;
      }

      try {
         if (minecraft.level != null) {
            minecraft.disconnect(new TitleScreen(), false);
         }
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] shot preview: disconnect failed", t);
      }
   }

   private static void tickShotTeardown(Minecraft minecraft) {
      if (shotPhase != 4) {
         return;
      }

      if (minecraft.level == null) {
         net.minecraft.client.gui.screens.Screen wall = shotWall;
         shotWall = null;
         shotPhase = 0;
         shotWait = 0;

         if (wall != null) {
            minecraft.setScreenAndShow(new WallScreen(() -> ENTRIES));
            wallShown = true;
            wallTicks = 0;
            LOGGER.info("[seedqueue] shot preview: back on wall");
         }
      }
   }


   // ------------------------------------------------- generation preview + freeze

   private static boolean captureDuringGeneration() {
      return Boolean.parseBoolean(System.getProperty("seedqueue.captureDuringGeneration", "false"));
   }

   private static void captureGenerationPreview(Minecraft minecraft) {
      if (!captureDuringGeneration()) {
         return;
      }

      for (QueueEntry entry : ENTRIES) {
         if (entry.state != QueueEntry.State.GENERATING || entry.previewTexture != null || entry.externalPreview != null) {
            continue;
         }

         java.util.List<net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket> snapshot;

         synchronized (entry.chunkPackets) {
            if (entry.chunkPackets.size() < 21) {
               continue;
            }

            snapshot = new java.util.ArrayList<>(entry.chunkPackets);
         }

         try {
            entry.externalPreview = me.voidxwalker.worldpreview.ExternalPreview.fromPackets(
               minecraft, entry.server, snapshot, previewRadius(entry)
            );

            if (entry.externalPreview == null) {
               continue;
            }

            com.mojang.blaze3d.platform.NativeImage image = SpawnViewRenderer.render(
               entry.externalPreview.level,
               entry.externalPreview.spawn,
               0.0,
               0.0,
               false
            );
            entry.nativePreview = image;
            entry.uploadNativePreview();
            entry.shotCaptured = true;
            entry.lastPreviewAt = System.currentTimeMillis();
            LOGGER.info("[seedqueue] generation preview captured for #{} ({} chunks in snapshot)", entry.number, snapshot.size());
         } catch (Throwable t) {
            LOGGER.warn("[seedqueue] generation preview failed for #{}", entry.number, t);
         }

         break;
      }
   }
   static boolean wallPreviewEnabled() {
      return Boolean.parseBoolean(System.getProperty("seedqueue.wallPreview", "true"));
   }

   private static void freezeReadyEntries(Minecraft minecraft) {
      // With the 3D wall preview the background server must stay alive: it is what builds the
      // client-side snapshot the cards render.
      if (!Boolean.parseBoolean(System.getProperty("seedqueue.freeze", "true"))
         || freezeCaptureEnabled() || wallPreviewEnabled()) {
         return;
      }

      for (QueueEntry entry : ENTRIES) {
         if (entry.state != QueueEntry.State.READY || entry.frozen
            || System.currentTimeMillis() - entry.readyAtMillis < 800L) {
            continue;
         }

         if (entry.previewTexture == null && entry.server != null && !shotPreviewEnabled()) {
            try {
               java.util.List<net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket> snapshot;

               synchronized (entry.chunkPackets) {
                  snapshot = new java.util.ArrayList<>(entry.chunkPackets);
               }

               entry.externalPreview = snapshot.size() >= 9
                  ? me.voidxwalker.worldpreview.ExternalPreview.fromPackets(minecraft, entry.server, snapshot, 2)
                  : me.voidxwalker.worldpreview.ExternalPreview.build(minecraft, entry.server, 2);

               if (entry.externalPreview != null) {
                  com.mojang.blaze3d.platform.NativeImage image = SpawnViewRenderer.render(
                     entry.externalPreview.level,
                     entry.externalPreview.spawn,
                     0.0,
                     0.0,
                     false
                  );
                  entry.nativePreview = image;
                  entry.uploadNativePreview();
                  entry.shotCaptured = true;
                  LOGGER.info("[seedqueue] fallback preview captured for #{}", entry.number);
               }
            } catch (Throwable t) {
               LOGGER.warn("[seedqueue] fallback preview failed for #{}", entry.number, t);
            }
         }

         entry.frozen = true;
         entry.releaseForScreenshot();
      }
   }

   // ------------------------------------------------- freeze-time on-screen capture

   private static QueueEntry freezeEntry;
   private static int freezePhase;
   private static int freezeWait;
   private static long freezeGrabAt;
   private static net.minecraft.client.gui.screens.Screen freezeWall;
   private static boolean freezeHudHidden;

   private static boolean freezeCaptureEnabled() {
      return Boolean.parseBoolean(System.getProperty("seedqueue.freezeCapture", "false"));
   }

   private static void tickFreezeCapture(Minecraft minecraft) {
      if (Boolean.getBoolean("seedqueue.freezeDebug")) {
         LOGGER.info("[seedqueue] freezeDebug phase={} screen={} entries={} enabled={}", freezePhase, minecraft.gui.screen(), ENTRIES.size(), freezeCaptureEnabled());
      }

      if (!freezeCaptureEnabled()) {
         return;
      }

      if (freezePhase == 0) {
         if (!(minecraft.gui.screen() instanceof WallScreen)) {
            return;
         }

         QueueEntry entry = null;

         for (QueueEntry candidate : ENTRIES) {
            if (candidate.state == QueueEntry.State.READY && !candidate.frozen
               && candidate.previewTexture == null && candidate.server != null) {
               entry = candidate;
               break;
            }
         }

         if (entry == null) {
            return;
         }

         try {
            java.util.List<net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket> snapshot;

            synchronized (entry.chunkPackets) {
               snapshot = new java.util.ArrayList<>(entry.chunkPackets);
            }

            if (entry.externalPreview == null) {
               entry.externalPreview = snapshot.size() >= 9
                  ? me.voidxwalker.worldpreview.ExternalPreview.fromPackets(minecraft, entry.server, snapshot, 2)
                  : me.voidxwalker.worldpreview.ExternalPreview.build(minecraft, entry.server, 2);
            }

            if (entry.externalPreview == null) {
               return;
            }

            // Wall previews are always rendered first person: the third-person camera sat several blocks
      // behind the player and the extra frustum area was never generated, which left a black band at
      // the bottom of the cards. The player's own perspective no longer affects the wall.
      System.setProperty("worldpreview.thirdPerson", "false");
      // Simulated window size for previews (0 = use the real window).
      System.setProperty("worldpreview.simulatedWidth", String.valueOf(QueueConfig.simulatedWidth));
      System.setProperty("worldpreview.simulatedHeight", String.valueOf(QueueConfig.simulatedHeight));
            entry.renderProperties = new me.voidxwalker.worldpreview.WorldPreviewProperties(
               entry.externalPreview.level,
               entry.externalPreview.listener,
               entry.externalPreview.gameMode,
               entry.externalPreview.player,
               entry.externalPreview.spawn
            ).withCamera(entry.externalPreview.cameraAnchor);
            me.voidxwalker.worldpreview.WorldPreview.activateExternal(entry.renderProperties);
            me.voidxwalker.worldpreview.WorldPreview.forceLevelRender = true;
            me.voidxwalker.worldpreview.WorldPreview.hideDebugOverlay = true;
            freezeEntry = entry;
            freezeWall = minecraft.gui.screen();
            freezePhase = 1;
            freezeWait = 40;

            if (!minecraft.gui.hud.isHidden()) {
               freezeHudHidden = true;
               minecraft.gui.hud.toggle();
            }

            minecraft.gui.setScreen(null);
            LOGGER.info("[seedqueue] freeze capture: previewing #{} on screen", entry.number);
         } catch (Throwable t) {
            LOGGER.warn("[seedqueue] freeze capture: setup failed", t);
            finishFreezeCapture(minecraft, null);
         }

         return;
      }

      if (freezePhase == 1) {
         if (freezeWait > 0) {
            freezeWait--;
         } else {
            freezeGrabAt = System.currentTimeMillis();
            net.minecraft.client.Screenshot.grab(minecraft, false);
            LOGGER.info("[seedqueue] freeze capture: grabbed #{}", freezeEntry == null ? -1 : freezeEntry.number);
            freezePhase = 2;
         }

         return;
      }

      if (freezePhase == 2) {
         java.io.File dir = new java.io.File(minecraft.gameDirectory, "screenshots");

         if (dir.isDirectory()) {
            for (java.io.File file : dir.listFiles()) {
               if (!file.isFile() || !file.getName().endsWith(".png") || file.length() < 1000) {
                  continue;
               }

               if (file.lastModified() >= freezeGrabAt - 1000) {
                  int width = 0;
                  int height = 0;

                  try (com.mojang.blaze3d.platform.NativeImage image =
                        com.mojang.blaze3d.platform.NativeImage.read(java.nio.file.Files.newInputStream(file.toPath()))) {
                     width = image.getWidth();
                     height = image.getHeight();
                     com.mojang.blaze3d.platform.NativeImage copy =
                        new com.mojang.blaze3d.platform.NativeImage(width, height, false);

                     for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                           copy.setPixel(x, y, image.getPixel(x, y));
                        }
                     }

                     if (freezeEntry != null) {
                        freezeEntry.nativePreview = copy;
                        freezeEntry.captureWidth = width;
                        freezeEntry.captureHeight = height;
                        freezeEntry.uploadNativePreview();
                        freezeEntry.shotCaptured = true;
                        LOGGER.info("[seedqueue] freeze capture: card image {}x{} for #{}", width, height, freezeEntry.number);
                     }
                  } catch (Throwable t) {
                     LOGGER.warn("[seedqueue] freeze capture: reading screenshot failed", t);
                  }

                  try {
                     java.nio.file.Files.deleteIfExists(file.toPath());
                  } catch (Throwable ignored) {
                  }

                  finishFreezeCapture(minecraft, null);
                  return;
               }
            }
         }

         return;
      }
   }

   private static void finishFreezeCapture(Minecraft minecraft, QueueEntry unused) {
      QueueEntry entry = freezeEntry;
      freezeEntry = null;
      freezePhase = 0;
      freezeWait = 0;
      me.voidxwalker.worldpreview.WorldPreview.forceLevelRender = false;
      me.voidxwalker.worldpreview.WorldPreview.hideDebugOverlay = false;
      me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();

      if (freezeHudHidden) {
         freezeHudHidden = false;
         minecraft.gui.hud.toggle();
      }

      net.minecraft.client.gui.screens.Screen wall = freezeWall;
      freezeWall = null;

      if (wall != null) {
         minecraft.setScreenAndShow(new WallScreen(() -> ENTRIES));
         wallShown = true;
         wallTicks = 0;
      }

      if (entry != null) {
         entry.frozen = true;
         entry.releaseForScreenshot();
      }
   }

   /**
    * Keeps the preview world on screen while the real client level connects, then releases it.
    * This is what removes the vanilla loading screen from the join: the queued world is already
    * displayed, so the switch to the client level happens on identical pixels.
    */
   private static void handoffTick(Minecraft minecraft) {
      if (PreviewState.handoffPreviewEntry == null) {
         return;
      }

      handoffTicks++;

      if (Boolean.getBoolean("seedqueue.joinShot") && (handoffTicks == 5 || handoffTicks == 12)) {
         WallPreview.writeFrame("join" + handoffTicks, minecraft.gameRenderer.mainRenderTarget());
      }

      if (handoffTicks < 100 && minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) {
         minecraft.gui.setScreen(null);
      }

      var previewLevel = PreviewState.handoffPreviewEntry.externalPreview == null ? null : PreviewState.handoffPreviewEntry.externalPreview.level;
      Object previewPlayer = PreviewState.handoffPreviewEntry.externalPreview == null ? null : PreviewState.handoffPreviewEntry.externalPreview.player;

      if (PreviewState.handoffPreviewEntry.renderProperties != null && PreviewState.handoffPreviewEntry.renderProperties.player != null) {
         previewPlayer = PreviewState.handoffPreviewEntry.renderProperties.player;
      }

      // The seamless join reuses the preview level as the client level, so "level changed" never
      // becomes true for it. The client attaches its own player as soon as it connects; that is the
      // moment the preview atmosphere (forced sky colour, fog, daylight lightmap) has to stop,
      // otherwise the joined world renders with the preview sky and fog for a few seconds.
      boolean realPlayer = minecraft.player != null && minecraft.player != previewPlayer;
      boolean realLevel = minecraft.level != null && minecraft.player != null
         && (minecraft.level != previewLevel || realPlayer);

      if (realLevel || handoffTicks > 200) {
         // Give the player the camera back before dropping the preview: the preview binds the camera
         // to its own anchor entity, and a seamless join reuses the preview level.
         me.voidxwalker.worldpreview.WorldPreview.releasePreviewCameraAndEntities(minecraft);

         // The wall pins Sodium to the preview level ("keepSodiumBound") so cards keep their compiled
         // geometry. If that is not cleared here, Sodium keeps rendering the joined world through the
         // preview level's section manager: terrain is culled/drawn for the wrong level (distant
         // chunks look wrong) and the frame rate collapses.
         me.voidxwalker.worldpreview.WorldPreview.keepSodiumBound = false;

         if (me.voidxwalker.worldpreview.WorldPreview.isActive()) {
            me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
         }

         me.voidxwalker.worldpreview.WorldPreview.rebindRealLevel(minecraft);
      PreviewState.renderDistance = -1;

         if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) {
            minecraft.gui.setScreen(null);
         }

         alignPlayerToPreviewedSpawn(PreviewState.handoffPreviewEntry, minecraft);

         LOGGER.info("[seedqueue] hand-off complete after {} ticks (preview released)", handoffTicks);
         PreviewState.handoffPreviewEntry = null;
         joinInProgress = false;
         restoreRespawnRadius(minecraft);
      }
   }

   /**
    * Restores the vanilla respawn radius once the player is in the world.
    *
    * <p>Queue worlds pin {@code respawn_radius} to 0 so the preview can predict the join position
    * (with a larger radius vanilla picks a random spot around the spawn, which no preview can match).
    * Leaving it at 0 made every death respawn on the exact same block, so the radius is restored to the
    * vanilla default as soon as the hand-over finished.</p>
    */
   private static void restoreRespawnRadius(Minecraft minecraft) {
      try {
         var server = minecraft.getSingleplayerServer();

         if (server != null) {
            server.getGameRules().set(
               net.minecraft.world.level.gamerules.GameRules.RESPAWN_RADIUS, 10, server
            );
         }
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] failed to restore the respawn radius", t);
      }
   }

   /**
    * Puts the joining player exactly on the block the card previewed.
    *
    * <p>Vanilla places a joining player through {@code PlayerSpawnFinder}, which searches a wide area
    * around the world spawn and can land dozens of blocks away from it - no preview can predict that.
    * Teleporting the player onto the previewed spawn keeps "what the card showed" and "where the run
    * starts" identical.</p>
    */
   private static void alignPlayerToPreviewedSpawn(QueueEntry entry, Minecraft minecraft) {
      if (entry == null || entry.server == null || entry.externalPreview == null) {
         return;
      }

      var spawn = entry.externalPreview.spawn;

      if (spawn == null) {
         return;
      }

      try {
         var server = entry.server;

         server.execute(() -> {
            try {
               for (var player : server.getPlayerList().getPlayers()) {
                  var level = server.overworld();

                  if (player.level() != level) {
                     continue;
                  }

                  player.teleportTo(
                     level,
                     spawn.getX() + 0.5,
                     spawn.getY(),
                     spawn.getZ() + 0.5,
                     java.util.Collections.emptySet(),
                     player.getYRot(),
                     player.getXRot(),
                     false
                  );
                  LOGGER.info("[seedqueue] aligned player to previewed spawn {}", spawn);
               }
            } catch (Throwable t) {
               LOGGER.warn("[seedqueue] previewed spawn alignment failed", t);
            }
         });
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] previewed spawn alignment could not be scheduled", t);
      }
   }

   private static boolean handoffSmokeDone;


   private static int timerLogTicks = -1;
   /** Chunk radius the current previews were built with; a change invalidates their section graph. */
   private static int previewRadiusBuilt = -1;
   private static int timerFinishTicks = -1;
   private static boolean resizeSmokeDone;
   private static int keysSmokeStage;
   private static int keysSmokeStage2;
   private static boolean lockSmokeDone;
   private static boolean playNextLockSmokeDone;
   private static boolean wallScreenshotDone;
   private static int wallScreenshotTicks;
   private static boolean configSmokeDone;
   private static long wallDelayStartedAt;
   private static boolean configListSmokeDone;
   private static boolean keysSmokeDone;
   private static int configSmokeTicks;

   /** Logs heap usage when -Dseedqueue.memLog=true (used to estimate per-instance memory). */
   public static void logMemory(String where) {
      if (!Boolean.getBoolean("seedqueue.memLog")) {
         return;
      }

      // No System.gc() here: a forced full GC per queued instance was a visible stutter whenever
      // -Dseedqueue.memLog=true was enabled. The numbers are a little stale, which is fine for a log.
      try {
         Thread.sleep(20L);
      } catch (InterruptedException ignored) {
         Thread.currentThread().interrupt();
      }

      Runtime runtime = Runtime.getRuntime();
      long used = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
      long committed = runtime.totalMemory() / 1048576L;
      long max = runtime.maxMemory() / 1048576L;
      LOGGER.info("[seedqueue] memory {}: heapUsed={}MB committed={}MB max={}MB", where, used, committed, max);
   }

   /** Ticks for which the preview rotation is paused after the client switched level (respawn). */

   private static Object lastPauseLevel;

   private static java.lang.reflect.Field nextTickTimeField;
   private static boolean nextTickTimeResolved;

   /** Clears the tick deadline of a server that was just unparked, so it does not report being behind. */
   public static void resetTickTiming(Object server) {
      try {
         if (!nextTickTimeResolved) {
            nextTickTimeResolved = true;

            for (Class<?> type = server.getClass(); type != null; type = type.getSuperclass()) {
               try {
                  java.lang.reflect.Field field = type.getDeclaredField("nextTickTimeNanos");
                  field.setAccessible(true);
                  nextTickTimeField = field;
                  break;
               } catch (NoSuchFieldException ignored) {
               }
            }
         }

         if (nextTickTimeField != null) {
            nextTickTimeField.setLong(server, net.minecraft.util.Util.timeSource().getAsLong());
         }
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] could not reset the tick timing of an unparked server", t);
      }
   }

   public static void tick(Minecraft minecraft) {
      SodiumLevelCache.processPending();

      // Re-assert the wall after a return: vanilla's disconnect completion sets the title screen on a
      // later tick and used to swallow it (F6 from the F3+Esc pause ended on the title screen).
      if (pendingWallReturn && minecraft.level == null) {
         if (minecraft.gui.screen() instanceof WallScreen) {
            pendingWallReturn = false;
         } else if (++wallReturnTicks >= 1) {
            pendingWallReturn = false;
            wallShown = true;
            wallTicks = 0;
            LOGGER.info("[seedqueue] reopening the wall after the disconnect finished");
            minecraft.setScreenAndShow(new WallScreen(() -> ENTRIES));
         }
      }

      // Respawning replaces the client level. Rebuilding every preview while the client rebuilds the
      // world itself caused a multi-second frame drop, so pause the rotation for a moment.
      if (minecraft.level != lastPauseLevel) {
         lastPauseLevel = minecraft.level;

         // The player is teleported onto the previewed spawn here, before any preview state is
         // released: the previous join path never reached the hand-off alignment code, so the first
         // position after joining could differ from what the card showed.
         if (spawnAlignPos != null && minecraft.player != null) {
            try {
               // Teleport the *server* player: a ClientLevel cannot be a teleport target, and the client
               // position is synchronised from the server anyway.
               var server = minecraft.getSingleplayerServer();
               var serverPlayer = server == null ? null
                  : server.getPlayerList().getPlayer(minecraft.player.getUUID());

               if (serverPlayer != null) {
                  serverPlayer.teleportTo(
                     server.overworld(),
                     spawnAlignPos.getX() + 0.5,
                     spawnAlignPos.getY(),
                     spawnAlignPos.getZ() + 0.5,
                     java.util.Collections.emptySet(),
                     serverPlayer.getYRot(),
                     serverPlayer.getXRot(),
                     false
                  );
                  LOGGER.info("[seedqueue] aligned player to previewed spawn {}", spawnAlignPos);
               }
            } catch (Throwable t) {
               LOGGER.warn("[seedqueue] previewed spawn alignment failed", t);
            }

            spawnAlignPos = null;
            spawnAlignEntry = null;
         }

         // Leaving a world *to the wall* also changes the level; the wall must refill immediately, so the
         // pause only applies when the client is actually rebuilding a level it is going to render.
         if (!joinInProgress && !(minecraft.gui.screen() instanceof WallScreen)) {
            // Respawning (or changing dimension) replaces the client level: Sodium rebuilds its whole
            // section graph for the new level, so release the preview binding and free the preview
            // managers first instead of letting their arenas compete with that rebuild.
            try {
               if (PreviewState.frameSwapEntry != null) {
                  me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
                  PreviewState.unbind();
               }

               SodiumLevelCache.evictAllExcept(minecraft.level);
            SodiumLevelCache.sweep();
            } catch (Throwable t) {
               LOGGER.warn("[seedqueue] failed to release previews after a level change", t);
            }

            LOGGER.info("[seedqueue] level changed, previews released");
         }
      }

      // Whatever started the join (wall click, Atum gold boots, a queued reset), once the client owns
      // a level and a player the vanilla "Preparing world generation" screen has to go. It used to be
      // dismissed only on the wall join path, so the gold-boots join from the title screen sat on that
      // screen forever while the world was already rendering behind it.
      if (minecraft.level != null
         && minecraft.player != null
         && minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) {
         LOGGER.info("[seedqueue] dismissing the loading screen (world and player are ready)");
         minecraft.gui.setScreen(null);
      }

      // Cards are a copy of the main render target, so the vanilla F3 overlay ended up inside them.
      // While the wall owns the screen the overlay stays hidden; the wall draws its own debug panel.
      if (minecraft.gui.screen() instanceof WallScreen) {
         me.voidxwalker.worldpreview.WorldPreview.hideDebugOverlay = true;
      }

      // A preview's section graph is sized from the chunk radius it was built with. Changing the
      // radius at runtime left the old graph in place, so areas outside it stayed unrendered.
      if (previewRadiusBuilt != QueueConfig.chunkRadius) {
         if (previewRadiusBuilt != -1) {
            evictPreviewCaches("chunk radius " + previewRadiusBuilt + " -> " + QueueConfig.chunkRadius);
         }

         previewRadiusBuilt = QueueConfig.chunkRadius;
      }


      if (suppressLoadingScreens) {
         if (minecraft.level != null && minecraft.player != null) {
            // Level and player exist: the hand-over is complete. Closing the wall only now keeps the
            // title/loading screens from flashing in between.
            me.voidxwalker.worldpreview.WorldPreview.rebindRealLevel(minecraft);
      PreviewState.renderDistance = -1;
            alignPlayerToPreviewedSpawn(spawnAlignEntry, minecraft);
            spawnAlignEntry = null;
            suppressLoadingScreens = false;
            suppressTicks = 0;
            joinInProgress = false;

            // A preview armed in the same frame as the join would keep owning the client level and
            // camera; drop it before the wall goes away.
            stopPreviewCycle(minecraft);

            // Dismiss the wall *and* the vanilla "Preparing world generation" screen: the world is
            // already playable at this point, leaving it up locked the player out of the game.
            // Anything that still sits on top of a playable world has to go: the title screen used to be
            // left behind over the joined world, and a leftover BlankScreen swallowed the escape key.
            net.minecraft.client.gui.screens.Screen open = minecraft.gui.screen();

            if (open instanceof WallScreen
               || open instanceof net.minecraft.client.gui.screens.LevelLoadingScreen
               || open instanceof net.minecraft.client.gui.screens.TitleScreen
               || open instanceof net.minecraft.client.gui.screens.ProgressScreen
               || open instanceof BlankScreen) {
               minecraft.gui.setScreen(null);
            }
         } else if (++suppressTicks > 400) {
            // Safety net: if the hand-off never completes, do not leave the wall stuck forever.
            suppressTicks = 0;
            LOGGER.warn("[seedqueue] level hand-off timed out, restoring screens");
            suppressLoadingScreens = false;
            joinInProgress = false;

            if (minecraft.gui.screen() instanceof WallScreen || minecraft.gui.screen() instanceof BlankScreen) {
               minecraft.gui.setScreen(null);
            }
         }
      }

      // A joined world must never be left behind an open wall: the wall keeps swapping preview
      // levels into the client, which is what left the player "stuck in a preview world" after the
      // second join. When the join did not go through the wall (wallOpenedInWorld) this cannot fire.
      if (!wallOpenedInWorld
         && joinedFromWall
         && PreviewState.handoffPreviewEntry == null
         && minecraft.level != null
         && minecraft.player != null
         && minecraft.gui.screen() instanceof WallScreen) {
         LOGGER.info("[seedqueue] closing the wall: the client joined a world");
         joinInProgress = false;
         stopPreviewCycle(minecraft);
         me.voidxwalker.worldpreview.WorldPreview.releasePreviewCameraAndEntities(minecraft);
         me.voidxwalker.worldpreview.WorldPreview.rebindRealLevel(minecraft);
      PreviewState.renderDistance = -1;
         minecraft.gui.setScreen(null);
      }

      // A preview must never keep owning the client level/camera once the player is in a world.
      if (joinedFromWall
         && PreviewState.handoffPreviewEntry == null
         && minecraft.level != null
         && minecraft.player != null
         && !(minecraft.gui.screen() instanceof WallScreen)
         && me.voidxwalker.worldpreview.WorldPreview.isActive()) {
         LOGGER.info("[seedqueue] releasing a leftover preview in a joined world");
         stopPreviewCycle(minecraft);
         me.voidxwalker.worldpreview.WorldPreview.releasePreviewCameraAndEntities(minecraft);

         // The stuck preview may also have left Minecraft.level pointing at its own level while the
         // client already joined another world. The player entity always lives in the joined level.
         if (minecraft.player.level() instanceof net.minecraft.client.multiplayer.ClientLevel joined
            && joined != minecraft.level) {
            LOGGER.info("[seedqueue] restoring the joined client level");
            minecraft.setLevel(joined);
         }

         me.voidxwalker.worldpreview.WorldPreview.rebindRealLevel(minecraft);
      PreviewState.renderDistance = -1;
      }

      applyGenerationPermits(minecraft);

      if (Boolean.getBoolean("seedqueue.levelListLog") && !levelListLogged
         && lastPlayedFolder != null && minecraft.level != null) {
         levelListLogged = true;

         try {
            var source = net.minecraft.world.level.storage.LevelStorageSource.createDefault(
               net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("saves")
            );
            java.util.List<String> names = new java.util.ArrayList<>();
            source.findLevelCandidates().levels().forEach(directory -> names.add(directory.directoryName()));
            LOGGER.info(
               "[seedqueue] level list ({}): {} containsPlayed={}",
               names.size(),
               names,
               names.contains(lastPlayedFolder)
            );
         } catch (Throwable t) {
            LOGGER.warn("[seedqueue] level list check failed", t);
         }
      }

      if (Boolean.getBoolean("seedqueue.wallKeysSmoke") && minecraft.gui.screen() instanceof WallScreen wall) {
         int stage = switch (wallOpenTicks) {
            case 60 -> 1;
            case 80 -> 2;
            case 120 -> 3;
            case 240 -> 4;
            default -> -1;
         };

         if (stage != -1 && keysSmokeStage < stage) {
            keysSmokeStage = stage;
            int key = switch (stage) {
               case 1, 2 -> com.mojang.blaze3d.platform.InputConstants.KEY_L;
               case 3 -> com.mojang.blaze3d.platform.InputConstants.KEY_E;
               default -> com.mojang.blaze3d.platform.InputConstants.KEY_R;
            };
            LOGGER.info("[seedqueue] keys smoke: stage {} pressing {} at wall tick {}", stage, key, wallOpenTicks);
            wall.debugPressKey(key);
         }
      }

      if (Boolean.getBoolean("seedqueue.keysSmoke") && !keysSmokeDone
         && minecraft.gui.screen() instanceof TitleScreen && minecraft.level == null) {
         keysSmokeDone = true;
         configSmokeDone = true;
         minecraft.gui.setScreen(new WallKeybindingsScreen(new TitleScreen()));
         configSmokeTicks = 0;
         LOGGER.info("[seedqueue] opened keybindings screen");
      }

      if (Boolean.getBoolean("seedqueue.configListSmoke") && !configListSmokeDone
         && minecraft.gui.screen() instanceof TitleScreen && minecraft.level == null) {
         configListSmokeDone = true;

         try {
            LOGGER.info("[seedqueue] opening SpeedrunAPI mod configs list");
            minecraft.gui.setScreen(
               new me.contaria.speedrunapi.config.screen.SpeedrunModConfigsScreen(new TitleScreen())
            );
            configSmokeTicks = 0;
         } catch (Throwable t) {
            LOGGER.warn("[seedqueue] config list smoke failed", t);
         }
      }

      if (Boolean.getBoolean("seedqueue.configSmoke") && !configSmokeDone
         && minecraft.gui.screen() instanceof TitleScreen && minecraft.level == null) {
         configSmokeDone = true;

         try {
            LOGGER.info("[seedqueue] opening SpeedrunAPI config screen");
            minecraft.gui.setScreen(
               me.contaria.speedrunapi.config.SpeedrunConfigAPI.createDefaultModConfigScreen(
                  System.getProperty("seedqueue.configSmokeMod", "seedqueue"), null, new TitleScreen()
               )
            );
            configSmokeTicks = 0;
         } catch (Throwable t) {
            LOGGER.warn("[seedqueue] config smoke failed", t);
         }
      }

      if ((configSmokeDone || configListSmokeDone) && minecraft.gui.screen() != null && configSmokeTicks >= 0) {
         if (++configSmokeTicks == 80) {
            net.minecraft.client.Screenshot.grab(minecraft, false);
            LOGGER.info("[seedqueue] captured config screen screenshot");
            configSmokeTicks = -1;
         }
      }

      if (Boolean.getBoolean("seedqueue.lockSmoke") && !lockSmokeDone
         && minecraft.gui.screen() instanceof WallScreen wall && wallOpenTicks > 60) {
         lockSmokeDone = true;
         LOGGER.info("[seedqueue] lock smoke: right-clicking first card");
         wall.debugRightClickFirst();
      }

      if (Boolean.getBoolean("seedqueue.wallKeysSmoke2") && minecraft.gui.screen() instanceof WallScreen wall) {
         int stage = switch (wallOpenTicks) {
            case 40 -> 1;
            case 80 -> 2;
            case 130 -> 3;
            case 180 -> 4;
            default -> -1;
         };

         if (stage != -1 && keysSmokeStage2 < stage) {
            keysSmokeStage2 = stage;
            int key = switch (stage) {
               case 1 -> com.mojang.blaze3d.platform.InputConstants.KEY_G;
               case 2 -> com.mojang.blaze3d.platform.InputConstants.KEY_B;
               case 3 -> com.mojang.blaze3d.platform.InputConstants.KEY_N;
               default -> com.mojang.blaze3d.platform.InputConstants.KEY_K;
            };
            LOGGER.info("[seedqueue] keys smoke2: stage {} pressing {} at wall tick {}", stage, key, wallTicks);
            wall.debugPressKey(key);
         }
      }

      if (Boolean.getBoolean("seedqueue.resizeSmoke") && !resizeSmokeDone
         && minecraft.gui.screen() instanceof WallScreen && wallOpenTicks > 120) {
         resizeSmokeDone = true;
         LOGGER.info("[seedqueue] resize smoke: setWindowed(1280, 720)");

         try {
            minecraft.getWindow().setWindowed(1280, 720);
         } catch (Throwable t) {
            LOGGER.warn("[seedqueue] resize smoke failed", t);
         }
      }

      // frameSwapTick runs per frame from WorldPreview.frameTickHook (one preview render per frame).
      handoffTick(minecraft);
      dumpWallFrame(minecraft);

      if (timerLogTicks >= 0) {
         if (++timerLogTicks == 60) {
            TimerCompat.report("60 ticks after join");
            timerLogTicks = -1;
         }
      }

      // The level swap is over: give SpeedRunIGT its normal world-change / disconnect handling back.
      if (timerFinishTicks >= 0 && ++timerFinishTicks == 10) {
         timerFinishTicks = -1;
         TimerCompat.finish();
      }

      if (Boolean.getBoolean("seedqueue.handoffSmoke") && !handoffSmokeDone && minecraft.level == null) {
         for (QueueEntry candidate : new java.util.ArrayList<>(ENTRIES)) {
            if (candidate.state == QueueEntry.State.READY) {
               handoffSmokeDone = true;
               playEntry(candidate);
               break;
            }
         }
      }
      tickShotTeardown(minecraft);
      tickScreenshotPreview(minecraft);
      tickFreezeCapture(minecraft);
      captureGenerationPreview(minecraft);
      freezeReadyEntries(minecraft);
      int grabEvery = Integer.getInteger("seedqueue.wallGrabEvery", 0);
      if (grabEvery > 0 && minecraft.gui.screen() instanceof WallScreen
         && wallOpenTicks > 40 && wallOpenTicks % grabEvery == 0) {
         net.minecraft.client.Screenshot.grab(minecraft, false);
         LOGGER.info("[seedqueue] periodic wall grab at ticks {}", wallOpenTicks);
      }

      if (wallShotPending > 0 && --wallShotPending == 0) {
         net.minecraft.client.Screenshot.grab(minecraft, false);
         LOGGER.info("[seedqueue] wall screenshot grabbed");
      }

      if (grabHoldTicks > 0 && --grabHoldTicks == 0) {
         PreviewCapture.active = false;
         me.voidxwalker.worldpreview.WorldPreview.suppressDebugOverlay = false;
      }

      if ((PreviewState.activePreviewEntry != null || pendingGrab)
         && minecraft.level != null && minecraft.gui.screen() == null) {
         stopPreviewCycle(minecraft);
      }

      if (Boolean.getBoolean("seedqueue.blankSmoke")) {
         if (minecraft.gui.screen() instanceof TitleScreen) {
            minecraft.setScreenAndShow(new BlankScreen());
         }

         if (++blankTicks == 80 && !blankShot) {
            blankShot = true;
            net.minecraft.client.Screenshot.grab(minecraft, false);
            LOGGER.info("[seedqueue] blank smoke: screen={}", minecraft.gui.screen() == null ? "null" : minecraft.gui.screen().getClass().getSimpleName());
         }
      }

      if (Boolean.getBoolean("seedqueue.titleSmoke") && minecraft.gui.screen() instanceof TitleScreen) {
         if (++titleTicks == 100 && !titleShotDone) {
            titleShotDone = true;
            net.minecraft.client.Screenshot.grab(minecraft, false);
            LOGGER.info("[seedqueue] captured title screenshot");
         }
      }

      if (ENABLED) {
         if (minecraft.gui.screen() instanceof WallScreen) {
            wallOpenTicks++;
            wallScheduledTick(minecraft);

            if (wallOpenTicks % 200 == 0) {
               Runtime runtime = Runtime.getRuntime();
               LOGGER.info(
                  "[seedqueue] wall ticks={} active={} entries={} fps={} sodiumCache={} heap={}MB screen={}",
                  wallOpenTicks,
                  PreviewState.activePreviewEntry != null,
                  ENTRIES.size(),
                  minecraft.getFps(),
                  SodiumLevelCache.size(),
                  (runtime.totalMemory() - runtime.freeMemory()) / 1048576L,
                  minecraft.gui.screen()
               );
            }
         } else {
            wallOpenTicks = 0;
         }

         if (Boolean.getBoolean("seedqueue.f3Smoke") && !f3Sent
         && minecraft.gui.screen() instanceof TitleScreen) {
         f3Sent = true;
         LOGGER.info("[seedqueue] pressing F3 on title");
         minecraft.keyboardHandler.keyPress(
            minecraft.getWindow().handle(),
            com.mojang.blaze3d.platform.InputConstants.KEY_F3,
            new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_F3, 0, 0)
         );
      }
      if (Boolean.getBoolean("seedqueue.wallAutoSmoke") && TEST
            && minecraft.gui.screen() instanceof TitleScreen && !ENTRIES.isEmpty()) {
            openWall(minecraft);
         }

         if (previewGrabEntry != null && me.voidxwalker.worldpreview.WorldPreview.hasMainCopy()) {
         QueueEntry entry = previewGrabEntry;
         previewGrabEntry = null;
         me.voidxwalker.worldpreview.WorldPreview.readMainCopy(image -> {
            try {
               entry.nativePreview = image;
               entry.uploadNativePreview();
               entry.lastPreviewAt = System.currentTimeMillis();
            } catch (Throwable t) {
               LOGGER.warn("[seedqueue] preview upload failed", t);
            }
         });
      }
      consumeGrab(minecraft);

      if (blankCaptureSavedWall != null && previewGrabEntry == null && grabStartedAt == 0) {
         net.minecraft.client.gui.screens.Screen wall = blankCaptureSavedWall;
         blankCaptureSavedWall = null;
         minecraft.gui.setScreen(wall);

         if (blankCaptureHudHidden) {
            blankCaptureHudHidden = false;
            minecraft.gui.hud.toggle();
         }

         stopPreviewCycle(minecraft);
      }

         if (!Boolean.getBoolean("seedqueue.manualPreview")
            && Boolean.parseBoolean(System.getProperty("seedqueue.livePreview", "false"))) {
            previewCycleTick(minecraft);
         }

         if (Boolean.getBoolean("seedqueue.frameOverrideTest") && !frameTestDone
            && minecraft.gui.screen() instanceof WallScreen) {
            for (QueueEntry candidate : ENTRIES) {
               if (candidate.state != QueueEntry.State.READY || candidate.server == null) {
                  continue;
               }

               if (candidate.externalPreview == null) {
                  candidate.externalPreview = me.voidxwalker.worldpreview.ExternalPreview.build(
                     minecraft, candidate.server, previewRadius(candidate)
                  );
               }

               if (candidate.externalPreview != null && candidate.renderProperties == null) {
                  candidate.renderProperties = new me.voidxwalker.worldpreview.WorldPreviewProperties(
                     candidate.externalPreview.level,
                     candidate.externalPreview.listener,
                     candidate.externalPreview.gameMode,
                     candidate.externalPreview.player,
                     candidate.externalPreview.spawn
                  );
               }

               if (candidate.renderProperties != null && candidate.renderTarget == null) {
                  candidate.renderTarget = me.voidxwalker.worldpreview.WorldPreview.createTarget(
                     minecraft, "seedqueue frame test", minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()
                  );
               }

               if (candidate.renderTarget != null) {
                  me.voidxwalker.worldpreview.WorldPreview.activateExternal(candidate.renderProperties);
                  frameTestEntry = candidate;
                  frameTestArmed = true;
                  frameTestDone = true;
                  LOGGER.info("[seedqueue] frame override test armed for #{}", candidate.number);
               }

               break;
            }
         }

         if (frameCaptureWaiting && frameCaptureEntry != null) {
         java.nio.file.Path shot = PreviewPipeline.findNewScreenshot();

         if (shot != null) {
            try {
               frameCaptureEntry.nativePreview = com.mojang.blaze3d.platform.NativeImage.read(java.nio.file.Files.newInputStream(shot));
               java.nio.file.Files.deleteIfExists(shot);
               try {
                  frameCaptureEntry.nativePreview.writeToFile(new java.io.File("seedqueue_spawncheck.png").getAbsoluteFile());
               } catch (Throwable ignored) {
               }

               frameCaptureEntry.uploadNativePreview();
               LOGGER.info("[seedqueue] loaded spawn capture from {}", shot.getFileName());
               frameCaptureWaiting = false;
               frameCaptureDone = true;
            } catch (Throwable t) {
            }
         }
      }

      if (Boolean.getBoolean("seedqueue.frameCaptureTest") && !frameCaptureDone && !frameCaptureActive
            && minecraft.gui.screen() instanceof WallScreen) {
            for (QueueEntry candidate : ENTRIES) {
               if (candidate.state != QueueEntry.State.READY || candidate.server == null) {
                  continue;
               }

               if (candidate.externalPreview == null) {
                  candidate.externalPreview = me.voidxwalker.worldpreview.ExternalPreview.build(minecraft, candidate.server, previewRadius(candidate));
               }

               if (candidate.externalPreview != null && candidate.renderProperties == null) {
                  candidate.renderProperties = new me.voidxwalker.worldpreview.WorldPreviewProperties(
                     candidate.externalPreview.level,
                     candidate.externalPreview.listener,
                     candidate.externalPreview.gameMode,
                     candidate.externalPreview.player,
                     candidate.externalPreview.spawn
                  );
               }

               if (candidate.renderProperties != null) {
                  me.voidxwalker.worldpreview.WorldPreview.activateExternal(candidate.renderProperties);
                  me.voidxwalker.worldpreview.WorldPreview.prepareExternal(minecraft);
                  me.voidxwalker.worldpreview.WorldPreview.forceLevelRender = true;
                  frameCaptureEntry = candidate;
                  frameCaptureActive = true;
                  frameCaptureTicks = 0;

                  if (Boolean.getBoolean("seedqueue.blankPreview")) {
                     minecraft.gui.setScreen(new BlankScreen());
                  }

                  LOGGER.info("[seedqueue] frame capture armed for #{}", candidate.number);
               }

               break;
            }
         }

         boolean allPreviewed = !ENTRIES.isEmpty();
         // wall shot delay is configurable for smoke tests
         for (QueueEntry entry : ENTRIES) {
            allPreviewed &= entry.previewTexture != null;
         }

         if (Boolean.getBoolean("seedqueue.previewShotTest") && !previewShotDone
         && minecraft.gui.screen() instanceof WallScreen && PreviewState.activePreviewEntry != null) {
         previewShotDone = true;
         PreviewCapture.active = true;
         net.minecraft.client.Screenshot.grab(minecraft, false);
         LOGGER.info("[seedqueue] preview window shot requested");
      }
         if (Boolean.getBoolean("seedqueue.wallShot") && minecraft.gui.screen() instanceof WallScreen
            && wallOpenTicks > Integer.getInteger("seedqueue.wallShotDelay", 200)
            && !wallShotDone) {
            wallShotDone = true;
            stopPreviewCycle(minecraft);
            wallShotPending = 3;
            LOGGER.info("[seedqueue] captured instance wall screenshot");
         }

         if (Boolean.getBoolean("seedqueue.holePreview")) {
            livePreviewTick(minecraft, WallScreen.hoveredEntry());
         }
      }

      if (Boolean.getBoolean("seedqueue.benchmarkSmoke") && minecraft.gui.screen() instanceof WallScreen
         && wallOpenTicks == 80 && !benchmarking && benchmarkFinishedAt == 0) {
         benchmarking = true;
         benchmarkCount = 0;
         benchmarkStarted = System.currentTimeMillis();
         LOGGER.info("[seedqueue] benchmark smoke started");
      }
      if (Boolean.getBoolean("seedqueue.wallF6Smoke") && !wallF6Sent
         && minecraft.gui.screen() instanceof WallScreen && wallOpenTicks > 120) {
         wallF6Sent = true;
         LOGGER.info("[seedqueue] wall F6 smoke: pressing F6 with the wall open, screen={}", minecraft.gui.screen());
         minecraft.keyboardHandler.keyPress(
            minecraft.getWindow().handle(),
            com.mojang.blaze3d.platform.InputConstants.KEY_F6,
            new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_F6, 0, 0)
         );
      }

      if (Boolean.getBoolean("seedqueue.f6Smoke") && minecraft.level != null && !f6Sent) {
         f6Ticks++;
         if (f6Ticks >= 80) {
            f6Sent = true;
            LOGGER.info("[seedqueue] pressing F6 in a joined world");
            minecraft.keyboardHandler.keyPress(
               minecraft.getWindow().handle(),
               com.mojang.blaze3d.platform.InputConstants.KEY_F6,
               new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_F6, 0, 0)
            );
         }
      }
      if (Boolean.getBoolean("seedqueue.wallKeySmoke") && minecraft.level != null) {
         wallKeyTicks++;
         if (!wallKeySent && wallKeyTicks >= 20) {
            wallKeySent = true;
            LOGGER.info("[seedqueue] pressing F7 in-game");
            minecraft.keyboardHandler.keyPress(minecraft.getWindow().handle(), com.mojang.blaze3d.platform.InputConstants.KEY_F6, new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_F6, 0, 0));
         }
      }

      if (Boolean.getBoolean("seedqueue.wallScreenshot") && !wallScreenshotDone
         && minecraft.gui.screen() instanceof WallScreen) {
         if (++wallScreenshotTicks >= Integer.getInteger("seedqueue.wallScreenshotTicks", 80)) {
            wallScreenshotDone = true;
            net.minecraft.client.Screenshot.grab(minecraft, false);
            LOGGER.info("[seedqueue] captured wall screenshot");
         }
      }

      if (Boolean.getBoolean("seedqueue.playNextLockSmoke") && !playNextLockSmokeDone
         && minecraft.gui.screen() instanceof WallScreen) {
         for (QueueEntry entry : ENTRIES) {
            if (entry.state == QueueEntry.State.READY) {
               playNextLockSmokeDone = true;
               entry.locked = true;
               entry.lockedAt = System.currentTimeMillis();
               LOGGER.info("[seedqueue] play next lock smoke: locked entry #{}", entry.number);
               playNextLock(minecraft);
               break;
            }
         }
      }

      if (Boolean.getBoolean("seedqueue.selfTest") && !selfTestDone && minecraft.gui.screen() instanceof WallScreen) {
         selfTestDone = true;
         WallPreview.selfTest(minecraft);
      }

      long wallOpenDelay = Long.getLong("seedqueue.wallOpenDelayMs", 0L);

      if (wallOpenDelay > 0 && wallDelayStartedAt == 0) {
         wallDelayStartedAt = System.currentTimeMillis() + wallOpenDelay;
      }

      if (wallEnabled() && !integrate() && !wallShown && minecraft.level == null && !ENTRIES.isEmpty()
         && minecraft.gui.screen() instanceof TitleScreen
         && (wallDelayStartedAt == 0 || System.currentTimeMillis() >= wallDelayStartedAt)) {
         wallShown = true;
         minecraft.setScreenAndShow(new WallScreen(() -> ENTRIES));
         LOGGER.info("[seedqueue] wall opened with {} entries", ENTRIES.size());
      }

      if (Boolean.getBoolean("seedqueue.previewSmoke") && !previewSmokeDone) {
         for (QueueEntry entry : ENTRIES) {
            if (entry.state == QueueEntry.State.READY && entry.server != null) {
               previewSmokeDone = true;
               var preview = me.voidxwalker.worldpreview.ExternalPreview.build(minecraft, entry.server, previewRadius(entry));
               LOGGER.info(
                  "[seedqueue] external preview for entry #{}: {}",
                  entry.number,
                  preview == null ? "failed" : preview.packets.size() + " chunks"
               );
               break;
            }
         }
      }

      if (Boolean.getBoolean("seedqueue.lockResetSmoke") && !lockResetDone) {
         for (QueueEntry entry : ENTRIES) {
            if (entry.state == QueueEntry.State.READY) {
               entry.locked = true;
               entry.lockedAt = System.currentTimeMillis();
               lockResetDone = true;
               LOGGER.info("[seedqueue] locked entry #{} so the reset must skip it", entry.number);
               break;
            }
         }
      }

      if (Boolean.getBoolean("seedqueue.wallSmokeLock")
         && minecraft.gui.screen() instanceof WallScreen wall
         && !lockSent
         && wall.debugCardCount() > 0) {
         boolean toggled = wall.debugToggleLockFirst();
         LOGGER.info("[seedqueue] wall lock toggled={}", toggled);
         lockSent = toggled;
      }

      if (Boolean.getBoolean("seedqueue.wallSmokeDelete")
         && minecraft.gui.screen() instanceof WallScreen wall
         && !deleteSent
         && wall.debugCardCount() > 0) {
         deleteSent = true;

         if (wall.debugMiddleClickFirst()) {
            LOGGER.info("[seedqueue] wall middle-click: deleted first card");
         }
      }

      if (Boolean.getBoolean("seedqueue.wallEscSmoke")
         && minecraft.gui.screen() instanceof WallScreen
         && !escSent
         && wallTicks > 60) {
         escSent = true;
         LOGGER.info("[seedqueue] pressing ESC on wall, screen={}", minecraft.gui.screen());
         net.minecraft.client.gui.screens.Screen wallScreen = minecraft.gui.screen();

         if (wallScreen instanceof WallScreen wall) {
            // Smoke test: dispatch the key to the screen directly, like the keyboard handler would.
            if (!wall.keyPressed(new net.minecraft.client.input.KeyEvent(41, com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE, 0))) {
               wall.onClose();
            }
         }

         LOGGER.info("[seedqueue] ESC dispatch done, screen={}", minecraft.gui.screen());
      }

      if (escSent && !minecraft.gui.screen().getClass().getSimpleName().equals("WallScreen")) {
         LOGGER.info("[seedqueue] after ESC screen={}", minecraft.gui.screen());
         net.minecraft.client.Screenshot.grab(minecraft, false);
         escSent = false;
      }
      if (Boolean.getBoolean("seedqueue.wallSmokeJoin")
         && minecraft.gui.screen() instanceof WallScreen wall
         && joinSmokeCount < Integer.getInteger("seedqueue.wallSmokeJoinCount", 1)
         && ENTRIES.stream().anyMatch(entry -> entry.state == QueueEntry.State.READY)) {
         joinSent = true;
         joinSmokeCount++;

         if (wall.debugClickFirst()) {
            LOGGER.info("[seedqueue] wall click: joining ready entry ({}. join)", joinSmokeCount);
         }
      }

      if (joinSent && minecraft.level != null && minecraft.gui.screen() == null && !minecraft.isPaused()) {
         joinTicks++;
         if (++joinTicks == 120 && !joinShotDone) {
            joinShotDone = true;
            net.minecraft.client.Screenshot.grab(minecraft, false);
            LOGGER.info("[seedqueue] joined world screenshot (level={})", minecraft.level);
         }
      }

      if (wallShown && minecraft.gui.screen() instanceof WallScreen) {
         wallTicks++;

         if (!previewShot
            && Boolean.getBoolean("seedqueue.smoke")
            && !ENTRIES.isEmpty()
            && ENTRIES.stream().allMatch(entry -> entry.previewTexture != null)
            && previewSettleTicks++ > 20) {
            previewShot = true;
            net.minecraft.client.Screenshot.grab(minecraft, false);
            LOGGER.info("[seedqueue] captured preview wall screenshot");
         }
      }

      if (TEST) {
         testTick(minecraft);
      } else if (integrate()) {
         integrateTick(minecraft);
      }
   }

   /**
    * Debug watchdog (1.21.4 parity): every 10 seconds print the render thread's stack trace (and the
    * background server threads' states) so a frozen reset can be diagnosed from the log.
    */
   private static void startWatchdog() {
      Thread thread = new Thread(() -> {
         while (true) {
            try {
               Thread.sleep(10_000L);
            } catch (InterruptedException e) {
               return;
            }

            try {
               java.util.Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
               Thread render = null;

               for (Thread candidate : traces.keySet()) {
                  if ("Render thread".equals(candidate.getName())) {
                     render = candidate;
                     break;
                  }
               }

               if (render != null) {
                  StringBuilder stack = new StringBuilder();

                  for (StackTraceElement element : traces.get(render)) {
                     stack.append("\n\tat ").append(element);
                  }

                  LOGGER.warn("[seedqueue] watchdog: render thread state={} stack:{}", render.getState(), stack);
               }

               for (java.util.Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
                  if (entry.getKey().getName().startsWith("SeedQueue server")) {
                     LOGGER.warn(
                        "[seedqueue] watchdog: {} state={} top={}",
                        entry.getKey().getName(),
                        entry.getKey().getState(),
                        entry.getValue().length == 0 ? "n/a" : entry.getValue()[0]
                     );
                  }
               }
            } catch (Throwable t) {
               LOGGER.warn("[seedqueue] watchdog failed", t);
            }
         }
      }, "SeedQueue Watchdog");
      thread.setDaemon(true);
      thread.setPriority(Thread.MIN_PRIORITY);
      thread.start();
      LOGGER.info("[seedqueue] watchdog started");
   }

   /**
    * Per-tick generation scheduler (1.21.4 parity): hands out at most
    * {@link QueueConfig#concurrency(boolean)} permits, always lets locked/scheduled worlds finish, and
    * resumes worlds that hit maxWorldGenerationPercentage once the queue is full and
    * resumeOnFilledQueue is enabled.
    */
   private static void applyGenerationPermits(Minecraft minecraft) {
      boolean wallOpen = minecraft.gui.screen() instanceof WallScreen;
      QueueExecutors.update(wallOpen);
      boolean resume = QueueConfig.resumeOnFilledQueue
         && !ENTRIES.isEmpty()
         && ENTRIES.size() >= queueSize()
         && ENTRIES.stream()
            .allMatch(
               entry -> entry.state == QueueEntry.State.READY
                  || entry.reachedLimit
                  || entry.state == QueueEntry.State.FAILED
                  || entry.state == QueueEntry.State.DISCARDED
            );
      int permits = QueueConfig.concurrency(wallOpen);

      for (QueueEntry entry : ENTRIES) {
         if (entry.locked && entry.state != QueueEntry.State.READY && entry.state != QueueEntry.State.FAILED) {
            entry.permit(true, true);
         }
      }

      for (QueueEntry entry : ENTRIES) {
         if (entry.locked) {
            continue;
         }

         if (entry.state == QueueEntry.State.READY
            || entry.state == QueueEntry.State.FAILED
            || entry.state == QueueEntry.State.DISCARDED) {
            entry.permit(false, false);
            continue;
         }

         boolean needs = !entry.reachedLimit || resume || entry.scheduledJoin;
         boolean permit = needs && (permits > 0 || resume || entry.scheduledJoin);
         entry.permit(permit, resume || entry.scheduledJoin);

         if (permit) {
            permits--;
         }
      }
   }

   /** True while fewer worlds than the concurrency limit are actively generating. */
   private static boolean canStartMoreEntries(Minecraft minecraft) {
      int concurrency = QueueConfig.concurrency(minecraft.gui.screen() instanceof WallScreen);
      long active = ENTRIES.stream()
         .filter(entry -> (entry.startQueued
            || entry.state == QueueEntry.State.STARTING
            || entry.state == QueueEntry.State.GENERATING)
            && !entry.reachedLimit)
         .count();

      if (active >= concurrency) {
         return false;
      }

      // Concurrent Server Starts: never start more servers at once than configured.
      long starting = ENTRIES.stream()
         .filter(entry -> entry.startQueued || entry.state == QueueEntry.State.STARTING)
         .count();
      return starting < Math.max(1, QueueConfig.startConcurrently);
   }

   /** Preload Worlds: while on the title screen only start up to prewarmWorlds worlds early. */
   private static boolean prewarmAllowsStart(Minecraft minecraft) {
      if (minecraft.gui.screen() instanceof WallScreen || !(minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen)) {
         return true;
      }

      int limit = Math.min(QueueConfig.prewarmWorlds, queueSize());

      if (limit <= 0) {
         return false;
      }

      long started = ENTRIES.stream()
         .filter(entry -> entry.state != QueueEntry.State.CREATED
            && entry.state != QueueEntry.State.PREPARING
            && entry.state != QueueEntry.State.PREPARED)
         .count();
      return started < limit;
   }

   /** Keeps the queue filled and lets Atum consume a ready world instead of creating a fresh one. */
   private static void integrateTick(Minecraft minecraft) {
      boolean wallOpen = minecraft.gui.screen() instanceof WallScreen;
      boolean idle = !wallOpen && (minecraft.gui.screen() instanceof TitleScreen || minecraft.level != null);

      long unlockedEntries = ENTRIES.stream().filter(entry -> !entry.locked).count();

      if (idle && unlockedEntries < queueSize() && QueueConfig.autoQueue) {
         QueueEntry entry = new QueueEntry(nextEntryNumber++, RANDOM.nextLong());
         ENTRIES.add(entry);
         LOGGER.info("[seedqueue] queued entry #{} (seed {})", entry.number, entry.seed);
         QueueExecutors.prepare().execute(() -> entry.prepare(minecraft));
      }

      for (QueueEntry entry : new ArrayList<>(ENTRIES)) {
         if (entry.state == QueueEntry.State.PREPARED && !entry.startQueued && prewarmAllowsStart(minecraft)
            && (entry.locked || canStartMoreEntries(minecraft))) {
            entry.startQueued = true;
            minecraft.execute(() -> entry.start(minecraft));
         }

         entry.tick();

         if (entry.state == QueueEntry.State.FAILED) {
            ENTRIES.remove(entry);
         }
      }
   }

   /**
    * Called when a fresh world is about to be created (Atum resets). Returns true when a queued
    * world was opened instead, in which case the caller must cancel the fresh world creation.
    */
   /**
    * Drops the cached Sodium section managers of every queued preview.
    *
    * <p>Their section graph is sized from the chunk radius and from the packets that were applied when
    * it was built. After the radius changes - or after the client rendered a real world between two
    * wall visits - the cached graph no longer matches the level, and whole areas of the card stay
    * unrendered. Destroying the managers makes Sodium rebuild them from scratch.</p>
    */
   public static void evictPreviewCaches(String reason) {
      int evicted = 0;

      for (QueueEntry entry : ENTRIES) {
         if (entry.externalPreview != null && entry.externalPreview.level != null) {
            SodiumLevelCache.evictLater(entry.externalPreview.level);
            // The rebuilt manager has an empty chunk tracker: force the snapshot to be re-applied.
            entry.externalPreview.invalidateSections();
            evicted++;
         }
      }

      LOGGER.info("[seedqueue] evicting {} preview section managers ({})", evicted, reason);
   }

   public static boolean consumeForReset(Minecraft minecraft) {
      if (!integrate()) {
         return false;
      }

      QueueEntry entry = null;

      for (QueueEntry candidate : ENTRIES) {
         if (candidate.state == QueueEntry.State.READY && !candidate.locked) {
            entry = candidate;
            break;
         }
      }

      if (entry == null) {
         LOGGER.info("[seedqueue] no ready queued world, creating a fresh one");
         return false;
      }

      LOGGER.info("[seedqueue] consuming queued entry #{} for the reset (seed {})", entry.number, entry.seed);
      return playEntry(entry);
   }

   /** Discards every unlocked entry (wall "reset all"). Returns how many were discarded. */
   public static int resetAllUnlocked() {
      int discarded = 0;

      for (QueueEntry entry : new ArrayList<>(ENTRIES)) {
         if (!entry.locked && !entry.transferred) {
            ENTRIES.remove(entry);

            if (PreviewState.frameSwapEntry == entry) {
               PreviewState.unbind();
            }

            discardAsync(entry);
            discarded++;
         }
      }

      if (discarded > 0) {
         LOGGER.info("[seedqueue] discarded {} unlocked entries", discarded);
      }

      return discarded;
   }

   /** Discards an entry on the background executor; never blocks the render thread. */
   private static void discardAsync(QueueEntry entry) {
      try {
         QueueExecutors.prepare().execute(entry::discard);
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] could not schedule discard for entry #{}, discarding inline", entry.number, t);
         entry.discard();
      }
   }

   /** Entries that are still generating (not READY yet). */
   public static int generatingCount() {
      int generating = 0;

      for (QueueEntry entry : ENTRIES) {
         if (entry.state != QueueEntry.State.READY) {
            generating++;
         }
      }

      return generating;
   }

   public static int entriesCount() {
      return ENTRIES.size();
   }

   /** Folders of the worlds currently queued; only these are hidden from the singleplayer list. */
   public static java.util.Set<String> activeWorldFolders() {
      java.util.Set<String> folders = new java.util.HashSet<>();

      for (QueueEntry entry : ENTRIES) {
         if (entry.folder != null) {
            folders.add(entry.folder);
         }
      }

      return folders;
   }

   /**
    * Builds and arms the preview for an entry before a hand-off (waitForPreviewSetup).
    * Returns true when the preview is active for that entry.
    */
   private static boolean ensurePreview(Minecraft minecraft, QueueEntry entry) {
      if (entry == null || entry.server == null) {
         return false;
      }

      if (entry.externalPreview != null) {
         return true;
      }

      try {
         java.util.List<net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket> snapshot;

         synchronized (entry.chunkPackets) {
            snapshot = new java.util.ArrayList<>(entry.chunkPackets);
         }

         entry.externalPreview = snapshot.size() >= 9
            ? me.voidxwalker.worldpreview.ExternalPreview.fromPackets(minecraft, entry.server, snapshot, 2)
            : me.voidxwalker.worldpreview.ExternalPreview.build(minecraft, entry.server, 2);

         if (entry.externalPreview == null) {
            return false;
         }

         SodiumLevelCache.markPreview(entry.externalPreview.level, entry.externalPreview.spawn);

         if (entry.renderProperties == null) {
            entry.renderProperties = new me.voidxwalker.worldpreview.WorldPreviewProperties(
               entry.externalPreview.level,
               entry.externalPreview.listener,
               entry.externalPreview.gameMode,
               entry.externalPreview.player,
               entry.externalPreview.spawn
            );
         }

         if (entry.displayTarget == null) {
            entry.displayTarget = me.voidxwalker.worldpreview.WorldPreview.createTarget(
               minecraft, "seedqueue card " + entry.number, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()
            );
         }

         me.voidxwalker.worldpreview.WorldPreview.activateExternal(entry.renderProperties);
         entry.externalPreview.activateSections();
         me.voidxwalker.worldpreview.WorldPreview.prepareExternal(minecraft);
         me.voidxwalker.worldpreview.WorldPreview.forceLevelRender = true;
         PreviewState.bind(entry);
         PreviewState.frameSwapDwellUntil = Long.MAX_VALUE;
         LOGGER.info("[seedqueue] prepared preview for hand-off of #{}", entry.number);
         return true;
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] failed to prepare preview for #{}", entry.number, t);
         return false;
      }
   }

   /** True when every queue slot is filled (used by Resume On Filled Queue). */
   public static boolean queueIsFull() {
      return !ENTRIES.isEmpty() && ENTRIES.size() >= queueSize();
   }

   /** Snapshot of the current entries for wall actions. */
   public static java.util.List<QueueEntry> entriesSnapshot() {
      return java.util.List.copyOf(ENTRIES);
   }

   /** Refills the queue after a wall reset/delete, mirroring the 1.21.4 refillAfterReset. */
   /** Number of entries whose server is currently parked (holding a full server + worlds). */
   /** True when at least one unlocked instance is READY and can be consumed by the benchmark. */
   public static boolean hasReadyUnlockedEntry() {
      for (QueueEntry entry : ENTRIES) {
         if (entry.state == QueueEntry.State.READY && !entry.locked && !entry.transferred) {
            return true;
         }
      }

      return false;
   }

   public static int parkedCount() {
      int count = 0;

      for (QueueEntry entry : ENTRIES) {
         if (entry.parked) {
            count++;
         }
      }

      return count;
   }

   /**
    * Hard cap on parked servers: every parked entry keeps a whole integrated server in memory, so the
    * number of them must never exceed the configured queue size. The oldest ones are halted and
    * discarded instead of being left alive forever (that is what ran the client out of memory).
    */
   private static void enforceParkedLimit() {
      int limit = Math.max(1, queueSize());
      int parked = parkedCount();

      if (parked <= limit) {
         return;
      }

      java.util.List<QueueEntry> candidates = new java.util.ArrayList<>();

      for (QueueEntry entry : ENTRIES) {
         if (entry.parked) {
            candidates.add(entry);
         }
      }

      candidates.sort(java.util.Comparator.comparingLong(entry -> entry.readyAtMillis));

      for (QueueEntry entry : candidates) {
         if (parked <= limit) {
            break;
         }

         LOGGER.warn(
            "[seedqueue] parked servers {} > queue size {}, discarding entry #{}",
            parked, limit, entry.number
         );
         ENTRIES.remove(entry);
         entry.discard();
         parked--;
      }
   }

   public static void refillQueue(Minecraft minecraft) {

      // While the player is actually playing in a world the queue stays frozen; it refills when the
      // wall is shown again. Generating in the background only costs frames here.
      // The benchmark runs inside a world and still needs the queue to refill, so it is exempt.
      if (minecraft.level != null && minecraft.player != null && !wallShown && !benchmarking) {
         return;
      }

      enforceParkedLimit();

      int parkedNow = parkedCount();
      int threshold = Math.max(1, Integer.getInteger("seedqueue.maxParkedServers", 8));

      if (parkedNow >= threshold && !benchmarking) {
         LOGGER.warn(
            "[seedqueue] {} parked servers reached the threshold ({}), pausing refill to protect memory",
            parkedNow, threshold
         );
         return;
      }

      long unlocked = ENTRIES.stream().filter(entry -> !entry.locked).count();

      while (unlocked < queueSize()) {
         QueueEntry entry = new QueueEntry(nextEntryNumber++, RANDOM.nextLong());
         ENTRIES.add(entry);
         unlocked++;
         LOGGER.info("[seedqueue] queued entry #{} (seed {})", entry.number, entry.seed);

         try {
            QueueExecutors.prepare().execute(() -> entry.prepare(minecraft));
         } catch (Throwable t) {
            LOGGER.warn("[seedqueue] could not queue entry #{} (executor stopped)", entry.number, t);
            ENTRIES.remove(entry);
            break;
         }
      }
   }

   /** Wall reset: discard one unlocked entry and refill its slot (1.21.4 "reset"). */
   public static void resetEntry(QueueEntry entry) {
      if (entry == null) {
         return;
      }

      resetEntries(java.util.List.of(entry));
   }

   /** Wall reset for one or more entries (row/column/reset all): one cooldown for the batch. */
   public static void resetEntries(java.util.Collection<QueueEntry> entries) {
      if (!QueueConfig.tryResetCooldown()) {
         return;
      }

      Minecraft minecraft = Minecraft.getInstance();
      int reset = 0;

      for (QueueEntry entry : new ArrayList<>(entries)) {
         if (entry == null || entry.locked || !ENTRIES.contains(entry)) {
            continue;
         }

         ENTRIES.remove(entry);

         if (PreviewState.frameSwapEntry == entry) {
            PreviewState.unbind();
         }

         // Never halt a server from the render thread: the server may still be generating chunks and
         // halt(true) waits for its thread, which would freeze the wall (1.21.4 discards in the
         // background executor for the same reason).
         discardAsync(entry);
         reset++;
      }

      if (reset > 0) {
         playSound("reset_instance");
         LOGGER.info("[seedqueue] reset {} entries", reset);
         refillQueue(minecraft);
      }
   }

   /** Deletes a single entry from the wall. */
   public static void discardEntry(QueueEntry entry) {
      if (entry == null) {
         return;
      }

      ENTRIES.remove(entry);
      discardAsync(entry);
      LOGGER.info("[seedqueue] deleted entry #{}", entry.number);
   }

   /** Resizes the per-card preview targets after a window resize (render thread). */
   public static void onWindowResize(int width, int height) {
      for (QueueEntry entry : ENTRIES) {
         resizeTarget(entry.renderTarget, width, height);
         resizeTarget(entry.displayTarget, width, height);
      }

      if (PreviewState.frameSwapEntry != null) {
         resizeTarget(PreviewState.frameSwapEntry.displayTarget, width, height);
      }

      WallPreview.resizeScratch(width, height);
      LOGGER.info("[seedqueue] resized preview targets to {}x{}", width, height);
   }

   private static void resizeTarget(com.mojang.blaze3d.pipeline.RenderTarget target, int width, int height) {
      if (target == null || (target.width == width && target.height == height)) {
         return;
      }

      try {
         target.resize(width, height);
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] failed to resize preview target", t);
      }
   }

   /** Stops the wall preview cycle; the preview world itself can be kept for a seamless join. */
   private static boolean stopWallPreview(Minecraft minecraft, QueueEntry keep) {
      boolean keepPreview = keep != null
         && PreviewState.frameSwapEntry == keep
         && me.voidxwalker.worldpreview.WorldPreview.isActive();

      PreviewState.unbind();
      stopPreviewCycle(minecraft);

      if (!keepPreview && me.voidxwalker.worldpreview.WorldPreview.isActive()) {
         me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
      }

      return keepPreview;
   }

   /**
    * Joins a ready queued world directly: the background integrated server is handed to the client,
    * so no world is loaded from disk and the vanilla loading screen is skipped.
    */
   /** Called when the client level changes; the wall closes once the local player exists. */
   public static void onLevelSet() {
      if (Boolean.getBoolean("seedqueue.screenLog")) {
         LOGGER.info("[seedqueue] client level installed, waiting for the player entity");
      }
   }

   /** Debug Menu F3: prints the hovered instance's state. */
   public static void logEntryDebug(QueueEntry entry) {
      LOGGER.info(
         "[seedqueue] debug entry #{} state={} seed={} chunks={}/{} locked={} scheduled={} reachedLimit={} paused={} frozen={} preview={} target={} level={}",
         entry.number,
         entry.state,
         entry.seed,
         entry.chunksGenerated,
         entry.chunksTotal,
         entry.locked,
         entry.scheduledJoin,
         entry.reachedLimit,
         entry.paused,
         entry.frozen,
         entry.externalPreview != null,
         entry.displayTarget != null,
         entry.externalPreview == null ? "null" : entry.externalPreview.level.dimension().identifier()
      );
   }

   /** Debug Menu Shift+F3: prints the hovered instance's server thread stack. */
   public static void logServerThreadStack(QueueEntry entry) {
      String name = "SeedQueue server " + entry.number;

      for (Thread thread : Thread.getAllStackTraces().keySet()) {
         if (name.equals(thread.getName())) {
            LOGGER.info("[seedqueue] debug entry #{} server thread state={}", entry.number, thread.getState());

            for (StackTraceElement element : thread.getStackTrace()) {
               LOGGER.info("[seedqueue] debug\tat {}", element);
            }

            return;
         }
      }

      LOGGER.info("[seedqueue] debug entry #{} has no live server thread", entry.number);
   }

   /** 1.21.4 "Play Next Lock": plays the oldest locked world that is ready. */
   public static boolean playNextLock(Minecraft minecraft) {
      QueueEntry next = null;

      for (QueueEntry entry : ENTRIES) {
         if (entry.locked && entry.state == QueueEntry.State.READY && !entry.transferred
            && (next == null || entry.lockedAt < next.lockedAt)) {
            next = entry;
         }
      }

      if (next == null) {
         LOGGER.info("[seedqueue] play next lock: no locked ready entry");
         return false;
      }

      LOGGER.info("[seedqueue] play next lock: entry #{}", next.number);
      return playEntry(next);
   }

   /** Entry the player just joined from the wall; used to align the player with the previewed spawn. */
   private static QueueEntry spawnAlignEntry;
   /** Spawn position of the preview the player joined from; survives the preview being released. */
   private static net.minecraft.core.BlockPos spawnAlignPos;

   public static boolean playEntry(QueueEntry entry) {
      if (entry == null || entry.transferred) {
         return false;
      }

      if (entry.state != QueueEntry.State.READY) {
         entry.locked = true;
         entry.lockedAt = System.currentTimeMillis();

         if (QueueConfig.smartSwitch) {
            for (QueueEntry other : ENTRIES) {
               if (other != entry && other.locked && other.state == QueueEntry.State.READY && !other.transferred) {
                  LOGGER.info("[seedqueue] smart switch: #{} is not ready, playing locked #{}", entry.number, other.number);
                  return playEntry(other);
               }
            }
         }

         entry.scheduledJoin = true;
         LOGGER.info("[seedqueue] entry #{} is not ready yet, scheduled join", entry.number);
         return false;
      }

      Minecraft minecraft = Minecraft.getInstance();

      if (entry.externalPreview != null) {
         // Cache the position itself: the preview object is gone by the time the client owns the world.
         spawnAlignEntry = entry;
         spawnAlignPos = entry.externalPreview.spawn;
      }


      // The wall draws the preview of a ready instance, but the preview itself is built a moment later.
      // Joining in between handed the client a level without a preview and left the title screen drawn
      // over the world, so entering is refused until the preview exists (or was explicitly skipped).
      if (QueueConfig.useWall && entry.externalPreview == null && entry.previewTexture == null) {
         entry.scheduledJoin = true;
         LOGGER.info("[seedqueue] entry #{} has no preview yet, scheduled join", entry.number);
         return false;
      }

      if (QueueConfig.waitForPreviewSetup) {
         ensurePreview(minecraft, entry);
      }

      playSound("play_instance");
      joinInProgress = true;
      boolean previewKept = stopWallPreview(minecraft, entry);
      ENTRIES.remove(entry);
      suppressLoadingScreens = true;
      LOGGER.info("[seedqueue] joining queued entry #{} (seed {})", entry.number, entry.seed);

      // Unpark before handing the server to the client: a server that is still parked never ticks, so
      // the world would stay limited to the preview chunks (no chunks load beyond them).
      entry.select();

      if (entry.play(minecraft)) {
         joinedFromWall = true;
         wallShown = false;

      // Leaving the wall frees every preview level's Sodium managers: their arenas stay referenced
      // otherwise and a later 256 MiB shared-arena allocation fails with GpuOutOfMemoryException.
      try {
         SodiumLevelCache.evictAllExcept(net.minecraft.client.Minecraft.getInstance().level);
         SodiumLevelCache.sweep();
      } catch (Throwable ignored) {
      }

         if (QueueConfig.joinTransition) {
            WallScreen.beginJoinTransition();
         }

         timerLogTicks = Boolean.getBoolean("seedqueue.timerLog") ? 0 : -1;
         timerFinishTicks = 0;

         if (previewKept) {
            // The preview world is already the queued world: keep it on screen and drop the vanilla
            // loading screen, so the client level swap is invisible (WorldPreview hands over later).
            PreviewState.handoffPreviewEntry = entry;
            handoffTicks = 0;

            if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) {
               minecraft.gui.setScreen(null);
            }
         }

         return true;
      }

      suppressLoadingScreens = false;
      joinInProgress = false;
      LOGGER.warn("[seedqueue] hand-off failed, discarding entry #{}", entry.number);
      entry.discard();
      return false;
   }

   private static void testTick(Minecraft minecraft) {
      if (!testStarted) {
         if (!(minecraft.gui.screen() instanceof TitleScreen)) {
            return;
         }

         testStarted = true;
         LOGGER.info("[seedqueue] test: queueing {} entries", queueSize());

         for (int i = 0; i < queueSize(); i++) {
            QueueEntry entry = new QueueEntry(nextEntryNumber++, RANDOM.nextLong());
            ENTRIES.add(entry);
            QueueExecutors.prepare().execute(() -> entry.prepare(minecraft));
         }
      }

      boolean allReady = !ENTRIES.isEmpty();
      boolean allFinished = !ENTRIES.isEmpty();

      for (QueueEntry entry : ENTRIES) {
         if (entry.state == QueueEntry.State.PREPARED && !entry.startQueued && prewarmAllowsStart(minecraft)
            && (entry.locked || canStartMoreEntries(minecraft))) {
            entry.startQueued = true;
            minecraft.execute(() -> entry.start(minecraft));
         }

         entry.tick();
         allReady &= entry.state == QueueEntry.State.READY;
         allFinished &= entry.state == QueueEntry.State.READY
            || entry.state == QueueEntry.State.FAILED
            || entry.state == QueueEntry.State.DISCARDED;
      }

      if (allReady && discordCountdown < 0) {
         LOGGER.info("[seedqueue] test: all {} entries ready", ENTRIES.size());

         if (Boolean.getBoolean("seedqueue.smoke")) {
            net.minecraft.client.Screenshot.grab(minecraft, false);
            LOGGER.info("[seedqueue] captured ready wall screenshot");
         }

         if (HANDOFF && !testDiscarded) {
            testDiscarded = true;
            QueueEntry entry = ENTRIES.get(0);
            LOGGER.info("[seedqueue] handoff: joining queued entry #{} (seed {})", entry.number, entry.seed);

            try {
               if (entry.server != null) {
                  entry.server.halt(true);
               }

               entry.transferred = true;
               ENTRIES.clear();
               QueueExecutors.shutdown();
               minecraft.createWorldOpenFlows().openWorld(entry.folder, () -> { });
               LOGGER.info("[seedqueue] handoff: openWorld called for {}", entry.folder);
            } catch (Throwable t) {
               LOGGER.error("[seedqueue] handoff failed", t);
            }
         } else {
            discordCountdown = Integer.getInteger(
               "seedqueue.testDiscardTicks", Boolean.getBoolean("seedqueue.liveSmoke") ? 240 : 60
            );
         }
      }

      if (discordCountdown > 0) {
         discordCountdown--;

         if (discordCountdown == 0 && !testDiscarded) {
            testDiscarded = true;
            ENTRIES.forEach(QueueEntry::discard);
            ENTRIES.clear();
            QueueExecutors.shutdown();
            LOGGER.info("[seedqueue] test: discarded all entries");
         }
      } else if (allFinished && !allReady && !testDiscarded) {
         testDiscarded = true;
         ENTRIES.forEach(QueueEntry::discard);
         ENTRIES.clear();
         QueueExecutors.shutdown();
         LOGGER.info("[seedqueue] test: finished with failures");
      }
   }
}
