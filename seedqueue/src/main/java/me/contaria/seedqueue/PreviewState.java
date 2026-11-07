package me.contaria.seedqueue;

/**
 * Mutable state of the wall preview: which entry is currently swapped into the client, which one is
 * being handed off, and the render distance to use while a preview is bound.
 *
 * <p>Split out of {@link SeedQueue} so the preview lifecycle has a single home instead of being
 * spread over a dozen static fields in the queue class. Everything here is expected to be touched
 * only from the render thread.</p>
 */
public final class PreviewState {
   /** Entry currently swapped into the client as the rendered level, or {@code null}. */
   static QueueEntry frameSwapEntry;
   /** Wall time until which the current entry keeps the frame (Long.MAX_VALUE = hand-off). */
   static long frameSwapDwellUntil;
   /** Entry being prepared for a join hand-off. */
   static QueueEntry handoffPreviewEntry;
   /** Entry used for the live preview behind the wall. */
   static QueueEntry activePreviewEntry;
   /** Render distance for preview levels, or -1 when the real level is rendered. */
   public static int renderDistance = -1;

   private PreviewState() {
   }

   public static QueueEntry current() {
      return frameSwapEntry;
   }

   public static boolean hasPreview() {
      return frameSwapEntry != null;
   }

   /** Drops the current preview binding and restores the real render distance. */
   public static void unbind() {
      frameSwapEntry = null;
      renderDistance = -1;
   }

   /** Binds {@code entry} as the rendered preview and limits the render distance to its loaded radius. */
   public static void bind(QueueEntry entry) {
      frameSwapEntry = entry;
      renderDistance = entry == null ? -1 : SeedQueue.previewRadius(entry) + 2;
   }
}
