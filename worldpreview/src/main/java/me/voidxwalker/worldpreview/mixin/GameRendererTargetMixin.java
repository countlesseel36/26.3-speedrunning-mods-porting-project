package me.voidxwalker.worldpreview.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets SeedQueue redirect level rendering into a per-instance render target. */
@Mixin(GameRenderer.class)
public class GameRendererTargetMixin {
   private static final java.util.concurrent.atomic.AtomicInteger HITS = new java.util.concurrent.atomic.AtomicInteger();

   @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
   private void worldpreview$overrideTarget(CallbackInfoReturnable<RenderTarget> cir) {
      RenderTarget override = WorldPreview.targetOverride();

      if (override != null) {
         if (HITS.getAndIncrement() < 5) {
            WorldPreview.LOGGER.info("[preview] mainRenderTarget overridden for instance render");
         }

         cir.setReturnValue(override);
      }
   }
}