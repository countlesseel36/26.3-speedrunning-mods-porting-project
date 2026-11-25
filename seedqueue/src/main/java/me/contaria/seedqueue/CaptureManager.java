package me.contaria.seedqueue;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import me.voidxwalker.worldpreview.ExternalPreview;
import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.WorldPreviewProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.config.*;

import static me.contaria.seedqueue.SeedQueue.*;

/**
 * 抓帧与冻结：卡片截图阶段、冻结抓帧与停止墙预览。
 * 
 * 在实例就绪后抓取一帧画面生成静态卡片图，避免墙上实时渲染过多实例。
 */
public final class CaptureManager {
   private CaptureManager() {
   }

   /**
    * 是否启用截图式预览。
    */
   static boolean shotPreviewEnabled() {
      return Boolean.parseBoolean(System.getProperty("seedqueue.shotPreview", "false"));
   }
   /**
    * 截图预览结束后的清理推进。
    */
   static void tickShotTeardown(Minecraft minecraft) {
      if (shotPhase == 4) {
         if (minecraft.level == null) {
            Screen screen = shotWall;
            shotWall = null;
            shotPhase = 0;
            if (screen != null) {
               minecraft.setScreenAndShow(new WallScreen(() -> ENTRIES));
               wallShown = true;
               wallTicks = 0;
               LOGGER.info("[seedqueue] shot preview: back on wall");
            }
         }
      }
   }
   /**
    * 是否启用冻结抓帧。
    */
   static boolean freezeCaptureEnabled() {
      return Boolean.parseBoolean(System.getProperty("seedqueue.freezeCapture", "false"));
   }
   /**
    * 冻结抓帧状态机：等待渲染、抓帧、冻结。
    */
   static void tickFreezeCapture(Minecraft minecraft) {
      if (Boolean.getBoolean("seedqueue.freezeDebug")) {
         LOGGER.info("[seedqueue] freezeDebug phase={} screen={} entries={} enabled={}", freezePhase, minecraft.gui.screen(), ENTRIES.size(), freezeCaptureEnabled());
      }

      if (freezeCaptureEnabled()) {
         if (freezePhase == 0) {
            if (minecraft.gui.screen() instanceof WallScreen) {
               QueueEntry entry = null;

               for (QueueEntry entry2 : ENTRIES) {
                  if (entry2.state == QueueEntry.State.READY && !entry2.frozen && entry2.previewTexture == null && entry2.server != null) {
                     entry = entry2;
                     break;
                  }
               }

               if (entry != null) {
                  try {
                     ArrayList list;
                     synchronized (entry.chunkPackets) {
                        list = new ArrayList<>(entry.chunkPackets);
                     }

                     if (entry.externalPreview == null) {
                        entry.externalPreview = list.size() >= 9
                           ? ExternalPreview.fromPackets(minecraft, entry.server, list, 2)
                           : ExternalPreview.build(minecraft, entry.server, 2);
                     }

                     if (entry.externalPreview == null) {
                        return;
                     }

                     System.setProperty("worldpreview.thirdPerson", "false");
                     System.setProperty("worldpreview.simulatedWidth", String.valueOf(QueueConfig.simulatedWidth));
                     System.setProperty("worldpreview.simulatedHeight", String.valueOf(QueueConfig.simulatedHeight));
                     entry.renderProperties = new WorldPreviewProperties(
                           entry.externalPreview.level,
                           entry.externalPreview.listener,
                           entry.externalPreview.gameMode,
                           entry.externalPreview.player,
                           entry.externalPreview.spawn
                        )
                        .withCamera(entry.externalPreview.cameraAnchor);
                     WorldPreview.activateExternal(entry.renderProperties);
                     WorldPreview.forceLevelRender = true;
                     WorldPreview.hideDebugOverlay = true;
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
                  } catch (Throwable error) {
                     LOGGER.warn("[seedqueue] freeze capture: setup failed", error);
                     /**
                      * 完成冻结抓帧并保存静态卡片图。
                      */
                     finishFreezeCapture(minecraft, null);
                  }
               }
            }
         } else if (freezePhase == 1) {
            if (freezeWait > 0) {
               freezeWait--;
            } else {
               freezeGrabAt = System.currentTimeMillis();
               Screenshot.grab(minecraft, false);
               LOGGER.info("[seedqueue] freeze capture: grabbed #{}", freezeEntry == null ? -1 : freezeEntry.number);
               freezePhase = 2;
            }
         } else if (freezePhase == 2) {
            File file = new File(minecraft.gameDirectory, "screenshots");
            if (file.isDirectory()) {
               for (File file2 : file.listFiles()) {
                  if (file2.isFile() && file2.getName().endsWith(".png") && file2.length() >= 1000L && file2.lastModified() >= freezeGrabAt - 1000L) {
                     int count = 0;
                     int count2 = 0;

                     try {
                        NativeImage image = NativeImage.read(Files.newInputStream(file2.toPath()));

                        try {
                           count = image.getWidth();
                           count2 = image.getHeight();
                           NativeImage image2 = new NativeImage(count, count2, false);

                           for (int i = 0; i < count2; i++) {
                              for (int j = 0; j < count; j++) {
                                 image2.setPixel(j, i, image.getPixel(j, i));
                              }
                           }

                           if (freezeEntry != null) {
                              freezeEntry.nativePreview = image2;
                              freezeEntry.captureWidth = count;
                              freezeEntry.captureHeight = count2;
                              freezeEntry.uploadNativePreview();
                              LOGGER.info("[seedqueue] freeze capture: card image {}x{} for #{}", count, count2, freezeEntry.number);
                           }
                        } catch (Throwable error2) {
                           if (image != null) {
                              try {
                                 image.close();
                              } catch (Throwable error3) {
                                 error2.addSuppressed(error3);
                              }
                           }

                           throw error2;
                        }

                        if (image != null) {
                           image.close();
                        }
                     } catch (Throwable error4) {
                        LOGGER.warn("[seedqueue] freeze capture: reading screenshot failed", error4);
                     }

                     try {
                        Files.deleteIfExists(file2.toPath());
                     } catch (Throwable error5) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error5);
      }

                     finishFreezeCapture(minecraft, null);
                     return;
                  }
               }
            }
         }
      }
   }
   static void finishFreezeCapture(Minecraft minecraft, QueueEntry entry) {
      QueueEntry entry2 = freezeEntry;
      freezeEntry = null;
      freezePhase = 0;
      freezeWait = 0;
      WorldPreview.forceLevelRender = false;
      WorldPreview.hideDebugOverlay = false;
      WorldPreview.deactivateExternal();
      if (freezeHudHidden) {
         freezeHudHidden = false;
         minecraft.gui.hud.toggle();
      }

      Screen screen = freezeWall;
      freezeWall = null;
      if (screen != null) {
         minecraft.setScreenAndShow(new WallScreen(() -> ENTRIES));
         wallShown = true;
         wallTicks = 0;
      }

      if (entry2 != null) {
         entry2.frozen = true;
         entry2.releaseForScreenshot();
      }
   }
   /**
    * 停止墙上的实时预览并恢复世界状态。
    */
   static boolean stopWallPreview(Minecraft minecraft, QueueEntry entry) {
      boolean flag = entry != null && PreviewState.frameSwapEntry == entry && WorldPreview.isActive();
      PreviewState.unbind();
      stopPreviewCycle(minecraft);

      // 进入世界后不再做“每帧把 minecraft.player/level 换成预览对象”的交换：
      // 登录/容器同步窗口内保留交换可能让同步包作用到预览玩家上，导致客户端背包与服务端不一致。
      // 出生点对齐仍然使用 entry 里保存的 externalPreview/renderProperties，不依赖这个交换。
      if (WorldPreview.isActive()) {
         WorldPreview.deactivateExternal();
      }

      return flag;
   }
}
