package me.contaria.seedqueue;

import me.voidxwalker.worldpreview.ExternalPreview;
import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.WorldPreviewProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.render.SodiumLevelCache;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.config.*;

import static me.contaria.seedqueue.SeedQueue.*;

/**
 * 预览循环：卡片装配、渲染帧推进、抓帧与离开预览时的清理。
 * 
 * 在 Minecraft.renderFrame 之前由 WorldPreview 的帧钩子驱动，
 * 每张卡片停留 previewDwellMs 毫秒，期间渲染该实例的预览世界。
 */
public final class PreviewManager {
   private PreviewManager() {
   }

   /**
    * 每张预览卡片停留的毫秒数。
    */
   static int previewDwellMs() {
      return PREVIEW_DWELL_MS;
   }
   /**
    * 预览期间切换到第三人称视角。
    */
   static void applyThirdPerson() {
      PreviewPipeline.applyPerspective();
   }
   /**
    * 世界渲染前的帧交换调度入口。
    */
   static void frameSwapRenderTick(Minecraft minecraft) {
      if (minecraft.gui.screen() instanceof WallScreen) {
         if (FPS_LOG) {
            fpsFrames++;
            long now = System.currentTimeMillis();
            long value2 = now - fpsWindowStart;
            if (value2 >= 2000L) {
               LOGGER.info("[seedqueue] wall fps ~{} ({} frames / {} ms)", (long)(fpsFrames * 1000) / value2, fpsFrames, value2);
               fpsWindowStart = now;
               fpsFrames = 0;
            }
         }

         /**
          * 按停留时间轮换预览卡片并装配预览世界。
          */
         frameSwapTick(minecraft);
      }
   }
   /**
    * 某个实例的预览渲染半径。
    */
   public static int previewRadius(QueueEntry entry) {
      int count = Math.max(1, entry == null ? QueueConfig.chunkRadius : entry.chunkRadius);
      return Math.max(1, Math.min(QueueConfig.wallChunkDistance, count));
   }
   static void frameSwapTick(Minecraft minecraft) {
      PreviewPipeline.frameSwapTick(minecraft);
   }
   /**
    * 抓取当前预览帧到卡片显示目标。
    */
   public static void captureWallFrame(Minecraft minecraft) {
      PreviewPipeline.captureWallFrame(minecraft);
   }
   /**
    * 预览循环的每 tick 推进（截图、抓帧、冻结阶段）。
    */
   static void previewCycleTick(Minecraft minecraft) {
      if (shotPhase == 0 && !joinInProgress && PreviewState.handoffPreviewEntry == null) {
         if (PreviewState.activePreviewEntry == null) {
            if (minecraft.gui.screen() instanceof WallScreen && !ENTRIES.isEmpty()) {
               long now = System.currentTimeMillis();
               long value2 = (long)PREVIEW_REFRESH_MS;
               int count = ENTRIES.size();
               WorldPreview.hideDebugOverlay = true;

               for (int i = 0; i < count; i++) {
                  QueueEntry entry = ENTRIES.get(previewCycleIndex++ % count);
                  if (entry.state == QueueEntry.State.READY && entry.server != null && (entry.previewTexture == null || now - entry.lastPreviewAt >= value2)) {
                     applyThirdPerson();
                     if (entry.externalPreview == null) {
                        entry.externalPreview = ExternalPreview.build(minecraft, entry.server, previewRadius(entry));
                     }

                     applyThirdPerson();
                     if (entry.externalPreview != null) {
                        if (entry.renderProperties == null) {
                           entry.renderProperties = new WorldPreviewProperties(
                                 entry.externalPreview.level,
                                 entry.externalPreview.listener,
                                 entry.externalPreview.gameMode,
                                 entry.externalPreview.player,
                                 entry.externalPreview.spawn
                              )
                              .withCamera(entry.externalPreview.cameraAnchor);
                        }

                        WorldPreview.activateExternal(entry.renderProperties);
                        WorldPreview.prepareExternal(minecraft);
                        WorldPreview.forceLevelRender = true;
                        if (entry.renderTarget == null) {
                           entry.renderTarget = WorldPreview.createTarget(
                              minecraft, "seedqueue card " + entry.number, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()
                           );
                        }

                        if (entry.displayTarget == null) {
                           entry.displayTarget = WorldPreview.createTarget(
                              minecraft, "seedqueue display " + entry.number, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()
                           );
                        }

                        WorldPreview.setTargetOverride(entry.renderTarget);
                        PreviewState.activePreviewEntry = entry;
                        return;
                     }
                  }
               }
            }
         }
      }
   }
   /**
    * 停止预览循环并解除预览绑定。
    */
   static void stopPreviewCycle(Minecraft minecraft) {
      PreviewPipeline.stop(minecraft);
   }
   /**
    * 渲染帧结束后的收尾（handoff、清理队列）。
    */
   public static void afterRenderFrame(Minecraft minecraft) {
      if (PreviewCapture.active && --PreviewCapture.hold <= 0) {
         PreviewCapture.active = false;
         WorldPreview.instanceRendering = false;
      }

      if (pendingGrab) {
         pendingGrab = false;
         grabStartedAt = System.currentTimeMillis();
         Screenshot.grab(minecraft, false);
         LOGGER.info("[seedqueue] grabbed preview frame for #{}", previewGrabEntry == null ? -1 : previewGrabEntry.number);
      }

      if (frameCaptureActive && frameCaptureTicks >= 40) {
         WorldPreview.instanceRendering = false;
         WorldPreview.forceLevelRender = false;
         WorldPreview.deactivateExternal();
         frameCaptureActive = false;
         frameCaptureWaiting = true;
         LOGGER.info("[seedqueue] frame capture finished for #{}", frameCaptureEntry == null ? -1 : frameCaptureEntry.number);
      } else if (frameTestActive) {
         WorldPreview.setTargetOverride(null);
         WorldPreview.deactivateExternal();
         frameTestActive = false;
         LOGGER.info("[seedqueue] frame override finished, dump pending");
      } else if (offscreenPending) {
         offscreenPending = false;
         QueueEntry entry = PreviewCapture.entry;
         WorldPreview.captureTargetImage(arg -> {
            if (entry != null) {
               entry.nativePreview = arg;
            }
         });
         WorldPreview.restoreMainTarget(minecraft);
      }
   }

   /**
    * 进入世界/内存紧张时释放当前不需要的预览世界（墙不可见时所有卡片预览都不需要），
    * 返回墙后 PreviewManager 会按需重新构建。
    */
   static void releaseHiddenPreviews(String reason) {
      int released = 0;

      for (QueueEntry entry : ENTRIES) {
         if (entry == liveEntry || entry.selected || entry.transferred || entry.externalPreview == null) {
            continue;
         }

         try {
            if (entry.externalPreview.level != null) {
               SodiumLevelCache.evictLater(entry.externalPreview.level);
            }
         } catch (Throwable error) {
            SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
         }

         entry.externalPreview = null;
         released++;
      }

      if (released > 0) {
         evictPreviewCaches(reason);
         SeedQueue.LOGGER.info("[seedqueue] released {} hidden preview levels ({})", released, reason);
      }
   }
}
