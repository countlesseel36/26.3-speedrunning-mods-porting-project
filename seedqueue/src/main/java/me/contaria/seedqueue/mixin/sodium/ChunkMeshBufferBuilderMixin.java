package me.contaria.seedqueue.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Turns Sodium's fatal native allocation failure into a normal Java exception.
 *
 * <p>{@code ChunkMeshBufferBuilder#reallocate} stores whatever {@code MemoryUtil.nmemRealloc}
 * returns in a {@code MemorySegment} and then writes vertex data through
 * {@code buffer.address()}. When the native allocation fails (arena/heap exhaustion, which happens
 * while the wall is rebuilding many preview renderers in a row) that address is {@code 0} and the
 * very next vertex store touches the null page: the JVM dies with
 * {@code EXCEPTION_ACCESS_VIOLATION ... in BlockRenderer.bufferQuad} instead of throwing.</p>
 *
 * <p>Sodium already catches {@code Throwable} per chunk build job
 * ({@code ChunkJobTyped#execute} logs "Chunk build failed"), so raising a normal exception here
 * keeps the game alive: only that one section fails to compile.</p>
 */
@Mixin(ChunkMeshBufferBuilder.class)
public abstract class ChunkMeshBufferBuilderMixin {
   @Unique
   private static boolean seedqueue$warnedOutOfNativeMemory;

   /** {@code -Dseedqueue.simulateNativeOom=N} fails the Nth native vertex allocation once, to test the recovery path. */
   @Unique
   private static final int SIMULATE_FAILURE_AT = Integer.getInteger("seedqueue.simulateNativeOom", 0);

   @Unique
   private static int seedqueue$allocations;

   @Redirect(
      method = "reallocate",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/system/MemoryUtil;nmemRealloc(JJ)J")
   )
   private static long seedqueue$failFastWhenNativeAllocationFails(long address, long size) {
      boolean simulated = SIMULATE_FAILURE_AT > 0 && ++seedqueue$allocations == SIMULATE_FAILURE_AT;
      long allocated = simulated ? 0L : MemoryUtil.nmemRealloc(address, size);

      if (allocated == 0L) {
         if (!seedqueue$warnedOutOfNativeMemory) {
            seedqueue$warnedOutOfNativeMemory = true;
            me.contaria.seedqueue.SeedQueue.LOGGER.error(
               "[seedqueue] sodium could not allocate {} bytes of native vertex memory; "
                  + "failing the chunk build instead of writing to address 0",
               size
            );
         }

         throw new me.contaria.seedqueue.NativeAllocationException(
            "Sodium native vertex allocation failed (out of native memory)"
         );
      }

      return allocated;
   }
}
