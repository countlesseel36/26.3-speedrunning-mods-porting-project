package me.contaria.seedqueue.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SodiumWorldRenderer.class})
public abstract class SodiumRenderLayerMixin {
   @Shadow
   private ClientLevel level;
   @Shadow
   private UniformBufferManager uniformBufferManager;

   @Inject(
      method = {"renderLayer"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void seedqueue$skipStaleTerrain(CallbackInfo callback) {
      if (this.level == null || this.uniformBufferManager == null) {
         callback.cancel();
      }
   }
}
