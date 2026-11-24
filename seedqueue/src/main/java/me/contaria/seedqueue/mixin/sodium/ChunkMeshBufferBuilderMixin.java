package me.contaria.seedqueue.mixin.sodium;

import me.contaria.seedqueue.queue.NativeAllocationException;
import me.contaria.seedqueue.SeedQueue;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({ChunkMeshBufferBuilder.class})
public abstract class ChunkMeshBufferBuilderMixin {
   @Unique
   private static boolean seedqueue$warnedOutOfNativeMemory;
   @Unique
   private static final int SIMULATE_FAILURE_AT = Integer.getInteger("seedqueue.simulateNativeOom", 0);
   @Unique
   private static int seedqueue$allocations;

   @Redirect(
      method = {"reallocate"},
      at = {@At(
         value = "INVOKE",
         target = "Lorg/lwjgl/system/MemoryUtil;nmemRealloc(JJ)J"
      )}
   )
   private static long seedqueue$failFastWhenNativeAllocationFails(long value, long value2) {
      boolean flag = SIMULATE_FAILURE_AT > 0 && ++seedqueue$allocations == SIMULATE_FAILURE_AT;
      long value3 = flag ? 0L : MemoryUtil.nmemRealloc(value, value2);
      if (value3 == 0L) {
         if (!seedqueue$warnedOutOfNativeMemory) {
            seedqueue$warnedOutOfNativeMemory = true;
            SeedQueue.LOGGER
               .error("[seedqueue] sodium could not allocate {} bytes of native vertex memory; failing the chunk build instead of writing to address 0", value2);
         }

         throw new NativeAllocationException("Sodium native vertex allocation failed (out of native memory)");
      } else {
         return value3;
      }
   }
}
