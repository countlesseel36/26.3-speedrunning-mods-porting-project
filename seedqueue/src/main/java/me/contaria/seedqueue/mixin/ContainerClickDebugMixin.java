package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 诊断：-Dseedqueue.inventoryDebug=true 时记录容器点击涉及的物品组件差异，
 * 用于排查“同类物品不合并”是否由组件不同或客户端状态不同步导致。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ContainerClickDebugMixin {
   @Inject(method = "handleContainerClick", at = @At("HEAD"))
   private void seedqueue$debugContainerClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
      if (!Boolean.getBoolean("seedqueue.inventoryDebug")) {
         return;
      }

      try {
         ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
         var menu = self.player.containerMenu;

         if (menu.containerId != packet.containerId() || packet.slotNum() < 0 || packet.slotNum() >= menu.slots.size()) {
            return;
         }

         ItemStack clicked = menu.getSlot(packet.slotNum()).getItem();

         if (clicked.isEmpty()) {
            return;
         }

         SeedQueue.LOGGER.info(
            "[seedqueue] click menu={} stateId={}/{} slot={} button={} input={} carried={}x{} item={} x{} patch={}",
            menu.containerId,
            packet.stateId(),
            menu.getStateId(),
            packet.slotNum(),
            packet.buttonNum(),
            packet.containerInput(),
            menu.getCarried().getItem(),
            menu.getCarried().getCount(),
            clicked.getItem(),
            clicked.getCount(),
            clicked.getComponentsPatch()
         );

         var inventory = self.player.getInventory();

         for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack other = inventory.getItem(slot);

            if (!other.isEmpty() && other.getItem() == clicked.getItem()) {
               SeedQueue.LOGGER.info(
                  "[seedqueue] compare invSlot={} item={} x{} same={} patch={}",
                  slot,
                  other.getItem(),
                  other.getCount(),
                  ItemStack.isSameItemSameComponents(clicked, other),
                  other.getComponentsPatch()
               );
               break;
            }
         }
      } catch (Throwable error) {
         SeedQueue.LOGGER.warn("[seedqueue] container click debug failed", error);
      }
   }

   @Inject(method = "handleContainerClick", at = @At("TAIL"))
   private void seedqueue$debugAfterClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
      if (!Boolean.getBoolean("seedqueue.inventoryDebug")) {
         return;
      }

      try {
         ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
         var menu = self.player.containerMenu;

         if (menu.containerId != packet.containerId() || packet.slotNum() < 0 || packet.slotNum() >= menu.slots.size()) {
            return;
         }

         ItemStack clicked = menu.getSlot(packet.slotNum()).getItem();
         SeedQueue.LOGGER.info(
            "[seedqueue] after slot={} state={} carried={}x{} clicked={}x{}",
            packet.slotNum(),
            menu.getStateId(),
            menu.getCarried().getItem(),
            menu.getCarried().getCount(),
            clicked.getItem(),
            clicked.getCount()
         );

         var inventory = self.player.getInventory();
         int shown = 0;

         for (int slot = 0; slot < inventory.getContainerSize() && shown < 6; slot++) {
            ItemStack other = inventory.getItem(slot);

            if (!other.isEmpty() && other.getItem() == clicked.getItem()) {
               SeedQueue.LOGGER.info("[seedqueue] after invSlot={} x{}", slot, other.getCount());
               shown++;
            }
         }
      } catch (Throwable error) {
         SeedQueue.LOGGER.warn("[seedqueue] container after-click debug failed", error);
      }
   }
}
