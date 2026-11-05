package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the per-card preview textures at the current window size. */
@Mixin(GameRenderer.class)
public class GameRendererResizeMixin {
   @Inject(method = "resize", at = @At("TAIL"))
   private void seedqueue$resizePreviewTargets(int width, int height, CallbackInfo ci) {
      SeedQueue.onWindowResize(width, height);
   }
}
