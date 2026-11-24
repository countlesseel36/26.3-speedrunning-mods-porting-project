package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

@Mixin({Minecraft.class})
public class DisconnectDebugMixin {
   @Inject(
      method = {"disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V"},
      at = {@At("HEAD")}
   )
   private void seedqueue$logDisconnect(Screen screen, boolean flag, boolean flag2, CallbackInfo callback) {
      if (Boolean.getBoolean("seedqueue.disconnectLog")) {
         SeedQueue.LOGGER.warn("[seedqueue] client disconnect requested (screen={}, save={})", screen, flag, new Throwable("disconnect"));
      }
   }

   @Inject(
      method = {"disconnectFromWorld(Lnet/minecraft/network/chat/Component;)V"},
      at = {@At("HEAD")}
   )
   private void seedqueue$logDisconnectFromWorld(Component component, CallbackInfo callback) {
      if (Boolean.getBoolean("seedqueue.disconnectLog")) {
         SeedQueue.LOGGER.warn("[seedqueue] client disconnectFromWorld: {}", component.getString(), new Throwable("disconnectFromWorld"));
      }
   }
}
