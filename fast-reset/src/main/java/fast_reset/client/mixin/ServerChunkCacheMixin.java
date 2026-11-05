package fast_reset.client.mixin;

import fast_reset.client.FastReset;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code ServerChunkCache#close()} saves every chunk before closing. Skipped while quitting
 * without saving.
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {
   @Inject(method = "save(Z)V", at = @At("HEAD"), cancellable = true)
   private void fastreset$skipSave(boolean flushStorage, CallbackInfo ci) {
      if (!FastReset.saveOnQuit) {
         ci.cancel();
      }
   }
}
