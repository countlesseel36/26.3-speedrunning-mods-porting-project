package fast_reset.client.mixin;

import fast_reset.client.FastReset;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a save-less quit is in progress every server-side save is skipped, no matter which caller
 * triggers it (quit button, abort during loading, shutdown hook...).
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerSaveMixin {
   @Inject(method = "saveAllChunks(ZZZ)Z", at = @At("HEAD"), cancellable = true)
   private void fastreset$skipAllChunks(boolean silent, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
      if (!FastReset.saveOnQuit) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "saveEverything(ZZZ)Z", at = @At("HEAD"), cancellable = true)
   private void fastreset$skipEverything(boolean silent, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
      if (!FastReset.saveOnQuit) {
         cir.setReturnValue(true);
      }
   }
}