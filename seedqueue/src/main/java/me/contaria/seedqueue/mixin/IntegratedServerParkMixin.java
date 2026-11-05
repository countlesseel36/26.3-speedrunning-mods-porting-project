package me.contaria.seedqueue.mixin;

import java.util.function.BooleanSupplier;
import me.contaria.seedqueue.QueueEntry;
import me.contaria.seedqueue.QueueServer;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freezes a queued instance once it finished loading: the background server thread blocks at the
 * start of every tick, so the world stays fully loaded and in memory while using no CPU. Selecting
 * the entry releases it, which is what allows the instant hand-off.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerParkMixin {
   @Inject(method = "tickServer", at = @At("HEAD"))
   private void seedqueue$parkWhileQueued(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
      QueueEntry entry = ((QueueServer)this).seedqueue$getEntry();

      if (entry != null) {
         entry.awaitUnpark();
      }
   }
}
