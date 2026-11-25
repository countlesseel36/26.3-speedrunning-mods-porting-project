package me.contaria.seedqueue;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import me.voidxwalker.worldpreview.WorldPreview;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelStorageSource;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

import static me.contaria.seedqueue.SeedQueue.*;

/**
 * 主循环：每 tick 的队列维持、墙状态同步、预览/加入推进与调试信息。
 * 
 * 由 MinecraftTickMixin 调用 SeedQueue.tick，再委派到这里执行。
 */
public final class TickManager {
   /** 是否曾经进入过世界（用于检测断开）。 */
   private static boolean hadLevel;

   /** 加入世界后是否已安排一次背包同步。 */
   private static boolean inventoryResyncPending;
   /** 背包同步延迟计时（40 tick）。 */
   private static int inventoryResyncTicks;


   private TickManager() {
   }

   /**
    * 主循环：墙状态同步、队列维持、加入/预览推进与调试输出。
    */
   public static void tick(Minecraft minecraft) {
      SodiumLevelCache.processPending();
      processPendingDeletions();
      resumePausedGeneration();
      if (benchmarking) {
         int count = WallKeybindings.key(WallKeybindings.Action.CANCEL_BENCHMARK);
         int count2 = WallKeybindings.key(WallKeybindings.Action.QUIT);
         if (count != -1 && InputConstants.isKeyDown(count) || count2 != -1 && InputConstants.isKeyDown(count2)) {
            cancelBenchmark();
         }
      }

      if (benchmarking) {
         resourceSampleTick(minecraft);
      }

      if (benchmarking && ENTRIES.isEmpty() && refillCooldown > 1) {
         refillCooldown = 0;
      }

      if (minecraft.level != null
         && minecraft.player != null
         && joinedFromWall
         && System.currentTimeMillis() - lastHandoffAt < 5000L
         && (minecraft.gui.screen() instanceof TitleScreen || minecraft.gui.screen() instanceof PauseScreen)) {
         LOGGER.info("[seedqueue] dismissing {} after a wall join", minecraft.gui.screen().getClass().getSimpleName());
         minecraft.gui.setScreen(null);
      }

      if (minecraft.level != null && minecraft.player == null && minecraft.gui.screen() != null && !(minecraft.gui.screen() instanceof WallScreen)) {
         if (++stuckLoadingTicks == 60) {
            LOGGER.info(
               "[seedqueue] forcing the overlay closed: {} title={}", minecraft.gui.screen().getClass().getSimpleName(), minecraft.gui.screen().getTitle().getString()
            );
            suppressLoadingScreens = false;
            minecraft.gui.setScreen(null);
         }
      } else {
         stuckLoadingTicks = 0;
      }

      if (minecraft.level != null && syncedDamageTypes == null) {
         try {
            syncedDamageTypes = minecraft.level
               .registryAccess()
               .lookupOrThrow(Registries.DAMAGE_TYPE)
               .registryKeySet()
               .stream()
                .map(arg -> (ResourceKey<?>)arg)
               .collect(Collectors.toUnmodifiableSet());
            LOGGER.info("[seedqueue] captured {} synced damage types", syncedDamageTypes.size());
         } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }
      }

      if (benchmarking) {
         wallScheduledTick(minecraft);
      }

      if (pendingPlayNextLock && System.currentTimeMillis() - pendingPlayNextLockAt > 2000L) {
         pendingPlayNextLock = false;
         LOGGER.info("[seedqueue] pending play-next-lock expired");
      }

      if (pendingPlayNextLock && minecraft.level == null) {
         if (minecraft.gui.screen() instanceof WallScreen) {
            pendingPlayNextLock = false;
            playNextLock(minecraft);
         } else if (!pendingWallReturn && ++wallReturnTicks >= 1) {
            pendingPlayNextLock = false;
            playNextLock(minecraft);
         }
      }

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

