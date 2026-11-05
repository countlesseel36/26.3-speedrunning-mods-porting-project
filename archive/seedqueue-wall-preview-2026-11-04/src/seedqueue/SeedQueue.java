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
   public static final String BUILD = "seedqueue-26.3-build-" + Integer.getInteger("seedqueue.build", 700);
   public static final boolean ENABLED = !Boolean.getBoolean("seedqueue.disable");
   private static final boolean TEST = Boolean.getBoolean("seedqueue.test");
   private static final boolean HANDOFF = Boolean.getBoolean("seedqueue.handoff");
   private static final boolean WALL_PROPERTY = Boolean.getBoolean("seedqueue.wall");

   private static final List<QueueEntry> ENTRIES = new ArrayList<>();
   private static final Random RANDOM = new Random();
   private static int nextEntryNumber;
   private static boolean testStarted;
   private static boolean testDiscarded;
   private static boolean wallShown;
   private static net.minecraft.client.gui.screens.Screen wallReturnScreen;
   private static int wallTicks;
   private static int configTicks;
   private static int wallKeyTicks;
   private static boolean wallKeySent;
   private static boolean joinSent;
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
   private static QueueEntry capturingEntry;
   private static me.voidxwalker.worldpreview.ExternalPreview capturingPreview;
   private static int captureTicks;
   private static boolean captureRequested;
   private static boolean captureFrameActive;
   private static int captureHold;
   private static net.minecraft.client.gui.screens.Screen blankCaptureSavedWall;
   private static boolean blankCaptureHudHidden;
   private static int restoreTicks = -1;
   private static boolean captureHudHidden;
   private static net.minecraft.client.gui.screens.Screen captureSavedScreen;
   private static boolean captureShot;
   private static boolean frameCaptured;
   private static boolean swapTestDone;
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
   private static QueueEntry activePreviewEntry;
   private static int previewFramesLeft;
   private static boolean previewCaptured;
   private static int previewCycleIndex;
   private static boolean copyDumped;
   private static int wallOpenTicks;
   private static boolean pendingGrab;
   private static long grabStartedAt;
   private static int grabHoldTicks;
   private static QueueEntry previewGrabEntry;
   private static boolean pendingPreviewCapture;
   private static QueueEntry pendingPreviewEntry;
   private static boolean frameCaptureDone;
   private static long frameCaptureStartMillis;
   private static boolean frameCaptureWaiting;
   private static int frameCaptureCleanupTicks;
   private static int renderedFrames;
   private static int offscreenTicks;
   private static boolean offscreenDone;
   private static int wallShotTicks;
   private static boolean wallShotDone;
   private static int wallShotPending;
   private static long captureStartMillis;
   private static boolean offscreenPending;
   private static boolean offscreenSwapped;
   private static boolean lockResetDone;
   private static boolean previewSmokeDone;
   private static int discordCountdown = -1;

   @Override
   public void onInitializeClient() {
      QueueConfig.load();
      cleanupStaleSaves();
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
   public static void shutdown() {
      for (QueueEntry entry : new ArrayList<>(ENTRIES)) {
         entry.discard();
      }

      if (liveEntry != null) {
         me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
         liveEntry.previewActive = false;
         liveEntry = null;
      }

      ENTRIES.clear();
      QueueExecutors.shutdown();
      LOGGER.info("[seedqueue] shut down background queue");
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
      if (shotPhase != 0) {
         return;
      }

      if (activePreviewEntry != null) {
         return;
      }

      if (!(minecraft.gui.screen() instanceof WallScreen) || ENTRIES.isEmpty()) {
         return;
      }

      long now = System.currentTimeMillis();
      long refresh = Integer.getInteger("seedqueue.previewRefreshMs", 3000);
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

         System.setProperty("worldpreview.thirdPerson", String.valueOf("third_person".equals(QueueConfig.perspective)));

         if (entry.externalPreview == null) {
            entry.externalPreview = me.voidxwalker.worldpreview.ExternalPreview.build(minecraft, entry.server, 2);
         }

         System.setProperty("worldpreview.thirdPerson", String.valueOf("third_person".equals(QueueConfig.perspective)));

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
                  "third_person".equals(QueueConfig.perspective)
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
         activePreviewEntry = entry;
         previewFramesLeft = 10;
         previewCaptured = false;
         return;
      }
   }

   /** Advances an active preview cycle: request the capture, then deactivate after a few frames. */
   private static void previewCycleFrame(Minecraft minecraft) {
      if (activePreviewEntry == null) {
         return;
      }

      if (!(minecraft.gui.screen() instanceof WallScreen) && blankCaptureSavedWall == null) {
         stopPreviewCycle(minecraft);
         return;
      }

      previewFramesLeft--;

      if (!previewCaptured && previewFramesLeft <= 4) {
         previewCaptured = true;
         QueueEntry entry = activePreviewEntry;
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
      return activePreviewEntry;
   }

   public static boolean isCaptureFrameActive() {
      return captureFrameActive;
   }

   public static void stopPreviewCycle(Minecraft minecraft) {
      pendingGrab = false;
      grabStartedAt = 0;
      me.voidxwalker.worldpreview.WorldPreview.instanceRendering = false;
      me.voidxwalker.worldpreview.WorldPreview.forceLevelRender = false;
      me.voidxwalker.worldpreview.WorldPreview.restoreMainTarget(minecraft);
      me.voidxwalker.worldpreview.WorldPreview.hideDebugOverlay = false;
      me.voidxwalker.worldpreview.WorldPreview.suppressDebugOverlay = false;
      captureFrameActive = false;

      if (activePreviewEntry != null) {
         try {
            me.voidxwalker.worldpreview.WorldPreview.swapOut(minecraft);
         } catch (Throwable t) {
            LOGGER.warn("[seedqueue] failed to swap out preview", t);
         }

         activePreviewEntry = null;
      }

      if (me.voidxwalker.worldpreview.WorldPreview.isActive()) {
         me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
      }

      previewCaptured = false;
      previewFramesLeft = 0;
      me.voidxwalker.worldpreview.WorldPreview.setTargetOverride(null);
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
            System.setProperty("worldpreview.thirdPerson", String.valueOf("third_person".equals(QueueConfig.perspective)));

         if (entry.externalPreview == null) {
               entry.externalPreview = me.voidxwalker.worldpreview.ExternalPreview.build(minecraft, entry.server, 2);
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

            if (entry.renderTarget != null
               && me.voidxwalker.worldpreview.WorldPreview.renderInstance(minecraft, entry.renderProperties, entry.renderTarget)) {
               entry.lastRenderedAt = System.currentTimeMillis();
               renderedFrames++;
               budget--;

               if (Boolean.getBoolean("seedqueue.renderLog") && SUCCESS_LOG_COUNTER.getAndIncrement() < 10) {
                  LOGGER.info("[seedqueue] rendered instance #{} (frame {})", entry.number, renderedFrames);
               }

               if (Boolean.getBoolean("seedqueue.dumpTargets") && renderedFrames < 4) {
                  java.io.File dump = new java.io.File("seedqueue_target_" + entry.number + "_" + renderedFrames + ".png")
                     .getAbsoluteFile();
                  net.minecraft.client.Screenshot.takeScreenshot(entry.renderTarget, image -> {
                     try {
                        image.writeToFile(dump);
                        LOGGER.info("[seedqueue] wrote instance target {}", dump);
                     } catch (Throwable t) {
                        LOGGER.warn("[seedqueue] failed to write instance target", t);
                     }
                  });
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
         hovered.externalPreview = me.voidxwalker.worldpreview.ExternalPreview.build(minecraft, hovered.server, 2);
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

   /** Builds and captures a real rendered frame for each ready instance, one at a time. */
   private static void previewTick(Minecraft minecraft) {
      if (capturingEntry != null) {
         captureTicks++;

         if (!captureRequested && captureTicks > 30) {
            captureRequested = true;
            LOGGER.info("[seedqueue] hiding UI before capturing instance #{}", capturingEntry.number);
            captureFrameActive = true;
            captureSavedScreen = minecraft.gui.screen();

            if (captureSavedScreen != null) {
               minecraft.setScreenAndShow(new BlankScreen());
            }

            if (!minecraft.gui.hud.isHidden()) {
               minecraft.gui.hud.toggle();
               captureHudHidden = true;
            }
         }

         if (Boolean.getBoolean("seedqueue.swapTest") && !swapTestDone && captureRequested && captureTicks > 40) {
            swapTestDone = true;
            boolean swapped = me.voidxwalker.worldpreview.WorldPreview.swapMainTarget(minecraft);
            LOGGER.info("[seedqueue] swap test: swapped={}", swapped);
            net.minecraft.client.Screenshot.grab(minecraft, false);
         }

         if (captureRequested && !captureShot && captureTicks > 50) {
            captureShot = true;
            LOGGER.info("[seedqueue] capturing spawn view for instance #{}", capturingEntry.number);
            captureStartMillis = System.currentTimeMillis();
            net.minecraft.client.Screenshot.grab(minecraft, false);
         }

         if (captureRequested && capturingEntry.nativePreview == null
            && !(minecraft.gui.screen() instanceof BlankScreen)) {
            minecraft.setScreenAndShow(new BlankScreen());
            LOGGER.info("[seedqueue] re-asserted blank screen for capture");
         }

         if (capturingEntry.nativePreview == null && captureStartMillis > 0) {
            java.nio.file.Path shot = findNewScreenshot();

            if (shot != null) {
               try {
                  capturingEntry.nativePreview = com.mojang.blaze3d.platform.NativeImage.read(java.nio.file.Files.newInputStream(shot));
                  java.nio.file.Files.deleteIfExists(shot);
                  LOGGER.info("[seedqueue] loaded captured spawn view from {}", shot.getFileName());
               } catch (Throwable t) {
                  LOGGER.warn("[seedqueue] failed to load captured screenshot", t);
               }

               captureStartMillis = 0;
            }
         }

         if (capturingEntry.nativePreview != null) {
            capturingEntry.uploadNativePreview();
            restoreAfterCapture(minecraft);
            me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
            capturingEntry = null;
            capturingPreview = null;
            captureTicks = 0;
            captureRequested = false;
         }

         return;
      }

      for (QueueEntry entry : ENTRIES) {
         if (entry.state == QueueEntry.State.READY && entry.mapColors != null
            && entry.previewTexture == null && entry.nativePreview == null && entry.server != null) {
            capturingPreview = me.voidxwalker.worldpreview.ExternalPreview.build(minecraft, entry.server, 2);

            if (capturingPreview != null) {
               me.voidxwalker.worldpreview.WorldPreview.activateExternal(
                  new me.voidxwalker.worldpreview.WorldPreviewProperties(
                     capturingPreview.level, capturingPreview.listener, capturingPreview.gameMode, capturingPreview.player, capturingPreview.spawn
                  )
               );
               capturingEntry = entry;
               captureTicks = 0;
               captureRequested = false;
               LOGGER.info("[seedqueue] capturing 3D preview for entry #{}", entry.number);
            }

            break;
         }
      }
   }

   private static java.nio.file.Path findNewScreenshot() {
      try {
         java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("screenshots");

         if (!java.nio.file.Files.isDirectory(dir)) {
            return null;
         }

         try (var stream = java.nio.file.Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".png"))
               .filter(p -> {
                  try {
                     return java.nio.file.Files.getLastModifiedTime(p).toMillis() >= captureStartMillis;
                  } catch (Throwable t) {
                     return false;
                  }
               })
               .max(java.util.Comparator.comparingLong(p -> {
                  try {
                     return java.nio.file.Files.getLastModifiedTime(p).toMillis();
                  } catch (Throwable t) {
                     return 0L;
                  }
               }))
               .orElse(null);
         }
      } catch (Throwable t) {
         return null;
      }
   }

   /** Called at the end of a frame: grabs the preview window image and keeps the wall hidden. */
   public static void afterRenderFrame(Minecraft minecraft) {
      if (captureFrameActive && --captureHold <= 0) {
         captureFrameActive = false;
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
      QueueEntry entry = capturingEntry;
      me.voidxwalker.worldpreview.WorldPreview.captureTargetImage(image -> {
         if (entry != null) {
            entry.nativePreview = image;
         }
      });
      me.voidxwalker.worldpreview.WorldPreview.restoreMainTarget(minecraft);
   }
   public static void restoreAfterCapture(Minecraft minecraft) {
      if (!captureFrameActive) {
         return;
      }

      if (captureSavedScreen != null) {
         minecraft.gui.setScreen(captureSavedScreen);
         captureSavedScreen = null;
      }

      if (captureHudHidden) {
         minecraft.gui.hud.toggle();
         captureHudHidden = false;
      }

      captureFrameActive = false;
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

   public static void openWall(Minecraft minecraft) {
      if (minecraft.gui.screen() instanceof WallScreen) {
         return;
      }

      wallReturnScreen = minecraft.gui.screen();
      wallShown = true;
      wallTicks = 0;
      minecraft.setScreenAndShow(new WallScreen(() -> ENTRIES));
      LOGGER.info("[seedqueue] wall opened from key with {} entries", ENTRIES.size());
   }

   /** Called when the wall is closed: restores the game state and the screen it was opened from. */
   public static void onWallClosed(Minecraft minecraft) {
      LOGGER.info("[seedqueue] onWallClosed");
      stopPreviewCycle(minecraft);
      me.voidxwalker.worldpreview.WorldPreview.hideDebugOverlay = false;
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
      return Boolean.parseBoolean(System.getProperty("seedqueue.shotPreview", "true"));
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
            "third_person".equals(QueueConfig.perspective)
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
               minecraft, entry.server, snapshot, 2
            );

            if (entry.externalPreview == null) {
               continue;
            }

            com.mojang.blaze3d.platform.NativeImage image = SpawnViewRenderer.render(
               entry.externalPreview.level,
               entry.externalPreview.spawn,
               0.0,
               0.0,
               "third_person".equals(QueueConfig.perspective)
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

   private static void freezeReadyEntries(Minecraft minecraft) {
      if (!Boolean.parseBoolean(System.getProperty("seedqueue.freeze", "true"))) {
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
                     "third_person".equals(QueueConfig.perspective)
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

   public static void tick(Minecraft minecraft) {
      tickShotTeardown(minecraft);
      tickScreenshotPreview(minecraft);
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
         captureFrameActive = false;
         me.voidxwalker.worldpreview.WorldPreview.suppressDebugOverlay = false;
      }

      if ((activePreviewEntry != null || pendingGrab)
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

            if (wallOpenTicks % 200 == 0) {
               LOGGER.info("[seedqueue] wall ticks={} active={} entries={} screen={}", wallOpenTicks, activePreviewEntry != null, ENTRIES.size(), minecraft.gui.screen());
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
                     minecraft, candidate.server, 2
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
         java.nio.file.Path shot = findNewScreenshot();

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
                  candidate.externalPreview = me.voidxwalker.worldpreview.ExternalPreview.build(minecraft, candidate.server, 2);
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
         && minecraft.gui.screen() instanceof WallScreen && activePreviewEntry != null) {
         previewShotDone = true;
         captureFrameActive = true;
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

      if (Boolean.getBoolean("seedqueue.wallKeySmoke") && minecraft.level != null) {
         wallKeyTicks++;
         if (!wallKeySent && wallKeyTicks >= 20) {
            wallKeySent = true;
            LOGGER.info("[seedqueue] pressing F7 in-game");
            minecraft.keyboardHandler.keyPress(minecraft.getWindow().handle(), com.mojang.blaze3d.platform.InputConstants.KEY_F7, new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_F7, 0, 0));
         }
      }
      if (Boolean.getBoolean("seedqueue.configSmoke") && minecraft.gui.screen() instanceof TitleScreen) {
         LOGGER.info("[seedqueue] opening settings screen");
         minecraft.setScreenAndShow(new SettingsScreen(null));
      }

      if (Boolean.getBoolean("seedqueue.configSmoke")
         && Boolean.getBoolean("seedqueue.smoke")
         && minecraft.gui.screen() instanceof SettingsScreen) {
         configTicks++;

         if (configTicks == 60) {
            SettingsScreen screen = (SettingsScreen) minecraft.gui.screen();
            for (var child : screen.children()) {
               if (child instanceof net.minecraft.client.gui.components.Button button
                  && button.getMessage().getString().startsWith("Queue size")) {
                  button.onPress(new net.minecraft.client.input.MouseButtonInfo(0, 0));
                  LOGGER.info("[seedqueue] pressed queue size button, now {}", QueueConfig.queueSize);
               }
            }
         }

         if (configTicks == 100) {
            net.minecraft.client.Screenshot.grab(minecraft, false);
            LOGGER.info("[seedqueue] captured settings screenshot");
         }
      }

      if (wallEnabled() && !integrate() && !wallShown && minecraft.level == null && !ENTRIES.isEmpty()
         && minecraft.gui.screen() instanceof TitleScreen) {
         wallShown = true;
         minecraft.setScreenAndShow(new WallScreen(() -> ENTRIES));
         LOGGER.info("[seedqueue] wall opened with {} entries", ENTRIES.size());
      }

      if (Boolean.getBoolean("seedqueue.previewSmoke") && !previewSmokeDone) {
         for (QueueEntry entry : ENTRIES) {
            if (entry.state == QueueEntry.State.READY && entry.server != null) {
               previewSmokeDone = true;
               var preview = me.voidxwalker.worldpreview.ExternalPreview.build(minecraft, entry.server, 2);
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
         && !joinSent
         && ENTRIES.stream().anyMatch(entry -> entry.state == QueueEntry.State.READY)) {
         joinSent = true;

         if (wall.debugClickFirst()) {
            LOGGER.info("[seedqueue] wall click: joining first ready entry");
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

   /** Keeps the queue filled and lets Atum consume a ready world instead of creating a fresh one. */
   private static void integrateTick(Minecraft minecraft) {
      boolean wallOpen = minecraft.gui.screen() instanceof WallScreen;
      boolean idle = !wallOpen && (minecraft.gui.screen() instanceof TitleScreen || minecraft.level != null);

      if (idle && ENTRIES.size() < queueSize() && QueueConfig.autoQueue) {
         QueueEntry entry = new QueueEntry(nextEntryNumber++, RANDOM.nextLong());
         ENTRIES.add(entry);
         LOGGER.info("[seedqueue] queued entry #{} (seed {})", entry.number, entry.seed);
         QueueExecutors.prepare().execute(() -> entry.prepare(minecraft));
      }

      for (QueueEntry entry : new ArrayList<>(ENTRIES)) {
         if (entry.state == QueueEntry.State.PREPARED) {
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
            entry.discard();
            discarded++;
         }
      }

      if (discarded > 0) {
         LOGGER.info("[seedqueue] discarded {} unlocked entries", discarded);
      }

      return discarded;
   }

   /** Deletes a single entry from the wall. */
   public static void discardEntry(QueueEntry entry) {
      if (entry == null) {
         return;
      }

      ENTRIES.remove(entry);
      entry.discard();
      LOGGER.info("[seedqueue] deleted entry #{}", entry.number);
   }

   /** Joins a ready queued world directly (used by wall clicks and by reset consumption). */
   public static boolean playEntry(QueueEntry entry) {
      if (entry == null || entry.state != QueueEntry.State.READY || entry.transferred) {
         return false;
      }

      stopPreviewCycle(Minecraft.getInstance());
      ENTRIES.remove(entry);
      LOGGER.info("[seedqueue] joining queued entry #{} (seed {})", entry.number, entry.seed);

      try {
         if (entry.server != null) {
            entry.server.halt(true);
         }

         entry.transferred = true;
         Minecraft.getInstance().createWorldOpenFlows().openWorld(entry.folder, () -> { });
         return true;
      } catch (Throwable t) {
         LOGGER.error("[seedqueue] failed to join queued world #{}", entry.number, t);
         return false;
      }
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
         if (entry.state == QueueEntry.State.PREPARED) {
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
