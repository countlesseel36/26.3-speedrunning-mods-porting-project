package me.contaria.seedqueue;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * SeedQueue wall. Layout and decoration come from the resource pack
 * ({@code assets/seedqueue/wall/custom_layout.json} + {@code textures/gui/wall/}); the built-in
 * default is a plain rows x columns grid with no locked strip.
 */
public final class WallScreen extends Screen {
   private static final boolean WALL_CLEAR_BACKGROUND = Boolean.getBoolean("seedqueue.wallClearBackground");
   private static final boolean WALL_FULLSCREEN_TARGET = Boolean.getBoolean("seedqueue.wallFullscreenTarget");

   private final Supplier<List<QueueEntry>> entries;
   private final List<int[]> cardBounds = new java.util.ArrayList<>();
   private final List<QueueEntry> cardEntries = new java.util.ArrayList<>();
   private final List<int[]> cardRects = new java.util.ArrayList<>();
   private final List<Integer> cardGroups = new java.util.ArrayList<>();
   private final List<Boolean> cardCosmetic = new java.util.ArrayList<>();
   private final List<QueueEntry> mainScratch = new java.util.ArrayList<>();
   private final List<QueueEntry> preparingScratch = new java.util.ArrayList<>();
   private final List<QueueEntry> lockedScratch = new java.util.ArrayList<>();
   private static QueueEntry hoveredEntry;
   private static volatile long joinSlideStartedAt;
   private int slideOffset;
   private WallLayout layout;
   private WallTextures textures;
   private final long[] debugFrameNanos = new long[120];
   private int debugSampleIndex;
   private long debugLastFrameNanos;

   public WallScreen(Supplier<List<QueueEntry>> entries) {
      super(Component.literal("SeedQueue"));
      this.entries = entries;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   protected void init() {
      super.init();
      this.layout = WallLayout.load(this.minecraft);
      this.textures = WallTextures.load(this.minecraft);
   }

   /** Starts the wall slide-out animation when the player joins an instance. */
   public static void beginJoinTransition() {
      joinSlideStartedAt = System.currentTimeMillis();
   }

   @Override
   public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
      if (QueueConfig.showDebugMenu && event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_F3) {
         QueueEntry entry = hoveredEntry;

         if (entry != null) {
            if ((event.modifiers() & 1) != 0) {
               SeedQueue.logServerThreadStack(entry);
            } else {
               SeedQueue.logEntryDebug(entry);
            }
         }

         return true;
      }

      WallKeybindings.Action action = WallKeybindings.match(event);
      QueueEntry hovered = hoveredEntry;

      if (action != null) {
         switch (action) {
            case PLAY:
               if (hovered != null) {
                  SeedQueue.playEntry(hovered);
               }

               return true;
            case LOCK:
               if (hovered != null) {
                  toggleLock(hovered);
               }

               return true;
            case RESET:
               SeedQueue.resetEntry(hovered);
               return true;
            case RESET_ALL:
               SeedQueue.playSound("reset_all");
               SeedQueue.resetEntries(this.groupEntries(0));
               return true;
            case RESET_COLUMN:
               if (hovered != null) {
                  SeedQueue.playSound("reset_column");
                  SeedQueue.resetEntries(this.resetGroup(0, hovered, true));
               }

               return true;
            case RESET_ROW:
               if (hovered != null) {
                  SeedQueue.playSound("reset_row");
                  SeedQueue.resetEntries(this.resetGroup(0, hovered, false));
               }

               return true;
            case PLAY_NEXT_LOCK:
               SeedQueue.playNextLock(this.minecraft);
               return true;
            case SCHEDULE_JOIN:
               if (hovered != null) {
                  hovered.locked = true;
                  hovered.lockedAt = System.currentTimeMillis();
                  hovered.scheduledJoin = true;
                  SeedQueue.playSound("lock_instance");
                  SeedQueue.LOGGER.info("[seedqueue] entry #{} scheduled to join", hovered.number);
               }

               return true;
            case SCHEDULE_ALL:
               SeedQueue.scheduleAll();
               return true;
            case START_BENCHMARK:
               SeedQueue.benchmarking = true;
               SeedQueue.benchmarkCount = 0;
               SeedQueue.benchmarkStarted = System.currentTimeMillis();
               SeedQueue.playSound("start_benchmark");
               SeedQueue.LOGGER.info(
                  "[seedqueue] benchmark started ({} resets)",
                  Integer.getInteger("seedqueue.benchmarkResets", QueueConfig.benchmarkResets)
               );
               return true;
            case CANCEL_BENCHMARK:
               SeedQueue.benchmarking = false;
               SeedQueue.playSound("finish_benchmark");
               SeedQueue.LOGGER.info("[seedqueue] benchmark cancelled after {} resets", SeedQueue.benchmarkCount);
               return true;
            case FOCUS_RESET:
               if (hovered != null && SeedQueue.playEntry(hovered)) {
                  if (QueueConfig.tryResetCooldown()) {
                     SeedQueue.resetAllUnlocked();
                     SeedQueue.refillQueue(this.minecraft);
                  }
               }

               return true;
            case QUIT:
               this.onClose();
               return true;
            default:
               break;
         }
      }

      if (event.isEscape()) {
         this.onClose();
         return true;
      }

      return super.keyPressed(event);
   }

