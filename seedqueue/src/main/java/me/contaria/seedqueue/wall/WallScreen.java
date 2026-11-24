package me.contaria.seedqueue.wall;
import me.contaria.seedqueue.SeedQueue;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

public final class WallScreen extends Screen {
   private static final boolean WALL_CLEAR_BACKGROUND = Boolean.getBoolean("seedqueue.wallClearBackground");
   private static final boolean WALL_FULLSCREEN_TARGET = Boolean.getBoolean("seedqueue.wallFullscreenTarget");
   private final Supplier<List<QueueEntry>> entries;
   private List<WallLayout.Slot> cachedMainSlots;
   private int cachedWidth = -1;
   private int cachedHeight = -1;
   private final List<int[]> cardBounds = new ArrayList<>();
   private final List<QueueEntry> cardEntries = new ArrayList<>();
   private final List<int[]> cardRects = new ArrayList<>();
   private final List<Integer> cardGroups = new ArrayList<>();
   private final List<Boolean> cardCosmetic = new ArrayList<>();
   private final List<QueueEntry> mainScratch = new ArrayList<>();
   private final List<QueueEntry> preparingScratch = new ArrayList<>();
   private final List<QueueEntry> lockedScratch = new ArrayList<>();
   private final List<int[]> rectPool = new ArrayList<>();
   private int rectPoolIndex;
   private final List<QueueEntry> lastLockedSnapshot = new ArrayList<>();
   private static QueueEntry hoveredEntry;
   private static volatile long joinSlideStartedAt;
   private int slideOffset;
   private WallLayout layout;
   private WallTextures textures;
   private final long[] debugFrameNanos = new long[120];
   private int debugSampleIndex;
   private long debugLastFrameNanos;
   private static GpuSampler sampler;

   public WallScreen(Supplier<List<QueueEntry>> supplier) {
      super(Component.literal("SeedQueue"));
      this.entries = supplier;
   }

   public boolean isPauseScreen() {
      return false;
   }

   protected void init() {
      super.init();
      this.layout = WallLayout.load(this.minecraft);
      this.textures = WallTextures.load(this.minecraft);
   }

   public static void beginJoinTransition() {
      joinSlideStartedAt = System.currentTimeMillis();
   }

   public boolean keyPressed(KeyEvent keyEvent) {
      if (SeedQueue.isPlayNextLockPending() && !keyEvent.isEscape()) {
         SeedQueue.LOGGER.info("[seedqueue] wall key ignored while play-next-lock is pending");
         return true;
      } else if (keyEvent.isEscape() && WallKeybindings.key(WallKeybindings.Action.QUIT) != 41) {
         return true;
      } else if (QueueConfig.showDebugMenu && keyEvent.key() == 60) {
         QueueEntry entry = hoveredEntry;
         if (entry != null) {
            if ((keyEvent.modifiers() & 1) != 0) {
               SeedQueue.logServerThreadStack(entry);
            } else {
               SeedQueue.logEntryDebug(entry);
            }
         }

         return true;
      } else {
         if (Boolean.getBoolean("seedqueue.keysLog")) {
            SeedQueue.LOGGER
               .info(
                  "[seedqueue] wall key raw={} normalized={} action={} binding.play={}",
                  keyEvent.key(),
                  WallKeybindings.keyOf(keyEvent),
                  WallKeybindings.match(keyEvent),
                  WallKeybindings.binding(WallKeybindings.Action.PLAY).primary
               );
         }

         WallKeybindings.Action wallKeybindingsAction = WallKeybindings.match(keyEvent);
         QueueEntry entry2 = hoveredEntry;
         if (wallKeybindingsAction != null && this.performAction(wallKeybindingsAction, entry2)) {
            return true;
         } else if (keyEvent.isEscape()) {
            this.onClose();
            return true;
         } else {
            return super.keyPressed(keyEvent);
         }
      }
   }