      if (minecraft.level != lastPauseLevel) {
         lastPauseLevel = minecraft.level;
         if (spawnAlignPos != null && minecraft.player != null) {
            try {
               IntegratedServer integratedServer = minecraft.getSingleplayerServer();
               ServerPlayer serverPlayer = integratedServer == null ? null : integratedServer.getPlayerList().getPlayer(minecraft.player.getUUID());
               if (serverPlayer != null) {
                  serverPlayer.teleportTo(
                     integratedServer.overworld(),
                     (double)spawnAlignPos.getX() + 0.5,
                     (double)spawnAlignPos.getY(),
                     (double)spawnAlignPos.getZ() + 0.5,
                     Collections.emptySet(),
                     serverPlayer.getYRot(),
                     serverPlayer.getXRot(),
                     false
                  );
                  LOGGER.info("[seedqueue] aligned player to previewed spawn {}", spawnAlignPos);
               }
            } catch (Throwable error2) {
               LOGGER.warn("[seedqueue] previewed spawn alignment failed", error2);
            }

            spawnAlignPos = null;
            spawnAlignEntry = null;
         }

         if (!joinInProgress && !(minecraft.gui.screen() instanceof WallScreen)) {
            try {
               if (PreviewState.frameSwapEntry != null) {
                  WorldPreview.deactivateExternal();
                  PreviewState.unbind();
               }

               SodiumLevelCache.evictAllExcept(minecraft.level);
               SodiumLevelCache.sweep();
            } catch (Throwable error3) {
               LOGGER.warn("[seedqueue] failed to release previews after a level change", error3);
            }

            LOGGER.info("[seedqueue] level changed, previews released");
         }
      }

      if (minecraft.level != null && minecraft.player != null && (minecraft.gui.screen() instanceof LevelLoadingScreen || minecraft.gui.screen() instanceof ProgressScreen)) {
         LOGGER.info("[seedqueue] dismissing the loading screen {} (world and player are ready)", minecraft.gui.screen().getClass().getSimpleName());
         suppressLoadingScreens = false;
         minecraft.gui.setScreen(null);
      }

      if (minecraft.gui.screen() instanceof WallScreen) {
         WorldPreview.hideDebugOverlay = true;
      }

      if (previewRadiusBuilt != QueueConfig.chunkRadius) {
         if (previewRadiusBuilt != -1) {
            evictPreviewCaches("chunk radius " + previewRadiusBuilt + " -> " + QueueConfig.chunkRadius);
         }

         previewRadiusBuilt = QueueConfig.chunkRadius;
      }

      if (suppressLoadingScreens && !joinInProgress && minecraft.level == null) {
         LOGGER.info("[seedqueue] clearing a stale loading-screen suppression");
         suppressLoadingScreens = false;
      }

      if (suppressLoadingScreens) {
         if (minecraft.level != null && minecraft.player != null) {
            WorldPreview.rebindRealLevel(minecraft);
            PreviewState.renderDistance = -1;
            alignPlayerToPreviewedSpawn(spawnAlignEntry, minecraft);
            spawnAlignEntry = null;
            suppressLoadingScreens = false;
            suppressTicks = 0;
            joinInProgress = false;
            stopPreviewCycle(minecraft);
            Screen screen = minecraft.gui.screen();
            if (screen instanceof WallScreen
               || screen instanceof LevelLoadingScreen
               || screen instanceof TitleScreen
               || screen instanceof ProgressScreen
               || screen instanceof BlankScreen) {
               minecraft.gui.setScreen(null);
            }
         } else if (++suppressTicks > 400) {
            suppressTicks = 0;
            LOGGER.warn("[seedqueue] level hand-off timed out, restoring screens");
            suppressLoadingScreens = false;
            joinInProgress = false;
            if (minecraft.gui.screen() instanceof WallScreen || minecraft.gui.screen() instanceof BlankScreen) {
               minecraft.gui.setScreen(null);
            }
         }
      }

