package me.contaria.seedqueue;

/**
 * Raised when Sodium cannot allocate the native vertex buffer for a chunk build.
 *
 * <p>Without this the failed {@code realloc} would hand Sodium a {@code MemorySegment} at address 0
 * and the first vertex store would kill the JVM with an access violation. The exception is swallowed
 * at the chunk build job boundary (see {@code ChunkJobResultMixin}), so the only consequence is that
 * the affected chunk section is not compiled this time and is retried later.</p>
 */
public class NativeAllocationException extends RuntimeException {
   public NativeAllocationException(String message) {
      super(message);
   }
}
