package me.contaria.seedqueue.mixin.sodium;

import me.contaria.seedqueue.SeedQueue;

import java.util.List;
import me.contaria.seedqueue.config.QueueConfig;
import me.contaria.seedqueue.render.SodiumLevelCache;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ChunkBuilder.class})
public abstract class ChunkBuilderMixin {
   @Shadow
   @Final
   private List<Thread> threads;
   @Unique
   private boolean seedqueue$shutdown;

   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")}
   )
   private void seedqueue$chunkThreadPriority(ClientLevel level, ChunkVertexType chunkVertexType, CallbackInfo callback) {
      int count = Math.max(1, Math.min(10, QueueConfig.chunkUpdateThreadPriority));

      for (Thread thread : this.threads) {
         try {
            thread.setPriority(count);
         } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }
      }
   }

   @Inject(
      method = {"shutdown"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void seedqueue$guardShutdown(CallbackInfo callback) {
      if (this.seedqueue$shutdown) {
         callback.cancel();
      } else {
         this.seedqueue$shutdown = true;
      }
   }

   @Inject(
      method = {"getThreadCount"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private static void seedqueue$limitPreviewThreads(CallbackInfoReturnable<Integer> callback) {
      int count = SodiumLevelCache.threadLimit();
      if (count > 0) {
         callback.setReturnValue(Math.min((Integer)callback.getReturnValue(), count));
      }
   }
}
