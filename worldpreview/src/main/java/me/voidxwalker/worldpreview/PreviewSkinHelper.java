package me.voidxwalker.worldpreview;

import com.mojang.authlib.GameProfile;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.ItemActivation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * 预览玩家的皮肤：应用真实玩家 profile / PlayerInfo。
 * 从 ExternalPreview 拆分出来，行为保持不变。
 */
final class PreviewSkinHelper {
   private PreviewSkinHelper() {
   }

private static java.lang.reflect.Field playerInfoField;
private static boolean playerInfoResolved;

   static void applyRealSkin(Minecraft minecraft, net.minecraft.client.player.LocalPlayer player) {
      try {
         var real = minecraft.getGameProfile();

         if (real == null || player == null) {
            return;
         }

         var connection = minecraft.getConnection();
         PlayerInfo info = connection == null ? null : connection.getPlayerInfo(real.id());
         boolean synthetic = false;

         if (info == null) {
            // 未连接服务器（墙上的预览即如此）时没有 PlayerInfo，
            // 用真实 profile 构造一个，让 SkinManager 能解析出玩家皮肤而不是默认皮肤。
            info = new PlayerInfo(real, false);
            synthetic = true;
         }

         if (!playerInfoResolved) {
            playerInfoResolved = true;

            for (Class<?> type = player.getClass(); type != null; type = type.getSuperclass()) {
               try {
                  java.lang.reflect.Field field = type.getDeclaredField("playerInfo");
                  field.setAccessible(true);
                  playerInfoField = field;
                  break;
               } catch (NoSuchFieldException ignored) {
               }
            }
         }

         if (playerInfoField != null) {
            playerInfoField.set(player, info);

         } else {
            WorldPreview.LOGGER.warn("[preview] playerInfo field not found, preview keeps the default skin");
         }
      } catch (Throwable t) {
         WorldPreview.LOGGER.warn("[preview] could not apply the real player skin", t);
      }
   }

   static GameProfile previewProfile(Minecraft minecraft) {
      try {
         var real = minecraft.getGameProfile();

         if (real != null) {
            var connection = minecraft.getConnection();

            if (connection != null) {
               var info = connection.getPlayerInfo(real.id());

               if (info != null && info.getProfile().properties().containsKey("textures")) {
                  return info.getProfile();
               }
            }

            return real;
         }
      } catch (Throwable t) {
         WorldPreview.LOGGER.warn("[preview] could not read the local game profile", t);
      }

      return new GameProfile(UUID.nameUUIDFromBytes("worldpreview".getBytes()), "Preview");
   }
}