   private boolean performAction(WallKeybindings.Action wallKeybindingsAction, QueueEntry entry) {
      if (wallKeybindingsAction != null) {
         switch (wallKeybindingsAction) {
            case PLAY:
               if (entry != null) {
                  SeedQueue.playEntry(entry);
               }

               return true;
            case LOCK:
               if (entry != null) {
                  this.toggleLock(entry);
               }

               return true;
            case RESET:
               SeedQueue.resetEntry(entry);
               return true;
            case RESET_ALL:
               SeedQueue.playSound("reset_all");
               SeedQueue.resetEntries(this.groupEntries(0));
               return true;
            case RESET_COLUMN:
               if (entry != null) {
                  SeedQueue.playSound("reset_column");
                  SeedQueue.resetEntries(this.resetGroup(0, entry, true));
               }

               return true;
            case RESET_ROW:
               if (entry != null) {
                  SeedQueue.playSound("reset_row");
                  SeedQueue.resetEntries(this.resetGroup(0, entry, false));
               }

               return true;
            case PLAY_NEXT_LOCK:
               SeedQueue.playNextLock(this.minecraft);
               return true;
            case SCHEDULE_JOIN:
               if (entry != null) {
                  entry.locked = true;
                  entry.lockedAt = System.currentTimeMillis();
                  entry.scheduledJoin = true;
                  entry.scheduledJoinAt = System.currentTimeMillis();
                  SeedQueue.playSound("lock_instance");
                  SeedQueue.LOGGER.info("[seedqueue] entry #{} scheduled to join", entry.number);
               }

               return true;
            case SCHEDULE_ALL:
               SeedQueue.scheduleAll();
               return true;
            case START_BENCHMARK:
               SeedQueue.startBenchmark();
         }
      }

      return false;
   }

   private List<QueueEntry> groupEntries(int group) {
      ArrayList entries = new ArrayList();

      for (int i = 0; i < this.cardEntries.size(); i++) {
         if (this.cardGroups.get(i) == group && !this.cardCosmetic.get(i)) {
            entries.add(this.cardEntries.get(i));
         }
      }

      return entries;
   }

   private List<QueueEntry> resetGroup(int group, QueueEntry anchor, boolean horizontal) {
      ArrayList entries = new ArrayList();
      int anchorIndex = this.cardEntries.indexOf(anchor);
      if (anchorIndex < 0) {
         return entries;
      } else {
         int[] anchorRect = this.cardRects.get(anchorIndex);

         for (int i = 0; i < this.cardEntries.size(); i++) {
            if (this.cardGroups.get(i) == group && !this.cardCosmetic.get(i) && !this.cardEntries.get(i).locked) {
               int[] rect = this.cardRects.get(i);
               boolean overlaps = horizontal ? rect[0] < anchorRect[2] && rect[2] > anchorRect[0] : rect[1] < anchorRect[3] && rect[3] > anchorRect[1];
               if (overlaps) {
                  entries.add(this.cardEntries.get(i));
               }
            }
         }

         return entries;
      }
   }

   private void toggleLock(QueueEntry entry) {
      entry.locked = !entry.locked;
      if (entry.locked) {
         entry.lockedAt = System.currentTimeMillis();
         SeedQueue.playSound("lock_instance");
         if (this.layout != null && this.layout.locked != null && this.layout.replaceLockedInstances) {
            SeedQueue.refillQueue(this.minecraft);
         }
      }

      SeedQueue.LOGGER.info("[seedqueue] entry #{} locked={}", entry.number, entry.locked);
   }

   public void onClose() {
      SeedQueue.onWallClosed(this.minecraft);
   }

   public static QueueEntry hoveredEntry() {
      return hoveredEntry;
   }

