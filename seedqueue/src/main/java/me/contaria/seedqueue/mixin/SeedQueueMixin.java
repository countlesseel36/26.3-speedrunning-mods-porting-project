package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drives the SeedQueue background work from the client tick. */
@Mixin(Minecraft.class)
public class SeedQueueMixin {
   @Inject(method = "tick", at = @At("HEAD"))
   private void seedqueue$tick(CallbackInfo ci) {
      if (SeedQueue.ENABLED) {
         SeedQueue.tick((Minecraft) (Object) this);
      }
   }

   @Inject(method = "renderFrame", at = @At("RETURN"))
   private void seedqueue$afterRenderFrame(boolean advanceGameTime, CallbackInfo ci) {
      SeedQueue.afterRenderFrame((Minecraft) (Object) this);
   }

   @Inject(method = "renderFrame", at = @At("HEAD"))
   private void seedqueue$renderInstances(boolean advanceGameTime, CallbackInfo ci) {
      SeedQueue.renderInstances((Minecraft) (Object) this);
   }

   @Inject(method = "close", at = @At("HEAD"))
   private void seedqueue$close(CallbackInfo ci) {
      SeedQueue.shutdown();
   }
}