   /** All cards of a group (0 = main, 1 = preparing, 2 = locked) that may be reset. */
   private List<QueueEntry> groupEntries(int group) {
      List<QueueEntry> result = new java.util.ArrayList<>();

      for (int i = 0; i < this.cardEntries.size(); i++) {
         if (this.cardGroups.get(i) == group && !this.cardCosmetic.get(i)) {
            result.add(this.cardEntries.get(i));
         }
      }

      return result;
   }

   /** Cards in the same row/column (by slot rect) for reset row/column. */
   private List<QueueEntry> resetGroup(int group, QueueEntry hovered, boolean column) {
      List<QueueEntry> result = new java.util.ArrayList<>();
      int index = this.cardEntries.indexOf(hovered);

      if (index < 0) {
         return result;
      }

      int[] target = this.cardRects.get(index);

      for (int i = 0; i < this.cardEntries.size(); i++) {
         if (this.cardGroups.get(i) != group || this.cardCosmetic.get(i) || this.cardEntries.get(i).locked) {
            continue;
         }

         int[] rect = this.cardRects.get(i);
         boolean same = column
            ? rect[0] < target[2] && rect[2] > target[0]
            : rect[1] < target[3] && rect[3] > target[1];

         if (same) {
            result.add(this.cardEntries.get(i));
         }
      }

      return result;
   }

   private void toggleLock(QueueEntry entry) {
      entry.locked = !entry.locked;

      if (entry.locked) {
         entry.lockedAt = System.currentTimeMillis();
         SeedQueue.playSound("lock_instance");

         // Only a layout with a locked group frees the main slot (and only replaceLockedInstances
         // says to fill it immediately; otherwise the next Reset All refills it).
         if (this.layout != null && this.layout.locked != null && this.layout.replaceLockedInstances) {
            SeedQueue.refillQueue(this.minecraft);
         }
      }

      SeedQueue.LOGGER.info("[seedqueue] entry #{} locked={}", entry.number, entry.locked);
   }

   @Override
   public void onClose() {
      SeedQueue.onWallClosed(this.minecraft);
   }

   public static QueueEntry hoveredEntry() {
      return hoveredEntry;
   }

   // 26.3 delivers mouse buttons straight from SDL, which numbers them 1 = left, 2 = middle,
   // 3 = right (GLFW/SDL2 style 0-based indices are not used any more). The old code assumed
   // 0/1/2, so left click locked a card and right click did nothing.
   private static final int BUTTON_LEFT = 1;
   private static final int BUTTON_MIDDLE = 2;
   private static final int BUTTON_RIGHT = 3;

   @Override
   public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
      int button = event.button();

