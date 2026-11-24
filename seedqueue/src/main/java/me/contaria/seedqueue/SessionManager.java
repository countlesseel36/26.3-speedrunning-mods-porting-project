package me.contaria.seedqueue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.gamerules.GameRules;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;

import static me.contaria.seedqueue.SeedQueue.*;

/**
 * 会话管理：加入/离开世界的交接、倒计时、出生点对齐与重置状态。
 * 
 * 处理“从预览过渡到实际世界”的阶段，并维护 liveEntry、重置请求等会话级状态。
 */
public final class SessionManager {
   private SessionManager() {
   }

   /**
    * 进入世界后的交接推进（预览到实际世界的过渡）。
    */
   static void handoffTick(Minecraft minecraft) {
      if (PreviewState.handoffPreviewEntry != null) {
         handoffTicks++;
         if (handoffTicks < 100 && minecraft.gui.screen() instanceof LevelLoadingScreen) {
            minecraft.gui.setScreen(null);
         }

         ClientLevel level = PreviewState.handoffPreviewEntry.externalPreview == null ? null : PreviewState.handoffPreviewEntry.externalPreview.level;
         LocalPlayer localPlayer = PreviewState.handoffPreviewEntry.externalPreview == null ? null : PreviewState.handoffPreviewEntry.externalPreview.player;
         if (PreviewState.handoffPreviewEntry.renderProperties != null && PreviewState.handoffPreviewEntry.renderProperties.player != null) {
            localPlayer = PreviewState.handoffPreviewEntry.renderProperties.player;
         }

         boolean flag = minecraft.player != null && minecraft.player != localPlayer;
         boolean flag2 = minecraft.level != null && minecraft.player != null && (minecraft.level != level || flag);
         if (flag2 || handoffTicks > 200) {
            WorldPreview.releasePreviewCameraAndEntities(minecraft);
            WorldPreview.keepSodiumBound = false;
            if (WorldPreview.isActive()) {
               WorldPreview.deactivateExternal();
            }

            WorldPreview.rebindRealLevel(minecraft);
            PreviewState.renderDistance = -1;
            if (minecraft.gui.screen() instanceof LevelLoadingScreen) {
               minecraft.gui.setScreen(null);
            }

            /**
             * 把玩家对齐到预览展示的出生点。
             */
            alignPlayerToPreviewedSpawn(PreviewState.handoffPreviewEntry, minecraft);
            LOGGER.info("[seedqueue] hand-off complete after {} ticks (preview released)", handoffTicks);
            PreviewState.handoffPreviewEntry = null;
            joinInProgress = false;
            /**
             * 恢复出生点半径设置。
             */
            restoreRespawnRadius(minecraft);
         }
      }
   }
   /**
    * 校准/读取客户端的 tick 计时字段。
    */
   public static void resetTickTiming(Object obj) {
      try {
         if (!nextTickTimeResolved) {
            nextTickTimeResolved = true;

            for (Class clazz = obj.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
               try {
                  Field field = clazz.getDeclaredField("nextTickTimeNanos");
                  field.setAccessible(true);
                  nextTickTimeField = field;
                  break;
               } catch (NoSuchFieldException noSuchFieldException) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", noSuchFieldException);
      }
            }
         }

         if (nextTickTimeField != null) {
            nextTickTimeField.setLong(obj, Util.timeSource().getAsLong());
         }
      } catch (Throwable error) {
         LOGGER.warn("[seedqueue] could not reset the tick timing of an unparked server", error);
      }
   }
   static void restoreRespawnRadius(Minecraft minecraft) {
      try {
         IntegratedServer integratedServer = minecraft.getSingleplayerServer();
         if (integratedServer != null) {
            integratedServer.getGameRules().set(GameRules.RESPAWN_RADIUS, 10, integratedServer);
         }
      } catch (Throwable error) {
         LOGGER.warn("[seedqueue] failed to restore the respawn radius", error);
      }
   }
   static void alignPlayerToPreviewedSpawn(QueueEntry entry, Minecraft minecraft) {
      if (entry != null && entry.server != null && entry.externalPreview != null) {
         BlockPos pos = entry.externalPreview.spawn;
         if (pos != null) {
            try {
               IntegratedServer integratedServer = entry.server;
               integratedServer.execute(
                  () -> {
                     try {
                        for (ServerPlayer serverPlayer : integratedServer.getPlayerList().getPlayers()) {
                           ServerLevel level = integratedServer.overworld();
                           if (serverPlayer.level() == level) {
                              serverPlayer.teleportTo(
                                 level,
                                 (double)pos.getX() + 0.5,
                                 (double)pos.getY(),
                                 (double)pos.getZ() + 0.5,
                                 Collections.emptySet(),
                                 serverPlayer.getYRot(),
                                 serverPlayer.getXRot(),
                                 false
                              );
                              LOGGER.info("[seedqueue] aligned player to previewed spawn {}", pos);
                           }
                        }
                     } catch (Throwable error) {
                        LOGGER.warn("[seedqueue] previewed spawn alignment failed", error);
                     }
                  }
               );
            } catch (Throwable error2) {
               LOGGER.warn("[seedqueue] previewed spawn alignment could not be scheduled", error2);
            }
         }
      }
   }
   /**
    * 客户端世界切换后的会话状态更新。
    */
   public static void onLevelSet() {
      if (Boolean.getBoolean("seedqueue.screenLog")) {
         LOGGER.info("[seedqueue] client level installed, waiting for the player entity");
      }
   }
   /**
    * 是否正在执行重置。
    */
   static boolean isResetInProgress() {
      try {
         Class clazz = Class.forName("me.voidxwalker.autoreset.Atum");
         return clazz.getField("shouldReset").getBoolean(null);
      } catch (Throwable error) {
         return false;
      }
   }
   /**
    * 消费一次重置请求并执行重置。
    */
   public static boolean consumeForReset(Minecraft minecraft) {
      if (!integrate()) {
         return false;
      } else if (!isResetInProgress()) {
         LOGGER.info("[seedqueue] world creation is not a reset, using vanilla");
         return false;
      } else {
         QueueEntry entry = null;

         for (QueueEntry entry2 : ENTRIES) {
            if (entry2.state == QueueEntry.State.READY && !entry2.locked) {
               entry = entry2;
               break;
            }
         }

         if (entry == null) {
            LOGGER.info("[seedqueue] no ready queued world, creating a fresh one");
            return false;
         } else {
            LOGGER.info("[seedqueue] consuming queued entry #{} for the reset (seed {})", entry.number, entry.seed);
            return playEntry(entry);
         }
      }
   }
   /**
    * 重置所有未锁定的实例。
    */
   static int resetAllUnlocked() {
      int count = 0;

      for (QueueEntry entry : new ArrayList<>(ENTRIES)) {
         if (!entry.locked && !entry.transferred) {
            ENTRIES.remove(entry);
            if (PreviewState.frameSwapEntry == entry) {
               PreviewState.unbind();
            }

            discardAsync(entry);
            count++;
         }
      }

      if (count > 0) {
         LOGGER.info("[seedqueue] discarded {} unlocked entries", count);
      }

      return count;
   }
   /**
    * 是否有待处理的“锁定下一个”请求。
    */
   public static boolean isPlayNextLockPending() {
      return pendingPlayNextLock;
   }
   /**
    * 是否正处于界面/世界过渡中。
    */
   public static boolean isTransitionPending() {
      return pendingPlayNextLock || joinInProgress || pendingWallReturn;
   }
   /**
    * 当前已加入世界的实例。
    */
   public static QueueEntry liveEntry() {
      return liveEntry;
   }
}
