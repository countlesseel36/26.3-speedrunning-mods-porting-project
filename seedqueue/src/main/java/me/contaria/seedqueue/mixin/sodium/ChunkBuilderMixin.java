package me.contaria.seedqueue.mixin.sodium;

import me.contaria.seedqueue.SodiumLevelCache;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Guards the chunk builder shutdown and limits preview builders to a few threads. */
@Mixin(ChunkBuilder.class)
public abstract class ChunkBuilderMixin {
   @org.spongepowered.asm.mixin.Shadow
   @org.spongepowered.asm.mixin.Final
   private java.util.List<Thread> threads;

   @Unique
   private boolean seedqueue$shutdown;

   @Inject(method = "<init>", at = @At("TAIL"))
   private void seedqueue$chunkThreadPriority(
      net.minecraft.client.multiplayer.ClientLevel level,
      net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType vertexType,
      CallbackInfo ci
   ) {
      int priority = Math.max(1, Math.min(10, me.contaria.seedqueue.QueueConfig.chunkUpdateThreadPriority));

      for (Thread thread : this.threads) {
         try {
            thread.setPriority(priority);
         } catch (Throwable ignored) {
         }
      }
   }

   @Inject(method = "shutdown", at = @At("HEAD"), cancellable = true)
   private void seedqueue$guardShutdown(CallbackInfo ci) {
      if (this.seedqueue$shutdown) {
         ci.cancel();
         return;
      }

      this.seedqueue$shutdown = true;
   }

   @Inject(method = "getThreadCount", at = @At("RETURN"), cancellable = true)
   private static void seedqueue$limitPreviewThreads(CallbackInfoReturnable<Integer> cir) {
      int limit = SodiumLevelCache.threadLimit();

      if (limit > 0) {
         cir.setReturnValue(Math.min(cir.getReturnValue(), limit));
      }
   }
}