   public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
      if (SeedQueue.isTransitionPending()) {
         SeedQueue.LOGGER.info("[seedqueue] wall click ignored while a transition is pending");
         return true;
      } else {
         int count = mouseButtonEvent.button();
         if (count != 1 && count != 2 && count != 3) {
            return false;
         } else {
            ArrayList rect = new ArrayList<>(this.cardBounds);
            ArrayList cards = new ArrayList<>(this.cardEntries);

            for (int i = 0; i < rect.size() && i < cards.size(); i++) {
               int[] counts = (int[])rect.get(i);
               if (mouseButtonEvent.x() >= (double)counts[0] && mouseButtonEvent.x() <= (double)counts[2] && mouseButtonEvent.y() >= (double)counts[1] && mouseButtonEvent.y() <= (double)counts[3]) {
                  QueueEntry entry = (QueueEntry)cards.get(i);
                  if (count == 3) {
                     this.toggleLock(entry);
                     return true;
                  }

                  if (count == 2) {
                     SeedQueue.discardEntry(entry);
                     SeedQueue.refillQueue(this.minecraft);
                     return true;
                  }

                  SeedQueue.playEntry(entry);
                  return true;
               }
            }

            return super.mouseClicked(mouseButtonEvent, doubleClick);
         }
      }
   }







   private static GpuSampler sampler() {
      if (sampler == null) {
         sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
      }

      return sampler;
   }

   public void extractRenderState(GuiGraphicsExtractor guiGraphicsExtractor, int count, int count2, float ratio) {
      int width = this.width;
      int height = this.height;
      if (this.layout == null) {
         this.layout = WallLayout.load(this.minecraft);
      }

      if (this.textures == null) {
         this.textures = WallTextures.load(this.minecraft);
      }

      this.slideOffset = 0;
      if (QueueConfig.joinTransition && joinSlideStartedAt != 0L) {
         float now = Math.min(1.0F, (float)(System.currentTimeMillis() - joinSlideStartedAt) / 320.0F);
         this.slideOffset = -Math.round(now * (float)width);
         if (now >= 1.0F) {
            joinSlideStartedAt = 0L;
         }
      }

      this.cardBounds.clear();
      this.cardEntries.clear();
      this.cardRects.clear();
      this.cardGroups.clear();
      this.cardCosmetic.clear();
      this.rectPoolIndex = 0;
      hoveredEntry = null;
      if (!WALL_CLEAR_BACKGROUND) {
         if (this.textures.background != null) {
            this.textures.background.blit(guiGraphicsExtractor, 0, 0, width, height);
         } else {
            guiGraphicsExtractor.fill(0, 0, width, height, -16777216);
         }
      }

      java.util.ArrayList<QueueEntry> list = new java.util.ArrayList<>(this.entries.get());
      this.mainScratch.clear();
      this.preparingScratch.clear();
      this.lockedScratch.clear();

      for (QueueEntry entry : list) {
         if (this.layout.locked != null && entry.locked) {
            this.lockedScratch.add(entry);
         } else if (!this.layout.preparing.isEmpty() && entry.state != QueueEntry.State.READY) {
            this.preparingScratch.add(entry);
         } else {
            this.mainScratch.add(entry);
         }
      }

      if (this.cachedMainSlots == null || this.cachedWidth != width || this.cachedHeight != height) {
         this.cachedMainSlots = this.layout.main.resolve(width, height);
         this.cachedWidth = width;
         this.cachedHeight = height;
      }

      List list2 = this.cachedMainSlots;

      for (int i = 0; i < list2.size(); i++) {
         WallLayout.Slot wallLayoutSlot = (WallLayout.Slot)list2.get(i);
         QueueEntry entry2 = i < this.mainScratch.size() ? this.mainScratch.get(i) : null;
         this.drawSlot(guiGraphicsExtractor, entry2, wallLayoutSlot, count, count2, 0);
      }

      int count5 = 0;

      for (WallLayout.Group wallLayoutGroup : this.layout.preparing) {
         for (WallLayout.Slot wallLayoutSlot2 : wallLayoutGroup.resolve(width, height)) {
            if (count5 >= this.preparingScratch.size()) {
               break;
            }

            this.drawSlot(guiGraphicsExtractor, this.preparingScratch.get(count5++), wallLayoutSlot2, count, count2, 1);
         }
      }

      if (this.layout.locked != null) {
         int count6 = 0;

         for (WallLayout.Slot wallLayoutSlot3 : this.layout.locked.resolve(width, height)) {
            if (count6 >= this.lockedScratch.size()) {
               break;
            }

            this.drawSlot(guiGraphicsExtractor, this.lockedScratch.get(count6++), wallLayoutSlot3, count, count2, 2);
         }
      }

      if (this.textures.overlay != null) {
         this.textures.overlay.blit(guiGraphicsExtractor, 0, 0, width, height);
      }

      this.drawBenchmarkOverlay(guiGraphicsExtractor);
      if (QueueConfig.showDebugMenu) {
         this.drawDebugOverlay(guiGraphicsExtractor);
      }

      if (WALL_FULLSCREEN_TARGET && !list.isEmpty() && ((QueueEntry)list.get(0)).renderTarget != null) {
         QueueEntry entry3 = (QueueEntry)list.get(0);
         RenderTarget target = entry3.displayTarget != null ? entry3.displayTarget : entry3.renderTarget;
         guiGraphicsExtractor.blit(target.getColorTextureView(), sampler(), 0, 0, width, height, 0.0F, 0.0F, 1.0F, 1.0F);
      }
   }

   private int[] rectAt(int x, int y, int x2, int y2) {
      if (this.rectPoolIndex >= this.rectPool.size()) {
         this.rectPool.add(new int[4]);
      }

      int[] rect = this.rectPool.get(this.rectPoolIndex++);
      rect[0] = x;
      rect[1] = y;
      rect[2] = x2;
      rect[3] = y2;
      return rect;
   }

   private void drawSlot(GuiGraphicsExtractor guiGraphicsExtractor, QueueEntry entry, WallLayout.Slot wallLayoutSlot, int mouseX, int mouseY, int group) {
      int width = wallLayoutSlot.pixelX(this.width) + this.slideOffset;
      int height = wallLayoutSlot.pixelY(this.height);
      int width2 = Math.max(1, wallLayoutSlot.pixelWidth(this.width));
      int height2 = Math.max(1, wallLayoutSlot.pixelHeight(this.height));
      int footerHeight = Math.min(16, Math.max(10, height2 / 8));
      int imageHeight = Math.max(1, height2 - footerHeight);
      int renderHeight = imageHeight;
      int renderWidth = Math.min(width2, Math.max(1, Math.round((float)imageHeight * 16.0F / 9.0F)));
      if (renderWidth > width2) {
         renderHeight = Math.max(1, Math.round((float)width2 * 9.0F / 16.0F));
      }

      if (renderHeight > imageHeight) {
         renderWidth = Math.max(1, Math.round((float)imageHeight * 16.0F / 9.0F));
      }

      if (entry != null) {
         int[] rect = this.rectAt(width, height, width + width2, height + height2);
         this.cardBounds.add(rect);
         this.cardRects.add(rect);
         this.cardEntries.add(entry);
         this.cardGroups.add(group);
         this.cardCosmetic.add(wallLayoutSlot.cosmetic);
         if (mouseX >= width && mouseX <= width + width2 && mouseY >= height && mouseY <= height + height2) {
            hoveredEntry = entry;
         }
      }

         if (entry != null && entry.displayTarget != null && entry.lastRenderedAt > 0L) {
         float ratio = entry.displayTarget.height == 0 ? 1.0F : (float)entry.displayTarget.width / (float)entry.displayTarget.height;
         float ratio2 = (float)width2 / (float)height2;
         float ratio3 = 0.0F;
         float ratio4 = 1.0F;
         float ratio5 = 1.0F;
         float ratio6 = 0.0F;
         if (ratio > ratio2) {
            float ratio7 = ratio2 / ratio;
            ratio3 = (1.0F - ratio7) / 2.0F;
            ratio4 = 1.0F - ratio3;
         } else if (ratio < ratio2) {
            float ratio8 = ratio / ratio2;
            float ratio9 = (1.0F - ratio8) / 2.0F;
            ratio5 = 1.0F - ratio9;
            ratio6 = ratio9;
         }

         guiGraphicsExtractor.blit(entry.displayTarget.getColorTextureView(), sampler(), width, height, width + width2, height + height2, ratio3, ratio4, ratio5, ratio6);
      } else if (entry != null && entry.previewTexture != null) {
         guiGraphicsExtractor.blit(entry.previewTexture, width, height, width + width2, height + height2, 0.0F, 1.0F, 0.0F, 1.0F);
      } else if (entry != null) {
         guiGraphicsExtractor.fill(width, height, width + width2, height + height2, -15066598);
      }

      if (entry != null && QueueConfig.showChunkProgress) {
         String text = stateLabel(entry.state);
         if (entry.state != QueueEntry.State.READY && entry.chunksTotal > 0) {
            text = text + " " + entry.chunksGenerated + "/" + entry.chunksTotal;
         }

         int count12 = height + height2 - 9 - 2;
         guiGraphicsExtractor.fill(width, count12 - 1, width + this.font.width(text) + 4, count12 + 9, Integer.MIN_VALUE);
         guiGraphicsExtractor.text(this.font, text, width + 2, count12, -1);
      }

      if (entry != null && entry.locked) {
         WallTextures.Animated wallTexturesAnimated = this.textures.lockFor(entry);
         if (wallTexturesAnimated != null) {
            int count13 = Math.max(1, Math.round((float)height2 * ((float)wallTexturesAnimated.textureWidth / (float)Math.max(1, wallTexturesAnimated.textureHeight))));
            int count14 = width + (width2 - count13) / 2;
            wallTexturesAnimated.blit(guiGraphicsExtractor, count14, height, count14 + count13, height + height2);
         } else {
            drawProceduralLock(guiGraphicsExtractor, width + width2 - 20, height + 4);
         }
      }
   }

   private void drawBenchmarkOverlay(GuiGraphicsExtractor guiGraphicsExtractor) {
      int width = this.width;
      int height = this.height;
      long now = System.currentTimeMillis();
      if (!SeedQueue.benchmarking) {
         if (SeedQueue.benchmarkFinishedAt != 0L && now - SeedQueue.benchmarkFinishedAt <= 15000L) {
            int count3 = Math.min(440, width - 40);
            byte boxHeight = 54;
            int count4 = (width - count3) / 2;
            int count5 = height / 2 - boxHeight / 2;
            float ratio = (float)SeedQueue.benchmarkResultMs / 1000.0F;
            float ratio2 = (float)SeedQueue.benchmarkResultResets * 1000.0F / (float)SeedQueue.benchmarkResultMs;
            guiGraphicsExtractor.fill(count4, count5, count4 + count3, count5 + boxHeight, -805306368);
            guiGraphicsExtractor.centeredText(this.font, Component.literal("SeedQueue Benchmark finished"), width / 2, count5 + 10, -11141291);
            guiGraphicsExtractor.centeredText(
               this.font,
               Component.literal(String.format("%d resets in %.2fs (%.2f RPS)", SeedQueue.benchmarkResultResets, ratio, ratio2)),
               width / 2,
               count5 + 30,
               -1
            );
         }
      } else {
         int count6 = Math.max(1, Integer.getInteger("seedqueue.benchmarkResets", QueueConfig.benchmarkResets));
         long value2 = Math.max(1L, now - SeedQueue.benchmarkStarted);
         float ratio3 = (float)SeedQueue.benchmarkCount * 1000.0F / (float)value2;
         int count7 = Math.min(440, width - 40);
         byte byte2 = 76;
         int count8 = (width - count7) / 2;
         byte byte3 = 8;
         guiGraphicsExtractor.fill(count8, byte3, count8 + count7, byte3 + byte2, -805306368);
         guiGraphicsExtractor.centeredText(this.font, Component.literal("SeedQueue Benchmark"), width / 2, byte3 + 8, -171);
         String text = SeedQueue.benchmarkCount == 0
            ? "waiting for ready worlds..."
            : String.format("%d / %d resets   %.1fs   %.2f RPS", SeedQueue.benchmarkCount, count6, (float)value2 / 1000.0F, ratio3);
         guiGraphicsExtractor.centeredText(this.font, Component.literal(text), width / 2, byte3 + 24, -1);
         int count9 = count8 + 14;
         int count10 = byte3 + 48;
         int count11 = count7 - 28;
         guiGraphicsExtractor.fill(count9, count10, count9 + count11, count10 + 8, 1610612736);
         int count12 = (int)((float)count11 * Math.min(1.0F, (float)SeedQueue.benchmarkCount / (float)count6));
         guiGraphicsExtractor.fill(count9, count10, count9 + count12, count10 + 8, -11141291);
      }
   }

   private void drawDebugOverlay(GuiGraphicsExtractor guiGraphicsExtractor) {
      long nanos = System.nanoTime();
      if (this.debugLastFrameNanos != 0L) {
         this.debugFrameNanos[this.debugSampleIndex++ % this.debugFrameNanos.length] = nanos - this.debugLastFrameNanos;
      }

      this.debugLastFrameNanos = nanos;
      int count = Math.min(this.debugSampleIndex, this.debugFrameNanos.length);
      int width = Math.min(160, Math.max(80, this.width / 4));
      byte graphHeight = 36;
      int width2 = this.width - width - 8;
      byte byte2 = 8;
      guiGraphicsExtractor.fill(width2 - 2, byte2 - 2, width2 + width + 2, byte2 + graphHeight + 24, -1342177280);
      long value2 = 0L;

      for (int i = 0; i < count; i++) {
         value2 += this.debugFrameNanos[(this.debugSampleIndex - count + i + this.debugFrameNanos.length) % this.debugFrameNanos.length];
      }

      int count4 = count > 0 && value2 > 0L ? (int)Math.round((double)count * 1.0E9 / (double)value2) : 0;
      int count5 = Math.max(1, width / Math.max(1, count));

      for (int j = 0; j < count; j++) {
         long value3 = this.debugFrameNanos[(this.debugSampleIndex - count + j + this.debugFrameNanos.length) % this.debugFrameNanos.length];
         int count6 = (int)Math.min((long)graphHeight, Math.max(1L, Math.round(1.6666667E7 / (double)Math.max(1L, value3) * 4.0)));
         int count7 = width2 + j * count5;
         guiGraphicsExtractor.fill(count7, byte2 + graphHeight - count6, count7 + count5 - 1, byte2 + graphHeight, -10035866);
      }

      long value4 = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576L;
      guiGraphicsExtractor.text(this.font, "fps " + count4 + "  cards " + this.cardEntries.size(), width2, byte2 + graphHeight + 3, -3355444);
      guiGraphicsExtractor.text(this.font, "mem " + value4 + " MB  sodium " + SodiumLevelCache.size(), width2, byte2 + graphHeight + 12, -3355444);
      long value5 = Runtime.getRuntime().maxMemory() / 1048576L;
      int count8 = value5 > 0L ? (int)Math.round((double)value4 * 100.0 / (double)value5) : 0;
      int count9 = SeedQueue.generatingCount();
      guiGraphicsExtractor.text(this.font, "SeedQueue " + this.cardEntries.size() + "/" + QueueConfig.queueSize + " | generating " + count9, 8, 8, -1);
      guiGraphicsExtractor.text(this.font, "Heap " + value4 + " MiB | M " + count8 + "%", 8, 19, -1);
      guiGraphicsExtractor.text(this.font, count9 > 0 ? "Generating seeds" : "Idle", 8, 30, -1);
   }

   private static String stateLabel(QueueEntry.State queueEntryState) {
      return switch (queueEntryState) {
         case CREATED -> "created";
         case PREPARING -> "preparing";
         case PREPARED -> "prepared";
         case STARTING -> "starting";
         case GENERATING -> "generating";
         case READY -> "ready";
         case DISCARDED -> "discarded";
         case FAILED -> "failed";
      };
   }

   private static void drawProceduralLock(GuiGraphicsExtractor guiGraphicsExtractor, int count, int count2) {
      guiGraphicsExtractor.fill(count + 2, count2, count + 10, count2 + 2, -1);
      guiGraphicsExtractor.fill(count, count2 + 2, count + 2, count2 + 7, -1);
      guiGraphicsExtractor.fill(count + 10, count2 + 2, count + 12, count2 + 7, -1);
      guiGraphicsExtractor.fill(count, count2 + 6, count + 12, count2 + 16, -10411);
      guiGraphicsExtractor.fill(count + 5, count2 + 9, count + 7, count2 + 13, -16777216);
   }
}
