package me.voidxwalker.worldpreview.mixin;

import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures preview frames at the start of a frame (before the new frame's render passes). */
@Mixin(Minecraft.class)
public class MinecraftFrameTailMixin {
   @Inject(method = "renderFrame(Z)V", at = @At("HEAD"))
   private void worldpreview$captureAtFrameStart(boolean advanceGameTime, CallbackInfo ci) {
      WorldPreview.captureFrame((Minecraft) (Object) this);
   }
}
