package me.voidxwalker.worldpreview;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Holds the fake client world/player used for the preview render pass. */
public final class WorldPreviewProperties {
   public final ClientLevel level;
   public final net.minecraft.client.multiplayer.ClientPacketListener listener;
   public final net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode;
   public final LocalPlayer player;
   /** Optional camera anchor used for third-person previews (invisible marker entity). */
   public net.minecraft.world.entity.Entity cameraEntity;

   public ClientLevel savedLevel;
   public LocalPlayer savedPlayer;
   public net.minecraft.world.entity.Entity savedCameraEntity;
   public net.minecraft.client.CameraType savedCameraType;
   public net.minecraft.client.multiplayer.MultiPlayerGameMode savedGameMode;
   public BlockPos spawnPos;
   public boolean rendering;

   public WorldPreviewProperties(ClientLevel level, net.minecraft.client.multiplayer.ClientPacketListener listener, net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode, LocalPlayer player, BlockPos spawnPos) {
      this.level = level;
      this.listener = listener;
      this.gameMode = gameMode;
      this.player = player;
      this.spawnPos = spawnPos;
   }

   public WorldPreviewProperties withCamera(net.minecraft.world.entity.Entity camera) {
      this.cameraEntity = camera;
      return this;
   }

   public Vec3 spawn() {
      return Vec3.atCenterOf(this.spawnPos);
   }
}
