package me.contaria.seedqueue;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.ArrayList;
import me.voidxwalker.worldpreview.ExternalPreview;
import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.WorldPreviewProperties;
import net.minecraft.client.Minecraft;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;

import static me.contaria.seedqueue.SeedQueue.*;

/**
 * 渲染与窗口：窗口尺寸变化、渲染目标重建与预览目标准备。
 */
public final class RenderManager {
   private RenderManager() {
   }

   /**
    * 窗口尺寸变化时调整卡片渲染目标。
    */
   public static void onWindowResize(int count, int count2) {
      for (QueueEntry entry : ENTRIES) {
         /**
          * 调整单个渲染目标的尺寸。
          */
         resizeTarget(entry.renderTarget, count, count2);
         resizeTarget(entry.displayTarget, count, count2);
      }

      if (PreviewState.frameSwapEntry != null) {
         resizeTarget(PreviewState.frameSwapEntry.displayTarget, count, count2);
      }

      WallPreview.resizeScratch(count, count2);
      LOGGER.info("[seedqueue] resized preview targets to {}x{}", count, count2);
   }
   static void resizeTarget(RenderTarget target, int count, int count2) {
      if (target != null && (target.width != count || target.height != count2)) {
         try {
            target.resize(count, count2);
         } catch (Throwable error) {
            LOGGER.warn("[seedqueue] failed to resize preview target", error);
         }
      }
   }
   /**
    * 确保实例拥有可用的预览目标。
    */
   static boolean ensurePreview(Minecraft minecraft, QueueEntry entry) {
      if (entry == null || entry.server == null) {
         return false;
      } else if (entry.externalPreview != null) {
         return true;
      } else {
         try {
            ArrayList list;
            synchronized (entry.chunkPackets) {
               list = new ArrayList<>(entry.chunkPackets);
            }

            entry.externalPreview = list.size() >= 9 ? ExternalPreview.fromPackets(minecraft, entry.server, list, 2) : ExternalPreview.build(minecraft, entry.server, 2);
            if (entry.externalPreview == null) {
               return false;
            } else {
               SodiumLevelCache.markPreview(entry.externalPreview.level, entry.externalPreview.spawn);
               if (entry.renderProperties == null) {
                  entry.renderProperties = new WorldPreviewProperties(
                     entry.externalPreview.level,
                     entry.externalPreview.listener,
                     entry.externalPreview.gameMode,
                     entry.externalPreview.player,
                     entry.externalPreview.spawn
                  );
               }

               if (entry.displayTarget == null) {
                  entry.displayTarget = WorldPreview.createTarget(
                     minecraft, "seedqueue card " + entry.number, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()
                  );
               }

               WorldPreview.activateExternal(entry.renderProperties);
               entry.externalPreview.activateSections();
               WorldPreview.prepareExternal(minecraft);
               WorldPreview.forceLevelRender = true;
               PreviewState.bind(entry);
               PreviewState.frameSwapDwellUntil = Long.MAX_VALUE;
               LOGGER.info("[seedqueue] prepared preview for hand-off of #{}", entry.number);
               return true;
            }
         } catch (Throwable error) {
            LOGGER.warn("[seedqueue] failed to prepare preview for #{}", entry.number, error);
            return false;
         }
      }
   }
}
