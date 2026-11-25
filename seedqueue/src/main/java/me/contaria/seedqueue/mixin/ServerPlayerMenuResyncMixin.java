package me.contaria.seedqueue.mixin;

import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 打开任意容器后，在下一个服务端 tick 再广播一次完整菜单状态。
 * 快速加入队列世界时客户端偶尔只收到增量槽位更新，导致“同类物品不合并/移入不合并”的显示错乱。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMenuResyncMixin {
   @Inject(method = "openMenu", at = @At("TAIL"))
   private void seedqueue$resyncOpenedMenu(MenuProvider provider, CallbackInfoReturnable<OptionalInt> callback) {
      if (!callback.getReturnValue().isPresent()
         || !Boolean.parseBoolean(System.getProperty("seedqueue.inventoryResync", "true"))) {
         return;
      }

      ServerPlayer self = (ServerPlayer) (Object) this;
      var server = Minecraft.getInstance().getSingleplayerServer();

      try {
         if (server == null) {
            return;
         }

         server.execute(() -> {
            try {
               self.containerMenu.broadcastFullState();
            } catch (Throwable ignored) {
            }
         });
      } catch (Throwable ignored) {
      }
   }
}
