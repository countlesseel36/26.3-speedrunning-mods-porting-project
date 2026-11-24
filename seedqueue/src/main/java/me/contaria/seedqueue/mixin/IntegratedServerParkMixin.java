package me.contaria.seedqueue.mixin;

import java.util.function.BooleanSupplier;
import me.contaria.seedqueue.queue.QueueEntry;
import me.contaria.seedqueue.queue.QueueServer;
import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

@Mixin({IntegratedServer.class})
public abstract class IntegratedServerParkMixin {
   @Inject(
      method = {"tickServer"},
      at = {@At("HEAD")}
   )
   private void seedqueue$parkWhileQueued(BooleanSupplier booleanSupplier, CallbackInfo callback) {
      QueueEntry entry = ((QueueServer)this).seedqueue$getEntry();
      if (entry != null) {
         boolean flag = entry.parked;
         entry.awaitUnpark();
         if (flag) {
            SeedQueue.resetTickTiming(this);
         }
      }
   }
}