      if (!wallOpenedInWorld
         && joinedFromWall
         && PreviewState.handoffPreviewEntry == null
         && minecraft.level != null
         && minecraft.player != null
         && minecraft.gui.screen() instanceof WallScreen) {
         LOGGER.info("[seedqueue] closing the wall: the client joined a world");
         joinInProgress = false;
         stopPreviewCycle(minecraft);
         WorldPreview.releasePreviewCameraAndEntities(minecraft);
         WorldPreview.rebindRealLevel(minecraft);
         PreviewState.renderDistance = -1;
         minecraft.gui.setScreen(null);
      }

      if (joinedFromWall
         && PreviewState.handoffPreviewEntry == null
         && minecraft.level != null
         && minecraft.player != null
         && !(minecraft.gui.screen() instanceof WallScreen)
         && WorldPreview.isActive()) {
         LOGGER.info("[seedqueue] releasing a leftover preview in a joined world");
         stopPreviewCycle(minecraft);
         WorldPreview.releasePreviewCameraAndEntities(minecraft);
         if (minecraft.player.level() instanceof ClientLevel level && level != minecraft.level) {
            LOGGER.info("[seedqueue] restoring the joined client level");
            minecraft.setLevel(level);
         }

         WorldPreview.rebindRealLevel(minecraft);
         PreviewState.renderDistance = -1;
      }

      applyGenerationPermits(minecraft);
      if (Boolean.getBoolean("seedqueue.levelListLog") && !levelListLogged && lastPlayedFolder != null && minecraft.level != null) {
         levelListLogged = true;

         try {
            LevelStorageSource levelStorageSource = LevelStorageSource.createDefault(FabricLoader.getInstance().getGameDir().resolve("saves"));
            ArrayList list = new ArrayList();
            levelStorageSource.findLevelCandidates().levels().forEach(arg -> list.add(arg.directoryName()));
            LOGGER.info("[seedqueue] level list ({}): {} containsPlayed={}", list.size(), list, list.contains(lastPlayedFolder));
         } catch (Throwable error4) {
            LOGGER.warn("[seedqueue] level list check failed", error4);
         }
      }

      if ((configSmokeDone || configListSmokeDone) && minecraft.gui.screen() != null && configSmokeTicks >= 0 && ++configSmokeTicks == 80) {
         Screenshot.grab(minecraft, false);
         LOGGER.info("[seedqueue] captured config screen screenshot");
         configSmokeTicks = -1;
      }

      handoffTick(minecraft);
      if (timerLogTicks >= 0 && ++timerLogTicks == 60) {
         TimerCompat.report("60 ticks after join");
         timerLogTicks = -1;
      }

      if (timerFinishTicks >= 0 && ++timerFinishTicks == 10) {
         timerFinishTicks = -1;
         TimerCompat.finish();
      }

      tickShotTeardown(minecraft);
      tickFreezeCapture(minecraft);
      freezeReadyEntries(minecraft);
      int count3 = Integer.getInteger("seedqueue.wallGrabEvery", 0);
      if (count3 > 0 && minecraft.gui.screen() instanceof WallScreen && wallOpenTicks > 40 && wallOpenTicks % count3 == 0) {
         Screenshot.grab(minecraft, false);
         LOGGER.info("[seedqueue] periodic wall grab at ticks {}", wallOpenTicks);
      }


      if (grabHoldTicks > 0 && --grabHoldTicks == 0) {
         PreviewCapture.active = false;
         WorldPreview.suppressDebugOverlay = false;
      }

