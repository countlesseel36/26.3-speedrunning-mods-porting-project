package me.contaria.seedqueue.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium's {@code renderLayer} throws "Global terrain uniforms have not been updated" when a frame
 * renders terrain without {@code setupTerrain} (this happens while a level hand-over or a quit is
 * transitioning). Skipping that one layer is harmless and avoids a hard crash.
 */
@Mixin(SodiumWorldRenderer.class)
public abstract class SodiumRenderLayerMixin {
   @Shadow
   private ClientLevel level;

   @Shadow
   private UniformBufferManager uniformBufferManager;

   @Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true)
   private void seedqueue$skipStaleTerrain(CallbackInfo ci) {
      if (this.level == null || this.uniformBufferManager == null) {
         ci.cancel();
      }
   }
}
