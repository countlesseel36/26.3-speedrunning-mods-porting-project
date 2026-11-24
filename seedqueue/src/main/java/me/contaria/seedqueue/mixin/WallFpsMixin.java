package me.contaria.seedqueue.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import me.contaria.seedqueue.config.QueueConfig;
import me.contaria.seedqueue.wall.WallScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

@Mixin({FramerateLimitTracker.class})
public class WallFpsMixin {
   @Inject(
      method = {"getFramerateLimit"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void seedqueue$wallFps(CallbackInfoReturnable<Integer> callback) {
      if (Minecraft.getInstance().gui.screen() instanceof WallScreen) {
         callback.setReturnValue(Math.max(1, QueueConfig.wallFps));
      }
   }
}
