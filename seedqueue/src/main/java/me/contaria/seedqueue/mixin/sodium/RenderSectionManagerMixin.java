package me.contaria.seedqueue.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes {@link RenderSectionManager#destroy()} idempotent so cached managers can be reused safely,
 * and drops chunk build results that failed on a native allocation.
 */
@Mixin(RenderSectionManager.class)
public abstract class RenderSectionManagerMixin {
   @Unique
   private boolean seedqueue$destroyed;

   @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
   private void seedqueue$guardDestroy(CallbackInfo ci) {
      if (this.seedqueue$destroyed) {
         ci.cancel();
         return;
      }

      this.seedqueue$destroyed = true;
   }

   /** Skips build results that failed on a native allocation instead of unwrapping them. */
   @Redirect(
      method = "collectChunkBuildResults",
      at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;add(Ljava/lang/Object;)Z")
   )
   private boolean seedqueue$skipFailedBuildResult(java.util.ArrayList<Object> results, Object output) {
      return output != null && results.add(output);
   }
}
