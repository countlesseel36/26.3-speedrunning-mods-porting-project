package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.Minecraft;
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
public class SeedQueueMixin {
   @Inject(
      method = {"tick"},
      at = {@At("HEAD")}
   )
   private void seedqueue$tick(CallbackInfo callback) {
      if (SeedQueue.ENABLED) {
         SeedQueue.tick(((Minecraft)(Object)this));
      }
   }

   @Inject(
      method = {"renderFrame"},
      at = {@At("RETURN")}
   )
   private void seedqueue$afterRenderFrame(boolean flag, CallbackInfo callback) {
      SeedQueue.afterRenderFrame(((Minecraft)(Object)this));
   }

   @Inject(
      method = {"close"},
      at = {@At("HEAD")},
      require = 0
   )
   private void seedqueue$saveOnClose(CallbackInfo callback) {
      try {
         SeedQueue.saveAllSettings();
      } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }
   }

   @Inject(
      method = {"close"},
      at = {@At("HEAD")}
   )
   private void seedqueue$close(CallbackInfo callback) {
      SeedQueue.shutdown();
   }
}