      // Only left (join), right (lock) and middle (discard) do anything. Mouse side buttons (SDL 4/5,
      // and anything above) are ignored so a side click never starts another world ("ghost").
      if (button != BUTTON_LEFT && button != BUTTON_MIDDLE && button != BUTTON_RIGHT) {
         return false;
      }

      for (int i = 0; i < this.cardBounds.size() && i < this.cardEntries.size(); i++) {
         int[] bounds = this.cardBounds.get(i);

         if (event.x() >= bounds[0] && event.x() <= bounds[2] && event.y() >= bounds[1] && event.y() <= bounds[3]) {
            QueueEntry entry = this.cardEntries.get(i);

            if (button == BUTTON_RIGHT) {
               this.toggleLock(entry);
               return true;
            }

            if (button == BUTTON_MIDDLE) {
               SeedQueue.discardEntry(entry);
               SeedQueue.refillQueue(this.minecraft);
               return true;
            }

            SeedQueue.playEntry(entry);
            return true;
         }
      }

      return super.mouseClicked(event, doubleClick);
   }

   public int debugCardCount() {
      return this.cardEntries.size();
   }

   public boolean debugPressKey(int key) {
      if (this.cardEntries.isEmpty()) {
         return false;
      }

      hoveredEntry = this.cardEntries.get(0);

      try {
         return this.keyPressed(new net.minecraft.client.input.KeyEvent(key, 0, 0));
      } finally {
         hoveredEntry = null;
      }
   }

   public boolean debugToggleLockFirst() {
      if (this.cardEntries.isEmpty()) {
         return false;
      }

      this.toggleLock(this.cardEntries.get(0));
      return true;
   }

   public boolean debugRightClickFirst() {
      return this.debugClick(1);
   }

   public boolean debugMiddleClickFirst() {
      return this.debugClick(BUTTON_MIDDLE);
   }

   public boolean debugClickFirst() {
      return this.debugClick(BUTTON_LEFT);
   }

   private boolean debugClick(int button) {
      if (this.cardBounds.isEmpty()) {
         return false;
      }

      int[] bounds = this.cardBounds.get(0);
      return this.mouseClicked(
         new net.minecraft.client.input.MouseButtonEvent(
            (bounds[0] + bounds[2]) / 2.0,
            (bounds[1] + bounds[3]) / 2.0,
            new net.minecraft.client.input.MouseButtonInfo(button, 0)
         ),
         false
      );
   }

   private static com.mojang.renderpearl.api.textures.GpuSampler sampler;

   private static com.mojang.renderpearl.api.textures.GpuSampler sampler() {
      if (sampler == null) {
         sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
            .getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.LINEAR);
      }

      return sampler;
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      int width = this.width;
      int height = this.height;

      if (this.layout == null) {
         this.layout = WallLayout.load(this.minecraft);
      }

      if (this.textures == null) {
         this.textures = WallTextures.load(this.minecraft);
      }

      this.slideOffset = 0;

      if (QueueConfig.joinTransition && joinSlideStartedAt != 0) {
         float progress = Math.min(1.0F, (System.currentTimeMillis() - joinSlideStartedAt) / 320.0F);
         this.slideOffset = -Math.round(progress * width);

         if (progress >= 1.0F) {
            joinSlideStartedAt = 0;
         }
      }

      this.cardBounds.clear();
      this.cardEntries.clear();
      this.cardRects.clear();
      this.cardGroups.clear();
      this.cardCosmetic.clear();
      hoveredEntry = null;

      if (!WALL_CLEAR_BACKGROUND) {
         if (this.textures.background != null) {
            this.textures.background.blit(graphics, 0, 0, width, height);
         } else {
            graphics.fill(0, 0, width, height, 0xFF000000);
         }
      }

      List<QueueEntry> list = this.entries.get();
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

      List<WallLayout.Slot> mainSlots = this.layout.main.resolve(width, height);

      for (int i = 0; i < mainSlots.size(); i++) {
         WallLayout.Slot slot = mainSlots.get(i);
         QueueEntry entry = i < this.mainScratch.size() ? this.mainScratch.get(i) : null;
         this.drawSlot(graphics, entry, slot, mouseX, mouseY, 0);
      }

      int preparingIndex = 0;

      for (WallLayout.Group group : this.layout.preparing) {
         for (WallLayout.Slot slot : group.resolve(width, height)) {
            if (preparingIndex >= this.preparingScratch.size()) {
               break;
            }

            this.drawSlot(graphics, this.preparingScratch.get(preparingIndex++), slot, mouseX, mouseY, 1);
         }
      }

      if (this.layout.locked != null) {
         int lockedIndex = 0;

         for (WallLayout.Slot slot : this.layout.locked.resolve(width, height)) {
            if (lockedIndex >= this.lockedScratch.size()) {
               break;
            }

            this.drawSlot(graphics, this.lockedScratch.get(lockedIndex++), slot, mouseX, mouseY, 2);
         }
      }

      if (this.textures.overlay != null) {
         this.textures.overlay.blit(graphics, 0, 0, width, height);
      }

      this.drawBenchmarkOverlay(graphics);

      if (QueueConfig.showDebugMenu) {
         this.drawDebugOverlay(graphics);
      }

      if (WALL_FULLSCREEN_TARGET && !list.isEmpty() && list.get(0).renderTarget != null) {
         QueueEntry first = list.get(0);
         var shown = first.displayTarget != null ? first.displayTarget : first.renderTarget;
         graphics.blit(shown.getColorTextureView(), sampler(), 0, 0, width, height, 0.0F, 0.0F, 1.0F, 1.0F);
      }
   }

   private void drawSlot(
      GuiGraphicsExtractor graphics,
      QueueEntry entry,
      WallLayout.Slot slot,
      int mouseX,
      int mouseY,
      int group
   ) {
      int x = slot.pixelX(this.width) + this.slideOffset;
      int y = slot.pixelY(this.height);
      int cardWidth = Math.max(1, slot.pixelWidth(this.width));
      int cardHeight = Math.max(1, slot.pixelHeight(this.height));
      int labelHeight = Math.min(16, Math.max(10, cardHeight / 8));
      int availableHeight = Math.max(1, cardHeight - labelHeight);
      // Keep the preview's 16:9 aspect inside whatever rectangle the layout defines (letterboxed in
      // tall slots, pillarboxed in wide ones) instead of stretching it.
      int imageHeight = availableHeight;
      int imageWidth = Math.min(cardWidth, Math.max(1, Math.round(availableHeight * 16.0F / 9.0F)));

      if (imageWidth > cardWidth) {
         imageWidth = cardWidth;
         imageHeight = Math.max(1, Math.round(cardWidth * 9.0F / 16.0F));
      }

      if (imageHeight > availableHeight) {
         imageHeight = availableHeight;
         imageWidth = Math.max(1, Math.round(availableHeight * 16.0F / 9.0F));
      }

      // Seamless wall: the preview fills the whole card slot, no letterbox and no background band.
      int imageX = x;
      imageWidth = cardWidth;
      imageHeight = cardHeight;

      if (entry != null) {
         this.cardBounds.add(new int[] { x, y, x + cardWidth, y + cardHeight });
         this.cardRects.add(new int[] { x, y, x + cardWidth, y + cardHeight });
         this.cardEntries.add(entry);
         this.cardGroups.add(group);
         this.cardCosmetic.add(slot.cosmetic);

         if (mouseX >= x && mouseX <= x + cardWidth && mouseY >= y && mouseY <= y + cardHeight) {
            hoveredEntry = entry;
         }
      }


      if (entry != null && entry.displayTarget != null) {
         float srcAspect = entry.displayTarget.height == 0
            ? 1.0F
            : entry.displayTarget.width / (float) entry.displayTarget.height;
         float dstAspect = imageWidth / (float) imageHeight;
         float u0 = 0.0F;
         float u1 = 1.0F;
         float v0 = 1.0F;
         float v1 = 0.0F;

         if (srcAspect > dstAspect) {
            float visible = dstAspect / srcAspect;
            u0 = (1.0F - visible) / 2.0F;
            u1 = 1.0F - u0;
         } else if (srcAspect < dstAspect) {
            float visible = srcAspect / dstAspect;
            float inset = (1.0F - visible) / 2.0F;
            v0 = 1.0F - inset;
            v1 = inset;
         }

         graphics.blit(
            entry.displayTarget.getColorTextureView(), sampler(), imageX, y, imageX + imageWidth, y + imageHeight, u0, u1, v0, v1
         );
      } else if (entry != null && entry.previewTexture != null) {
         graphics.blit(entry.previewTexture, imageX, y, imageX + imageWidth, y + imageHeight, 0.0F, 1.0F, 0.0F, 1.0F);
      } else if (entry != null) {
         graphics.fill(imageX, y, imageX + imageWidth, y + imageHeight, 0xFF1A1A1A);
      }


      // The state line ("generating" / "ready" and the chunk counter) is only shown when the chunk
      // progress option is enabled; by default the wall stays free of progress text.
      if (entry != null && QueueConfig.showChunkProgress) {
         String label = stateLabel(entry.state);

         if (entry.state != QueueEntry.State.READY && entry.chunksTotal > 0) {
            label = label + " " + entry.chunksGenerated + "/" + entry.chunksTotal;
         }

         graphics.text(this.font, label, imageX + 2, y + imageHeight + 2, 0xFFCFCFCF);
      }

      if (entry != null && entry.locked) {
         WallTextures.Animated lock = this.textures.lockFor(entry);

         if (lock != null) {
            int lockHeight = imageHeight;
            int lockWidth = Math.max(1, Math.round(lockHeight * (lock.textureWidth / (float) Math.max(1, lock.textureHeight))));
            int lockX = imageX + (imageWidth - lockWidth) / 2;
            lock.blit(graphics, lockX, y, lockX + lockWidth, y + lockHeight);
         } else {
            drawProceduralLock(graphics, imageX + imageWidth - 20, y + 4);
         }
      }
   }

   private void drawBenchmarkOverlay(GuiGraphicsExtractor graphics) {
      int width = this.width;
      int height = this.height;
      long now = System.currentTimeMillis();

      if (!SeedQueue.benchmarking) {
         if (SeedQueue.benchmarkFinishedAt == 0 || now - SeedQueue.benchmarkFinishedAt > 12000L) {
            return;
         }

         int panelW = Math.min(440, width - 40);
         int panelH = 54;
         int x = (width - panelW) / 2;
         int y = height / 2 - panelH / 2;
         float seconds = SeedQueue.benchmarkResultMs / 1000.0F;
         float rps = SeedQueue.benchmarkResultResets * 1000.0F / SeedQueue.benchmarkResultMs;
         graphics.fill(x, y, x + panelW, y + panelH, 0xD0000000);
         graphics.centeredText(this.font, Component.literal("SeedQueue Benchmark finished"), width / 2, y + 10, 0xFF55FF55);
         graphics.centeredText(
            this.font,
            Component.literal(
               String.format("%d resets in %.2fs (%.2f RPS)", SeedQueue.benchmarkResultResets, seconds, rps)
            ),
            width / 2,
            y + 30,
            0xFFFFFFFF
         );
         return;
      }

      int total = Math.max(1, Integer.getInteger("seedqueue.benchmarkResets", QueueConfig.benchmarkResets));
      long elapsed = Math.max(1L, now - SeedQueue.benchmarkStarted);
      float rps = SeedQueue.benchmarkCount * 1000.0F / elapsed;
      int panelW = Math.min(440, width - 40);
      int panelH = 76;
      int x = (width - panelW) / 2;
      int y = 8;
      graphics.fill(x, y, x + panelW, y + panelH, 0xD0000000);
      graphics.centeredText(this.font, Component.literal("SeedQueue Benchmark"), width / 2, y + 8, 0xFFFFFF55);
      String line = SeedQueue.benchmarkCount == 0
         ? "waiting for ready worlds..."
         : String.format("%d / %d resets   %.1fs   %.2f RPS", SeedQueue.benchmarkCount, total, elapsed / 1000.0F, rps);
      graphics.centeredText(this.font, Component.literal(line), width / 2, y + 24, 0xFFFFFFFF);
      int barX = x + 14;
      int barY = y + 48;
      int barW = panelW - 28;
      graphics.fill(barX, barY, barX + barW, barY + 8, 0x60000000);
      int filled = (int)(barW * Math.min(1.0F, SeedQueue.benchmarkCount / (float)total));
      graphics.fill(barX, barY, barX + filled, barY + 8, 0xFF55FF55);
   }

   private void drawDebugOverlay(GuiGraphicsExtractor graphics) {
      long now = System.nanoTime();

      if (this.debugLastFrameNanos != 0) {
         this.debugFrameNanos[this.debugSampleIndex++ % this.debugFrameNanos.length] = now - this.debugLastFrameNanos;
      }

      this.debugLastFrameNanos = now;
      int samples = Math.min(this.debugSampleIndex, this.debugFrameNanos.length);
      int graphWidth = Math.min(160, Math.max(80, this.width / 4));
      int graphHeight = 36;
      int x = this.width - graphWidth - 8;
      int y = 8;
      graphics.fill(x - 2, y - 2, x + graphWidth + 2, y + graphHeight + 24, 0xB0000000);
      long sum = 0;

      for (int i = 0; i < samples; i++) {
         sum += this.debugFrameNanos[(this.debugSampleIndex - samples + i + this.debugFrameNanos.length) % this.debugFrameNanos.length];
      }

      int fps = samples > 0 && sum > 0 ? (int) Math.round((double) samples * 1.0E9 / sum) : 0;
      int barWidth = Math.max(1, graphWidth / Math.max(1, samples));

      for (int i = 0; i < samples; i++) {
         long nanos = this.debugFrameNanos[(this.debugSampleIndex - samples + i + this.debugFrameNanos.length) % this.debugFrameNanos.length];
         int barHeight = (int) Math.min(graphHeight, Math.max(1, Math.round(16_666_667.0 / Math.max(1L, nanos) * 4.0)));
         int barX = x + i * barWidth;
         graphics.fill(barX, y + graphHeight - barHeight, barX + barWidth - 1, y + graphHeight, 0xFF66DD66);
      }

      long usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024L * 1024L);
      graphics.text(this.font, "fps " + fps + "  cards " + this.cardEntries.size(), x, y + graphHeight + 3, 0xFFCCCCCC);
      graphics.text(this.font, "mem " + usedMemory + " MB  sodium " + SodiumLevelCache.size(), x, y + graphHeight + 12, 0xFFCCCCCC);

      // Status block (top left), mirroring the classic SeedQueue HUD: queue usage, memory and state.
      long maxMemory = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
      int memoryPercent = maxMemory > 0 ? (int)Math.round(usedMemory * 100.0 / maxMemory) : 0;
      int generating = SeedQueue.generatingCount();
      graphics.text(
         this.font,
         "SeedQueue " + this.cardEntries.size() + "/" + QueueConfig.queueSize + " | generating " + generating,
         8,
         8,
         0xFFFFFFFF
      );
      graphics.text(this.font, "Heap " + usedMemory + " MiB | M " + memoryPercent + "%", 8, 19, 0xFFFFFFFF);
      graphics.text(this.font, generating > 0 ? "Generating seeds" : "Idle", 8, 30, 0xFFFFFFFF);
   }

   private static String stateLabel(QueueEntry.State state) {
      return switch (state) {
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

   private static void drawProceduralLock(GuiGraphicsExtractor graphics, int x, int y) {
      graphics.fill(x + 2, y, x + 10, y + 2, 0xFFFFFFFF);
      graphics.fill(x, y + 2, x + 2, y + 7, 0xFFFFFFFF);
      graphics.fill(x + 10, y + 2, x + 12, y + 7, 0xFFFFFFFF);
      graphics.fill(x, y + 6, x + 12, y + 16, 0xFFFFD755);
      graphics.fill(x + 5, y + 9, x + 7, y + 13, 0xFF000000);
   }
}
