package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 诊断：-Dseedqueue.inventoryDebug=true 时记录客户端收到的容器全量/增量同步，
 * 用于判断“服务端已合并但客户端不显示”时同步包是否到达客户端。
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientContainerDebugMixin {
   @Inject(method = "handleContainerContent", at = @At("TAIL"))
   private void seedqueue$debugContent(ClientboundContainerSetContentPacket packet, CallbackInfo callback) {
      if (!Boolean.getBoolean("seedqueue.inventoryDebug")) {
         return;
      }

      var minecraft = Minecraft.getInstance();
      var player = minecraft.player;

      if (player == null) {
         return;
      }

      var menu = packet.containerId() == 0 ? player.inventoryMenu : player.containerMenu;
      var inventory = player.getInventory();
      StringBuilder slots = new StringBuilder();

      for (int i = 0; i < inventory.getContainerSize() && slots.length() < 200; i++) {
         ItemStack stack = inventory.getItem(i);

         if (!stack.isEmpty()) {
            slots.append(i).append('=').append(stack.getItem()).append('x').append(stack.getCount()).append(' ');
         }
      }

      var preview = me.voidxwalker.worldpreview.WorldPreview.properties();

      SeedQueue.LOGGER.info(
         "[seedqueue] client content id={} state={} menuState={} size={} playerId={} previewPlayer={} mismatch={} inv=({})",
         packet.containerId(),
         packet.stateId(),
         menu.getStateId(),
         menu.slots.size(),
         player.getId(),
         preview != null && preview.player != null && preview.player == player,
         packet.containerId() != 0 && packet.containerId() != player.containerMenu.containerId,
         slots
      );
   }

   @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
   private void seedqueue$debugSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo callback) {
      if (!Boolean.getBoolean("seedqueue.inventoryDebug")) {
         return;
      }

      var minecraft = Minecraft.getInstance();
      var player = minecraft.player;

      if (player == null) {
         return;
      }

      var menu = packet.getContainerId() == 0 ? player.inventoryMenu : player.containerMenu;

      boolean applies = packet.getContainerId() == 0 || packet.getContainerId() == player.containerMenu.containerId;
      var preview = me.voidxwalker.worldpreview.WorldPreview.properties();

      SeedQueue.LOGGER.info(
         "[seedqueue] client slot id={} slot={} state={} menuState={} item={}x{} playerId={} previewPlayer={} applies={}",
         packet.getContainerId(),
         packet.getSlot(),
         packet.getStateId(),
         menu.getStateId(),
         packet.getItem().getItem(),
         packet.getItem().getCount(),
         player.getId(),
         preview != null && preview.player != null && preview.player == player,
         applies
      );
   }
}
