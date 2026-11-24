package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.queue.QueueEntry;
import me.contaria.seedqueue.queue.QueueServer;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

@Mixin({IntegratedServer.class})
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