      if ((PreviewState.activePreviewEntry != null || pendingGrab) && minecraft.level != null && minecraft.gui.screen() == null) {
         stopPreviewCycle(minecraft);
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

         if (previewGrabEntry != null && WorldPreview.hasMainCopy()) {
            QueueEntry entry = previewGrabEntry;
            previewGrabEntry = null;
            WorldPreview.readMainCopy(arg -> {
               try {
                  entry.nativePreview = arg;
                  entry.uploadNativePreview();
                  entry.lastPreviewAt = System.currentTimeMillis();
               } catch (Throwable error5) {
                  LOGGER.warn("[seedqueue] preview upload failed", error5);
               }
            });
         }

         if (blankCaptureSavedWall != null && previewGrabEntry == null && grabStartedAt == 0L) {
            Screen screen2 = blankCaptureSavedWall;
            blankCaptureSavedWall = null;
            minecraft.gui.setScreen(screen2);
            if (blankCaptureHudHidden) {
               blankCaptureHudHidden = false;
               minecraft.gui.hud.toggle();
            }

            stopPreviewCycle(minecraft);
         }

         if (!Boolean.getBoolean("seedqueue.manualPreview") && Boolean.parseBoolean(System.getProperty("seedqueue.livePreview", "false"))) {
            previewCycleTick(minecraft);
         }

         if (frameCaptureWaiting && frameCaptureEntry != null) {
            Path path = PreviewPipeline.findNewScreenshot();
            if (path != null) {
               try {
                  frameCaptureEntry.nativePreview = NativeImage.read(Files.newInputStream(path));
                  Files.deleteIfExists(path);

                  try {
                     frameCaptureEntry.nativePreview.writeToFile(new File("seedqueue_spawncheck.png").getAbsoluteFile());
                  } catch (Throwable error6) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error6);
      }

                  frameCaptureEntry.uploadNativePreview();
                  LOGGER.info("[seedqueue] loaded spawn capture from {}", path.getFileName());
                  frameCaptureWaiting = false;
               } catch (Throwable error7) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error7);
      }
            }
         }

         boolean allHavePreview = !ENTRIES.isEmpty();

         for (QueueEntry candidate : ENTRIES) {
            allHavePreview &= candidate.previewTexture != null;
         }

      }

      if (Boolean.getBoolean("seedqueue.wallScreenshot")
         && !wallScreenshotDone
         && minecraft.gui.screen() instanceof WallScreen
         && ++wallScreenshotTicks >= Integer.getInteger("seedqueue.wallScreenshotTicks", 80)) {
         wallScreenshotDone = true;
         Screenshot.grab(minecraft, false);
         LOGGER.info("[seedqueue] captured wall screenshot");
      }

      long delayMs = Long.getLong("seedqueue.wallOpenDelayMs", 0L);
      if (delayMs > 0L && wallDelayStartedAt == 0L) {
         wallDelayStartedAt = System.currentTimeMillis() + delayMs;
      }

      if (wallEnabled()
         /**
          * 整合模式是否启用。
          */
         && !integrate()
         && !wallShown
         && minecraft.level == null
         && !ENTRIES.isEmpty()
         && minecraft.gui.screen() instanceof TitleScreen
         && (wallDelayStartedAt == 0L || System.currentTimeMillis() >= wallDelayStartedAt)) {
         wallShown = true;
         minecraft.setScreenAndShow(new WallScreen(() -> ENTRIES));
         LOGGER.info("[seedqueue] wall opened with {} entries", ENTRIES.size());
      }

      if (escSent && !minecraft.gui.screen().getClass().getSimpleName().equals("WallScreen")) {
         LOGGER.info("[seedqueue] after ESC screen={}", minecraft.gui.screen());
         Screenshot.grab(minecraft, false);
         escSent = false;
      }

      if (joinSent && minecraft.level != null && minecraft.gui.screen() == null && !minecraft.isPaused()) {
         joinTicks++;
         if (++joinTicks == 120 && !joinShotDone) {
            joinShotDone = true;
            Screenshot.grab(minecraft, false);
            LOGGER.info("[seedqueue] joined world screenshot (level={})", minecraft.level);
         }
      }

      if (wallShown && minecraft.gui.screen() instanceof WallScreen) {
         wallTicks++;
      }

      if (integrate()) {
         /**
          * 整合模式：自动入队与启动实例。
          */
         integrateTick(minecraft);
      }

      if (minecraft.gui.screen() instanceof WallScreen) {
         /**
          * 墙上时保持队列填充。
          */
         keepWallQueueFilled(minecraft);
         logGenerationState();
      }


      // 加入世界后延迟 40 tick 强制同步一次背包：快速加入时客户端容器状态可能不同步，
      // 表现为同类物品不合并/槽位显示错乱，重新登录后恢复。
      if (minecraft.player == null) {
         inventoryResyncPending = false;
         inventoryResyncTicks = 0;
      } else if (liveEntry() != null && Boolean.parseBoolean(System.getProperty("seedqueue.inventoryResync", "true"))) {
         if (!inventoryResyncPending) {
            inventoryResyncPending = true;
            inventoryResyncTicks = 0;
         } else if (inventoryResyncTicks >= 0 && ++inventoryResyncTicks >= 40) {
            if (inventoryResyncTicks < 400) {
               inventoryResyncTicks = -(inventoryResyncTicks + 60);
            } else {
               inventoryResyncTicks = -1;
            }
            var server = minecraft.getSingleplayerServer();

            if (server != null) {
               server.execute(() -> {
                  for (var player : server.getPlayerList().getPlayers()) {
                     player.inventoryMenu.broadcastFullState();
                     player.containerMenu.broadcastFullState();
                  }

                  LOGGER.info("[seedqueue] resynced player inventory after join");

                  if (Boolean.getBoolean("seedqueue.inventoryDebug")) {
                     for (var player : server.getPlayerList().getPlayers()) {
                        var inventory = player.getInventory();

                        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                           var stack = inventory.getItem(slot);

                           if (!stack.isEmpty()) {
                              LOGGER.info("[seedqueue] inv slot {}: {} x{}", slot, stack.getItem(), stack.getCount());
                           }
                        }
                     }
                  }
               });
            }
         }
      }

      // 断开世界后释放客户端对旧 ClientLevel 的引用：levelExtractor / Camera / 粒子引擎 /
      // Sodium 都持有世界对象，不主动清空时旧世界（含渲染 section）要等很久才能被 GC 回收。
      if (minecraft.level != null) {
         hadLevel = true;
      } else if (hadLevel) {
         hadLevel = false;
         releaseClosedWorldRefs(minecraft);
      }

      sessionDiagnostics();
      DiagnosticsManager.logHeapTrend();
      memoryGuard();
   }

   /**
    * 断开世界后释放客户端层面对旧世界的引用。
    */
   private static void releaseClosedWorldRefs(Minecraft minecraft) {
      int released = 0;

      try {
         minecraft.levelExtractor.setLevel(null);
         released++;
      } catch (Throwable error) {
         LOGGER.debug("[seedqueue] levelExtractor release failed", error);
      }

      try {
         minecraft.gameRenderer.mainCamera().setLevel(null);
         released++;
      } catch (Throwable error) {
         LOGGER.debug("[seedqueue] camera release failed", error);
      }

      try {
         minecraft.particleEngine.setLevel(null);
         released++;
      } catch (Throwable error) {
         LOGGER.debug("[seedqueue] particle engine release failed", error);
      }

      try {
         Class<?> sodiumCompat = Class.forName("me.voidxwalker.worldpreview.SodiumCompat");
         sodiumCompat.getMethod("release").invoke(null);
         released++;
      } catch (Throwable error) {
         LOGGER.debug("[seedqueue] sodium level release failed", error);
      }

      try {
         me.contaria.seedqueue.render.SodiumLevelCache.sweep();
      } catch (Throwable error) {
         LOGGER.debug("[seedqueue] sodium cache sweep failed", error);
      }

      LOGGER.info("[seedqueue] released closed-world references ({})", released);
   }

   static void keepWallQueueFilled(Minecraft minecraft) {
      if (!heapPressure && QueueConfig.autoQueue && canQueueAnother()) {
         refillQueue(minecraft);
      }
   }
   static void integrateTick(Minecraft minecraft) {
      boolean onWall = minecraft.gui.screen() instanceof WallScreen;
      boolean canAutoQueue = !onWall && (minecraft.gui.screen() instanceof TitleScreen || minecraft.level != null);
      long pendingCount = ENTRIES.stream().filter(arg -> !arg.transferred).count();
      int maxSlots = Math.max(1, QueueConfig.rows * QueueConfig.columns);
      if (canAutoQueue && pendingCount < (long)effectiveQueueSize() && pendingCount < (long)maxSlots && QueueConfig.autoQueue && canQueueAnother()) {
         QueueEntry entry = new QueueEntry(nextEntryNumber++, nextQueueSeed());
         ENTRIES.add(entry);
         LOGGER.info("[seedqueue] queued entry #{} (seed {})", entry.number, entry.seed);
         QueueExecutors.prepare().execute(() -> entry.prepare(minecraft));
      }

      for (QueueEntry candidate : new ArrayList<>(ENTRIES)) {
         if (candidate.state == QueueEntry.State.PREPARED && !candidate.startQueued && prewarmAllowsStart(minecraft) && (candidate.locked || canStartMoreEntries(minecraft))) {
            candidate.startQueued = true;
            minecraft.execute(() -> candidate.start(minecraft));
         }

         candidate.tick();
         if (candidate.state == QueueEntry.State.FAILED) {
            ENTRIES.remove(candidate);
         }
      }
   }
   static boolean integrate() {
      String text = System.getProperty("seedqueue.integrate");
      return text != null ? Boolean.parseBoolean(text) : QueueConfig.autoQueue;
   }
   /**
    * 输出单个实例的调试状态。
    */
   public static void logEntryDebug(QueueEntry entry) {
      LOGGER.info(
         "[seedqueue] debug entry #{} state={} seed={} chunks={}/{} locked={} scheduled={} reachedLimit={} paused={} frozen={} preview={} target={} level={}",
         new Object[]{
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
         }
      );
   }
   /**
    * 输出实例服务端线程栈（卡住诊断）。
    */
   public static void logServerThreadStack(QueueEntry entry) {
      String text = "SeedQueue server " + entry.number;

      for (Thread thread : Thread.getAllStackTraces().keySet()) {
         if (text.equals(thread.getName())) {
            LOGGER.info("[seedqueue] debug entry #{} server thread state={}", entry.number, thread.getState());

            for (StackTraceElement stackTraceElement : thread.getStackTrace()) {
               LOGGER.info("[seedqueue] debug\tat {}", stackTraceElement);
            }

            return;
         }
      }

      LOGGER.info("[seedqueue] debug entry #{} has no live server thread", entry.number);
   }
   /**
    * 获取 Sodium 版本字符串。
    */
   static String sodiumVersion() {
      try {
         return FabricLoader.getInstance().getModContainer("sodium").map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("none");
      } catch (Throwable error) {
         return "unknown";
      }
   }
   /**
    * 判断伤害类型是否已在服务端注册。
    */
   public static boolean isDamageTypeRegistered(MinecraftServer minecraftServer, ClientboundDamageEventPacket clientboundDamageEventPacket) {
      try {
         Optional optional = clientboundDamageEventPacket.sourceType().unwrapKey();
         if (optional.isEmpty()) {
            return false;
         } else {
            boolean hasDamageType = minecraftServer.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).containsKey((ResourceKey)optional.get());
            return !hasDamageType ? false : syncedDamageTypes == null || syncedDamageTypes.contains(optional.get());
         }
      } catch (Throwable error) {
         return false;
      }
   }


}
