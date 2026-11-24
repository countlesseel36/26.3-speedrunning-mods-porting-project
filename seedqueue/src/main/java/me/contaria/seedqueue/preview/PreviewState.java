package me.contaria.seedqueue.preview;
import me.contaria.seedqueue.SeedQueue;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;

public final class PreviewState {
   public static QueueEntry frameSwapEntry;
   public static long frameSwapDwellUntil;
   /** 预览装配时的帧号：抓帧必须等到下一次完整渲染之后。 */
   public static QueueEntry handoffPreviewEntry;
   public static QueueEntry activePreviewEntry;
   public static int renderDistance = -1;

   private PreviewState() {
   }

   public static QueueEntry current() {
      return frameSwapEntry;
   }

   public static void unbind() {
      frameSwapEntry = null;
      renderDistance = -1;
   }

   public static void bind(QueueEntry entry) {
      frameSwapEntry = entry;
      renderDistance = entry == null ? -1 : SeedQueue.previewRadius(entry) + 2;
   }
}
