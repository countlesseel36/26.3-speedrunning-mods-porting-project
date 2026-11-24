package me.contaria.seedqueue.mixin.sodium;

import me.contaria.seedqueue.queue.NativeAllocationException;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ChunkJobResult.class})
public abstract class ChunkJobResultMixin {
   @Shadow
   private Throwable throwable;

   @Inject(
      method = {"unwrap"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void seedqueue$dropNativeAllocationFailure(CallbackInfoReturnable<Object> callback) {
      if (this.throwable instanceof NativeAllocationException) {
         callback.setReturnValue(null);
      }
   }
}
