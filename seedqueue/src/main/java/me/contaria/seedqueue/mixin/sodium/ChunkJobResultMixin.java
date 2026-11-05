package me.contaria.seedqueue.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drops a chunk build result that failed because of a native allocation failure.
 *
 * <p>Sodium turns any exception thrown by a build task into a failed {@link ChunkJobResult}, and then
 * rethrows it on the render thread in {@code unwrap()} while collecting the results - which would
 * crash the game right after we avoided the access violation. Returning {@code null} here lets the
 * collector skip that one section instead (see {@code RenderSectionManagerMixin}).</p>
 */
@Mixin(ChunkJobResult.class)
public abstract class ChunkJobResultMixin {
   @Shadow
   private Throwable throwable;

   @Inject(method = "unwrap", at = @At("HEAD"), cancellable = true)
   private void seedqueue$dropNativeAllocationFailure(CallbackInfoReturnable<Object> cir) {
      if (this.throwable instanceof me.contaria.seedqueue.NativeAllocationException) {
         cir.setReturnValue(null);
      }
   }
}
