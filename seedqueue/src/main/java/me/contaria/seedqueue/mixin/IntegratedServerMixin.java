package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.QueueEntry;
import me.contaria.seedqueue.QueueServer;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Tags each background integrated server with the queue entry it belongs to. */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin implements QueueServer {
   @Unique
   private QueueEntry seedqueue$entry;

   @Override
   public QueueEntry seedqueue$getEntry() {
      return this.seedqueue$entry;
   }

   @Override
   public void seedqueue$setEntry(QueueEntry entry) {
      this.seedqueue$entry = entry;
   }
}