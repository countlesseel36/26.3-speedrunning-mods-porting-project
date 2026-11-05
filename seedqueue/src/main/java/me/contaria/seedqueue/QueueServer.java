package me.contaria.seedqueue;

/** Implemented on {@link net.minecraft.client.server.IntegratedServer} by a mixin. */
public interface QueueServer {
   QueueEntry seedqueue$getEntry();

   void seedqueue$setEntry(QueueEntry entry);
}