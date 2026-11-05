package me.contaria.seedqueue.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import me.contaria.seedqueue.QueueConfig;
import me.contaria.seedqueue.WallScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Caps the frame rate while the wall is open (1.21.4 WallFpsMixin / wallFPS option). */
@Mixin(FramerateLimitTracker.class)
public class WallFpsMixin {
   @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
   private void seedqueue$wallFps(CallbackInfoReturnable<Integer> cir) {
      if (Minecraft.getInstance().gui.screen() instanceof WallScreen) {
         cir.setReturnValue(Math.max(1, QueueConfig.wallFps));
      }
   }
}
