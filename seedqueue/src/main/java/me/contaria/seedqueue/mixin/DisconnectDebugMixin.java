package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostic: logs why the client leaves a world (stack trace points at the caller). */
@Mixin(Minecraft.class)
public class DisconnectDebugMixin {
   @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
   private void seedqueue$logDisconnect(Screen screen, boolean save, boolean showScreen, CallbackInfo ci) {
      if (Boolean.getBoolean("seedqueue.disconnectLog")) {
         SeedQueue.LOGGER.warn("[seedqueue] client disconnect requested (screen={}, save={})", screen, save, new Throwable("disconnect"));
      }
   }

   @Inject(method = "disconnectFromWorld(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
   private void seedqueue$logDisconnectFromWorld(net.minecraft.network.chat.Component reason, CallbackInfo ci) {
      if (Boolean.getBoolean("seedqueue.disconnectLog")) {
         SeedQueue.LOGGER.warn(
            "[seedqueue] client disconnectFromWorld: {}", reason.getString(), new Throwable("disconnectFromWorld")
         );
      }
   }
}
