package me.contaria.seedqueue;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** SeedQueue wall: shows every queued world, its progress and a top-down preview map. */
public final class WallScreen extends Screen {
   private final Supplier<List<QueueEntry>> entries;
   private final List<int[]> cardBounds = new java.util.ArrayList<>();
   private final List<QueueEntry> cardEntries = new java.util.ArrayList<>();
   private static QueueEntry hoveredEntry;

   public WallScreen(Supplier<List<QueueEntry>> entries) {
      super(Component.literal("SeedQueue"));
      this.entries = entries;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
      if (event.isEscape()) {
         this.onClose();
         return true;
      }

      return super.keyPressed(event);
   }

   @Override
   public void onClose() {
      SeedQueue.onWallClosed(this.minecraft);
   }

   /** The instance currently under the mouse; its world is rendered live behind the wall. */
   public static QueueEntry hoveredEntry() {
      return hoveredEntry;
   }

   private static String trim(String text, int length) {
      return text.length() <= length ? text : text.substring(0, length - 1) + "~";
   }

   /** Simple hillshading so the top-down map reads as terrain. */
   private static int shade(int[] colors, int[] heights, int index, int width) {
      int color = colors[index];

      if (heights == null) {
         return color;
      }

      int height = heights[index];
      int west = index % width > 0 ? heights[index - 1] : height;
      int north = index >= width ? heights[index - width] : height;
      float light = 1.0F + (height - west) * 0.06F + (height - north) * 0.04F;
      light = Math.max(0.55F, Math.min(1.25F, light));
      int r = (int) Math.min(255, ((color >> 16) & 0xFF) * light);
      int g = (int) Math.min(255, ((color >> 8) & 0xFF) * light);
      int b = (int) Math.min(255, (color & 0xFF) * light);
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   @Override
   public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
      for (int i = 0; i < cardBounds.size() && i < cardEntries.size(); i++) {
         int[] bounds = cardBounds.get(i);

         if (event.x() >= bounds[0] && event.x() <= bounds[2] && event.y() >= bounds[1] && event.y() <= bounds[3]) {
            QueueEntry entry = cardEntries.get(i);

            if (event.button() == 1) {
               entry.locked = !entry.locked;
               SeedQueue.LOGGER.info("[seedqueue] entry #{} locked={}", entry.number, entry.locked);
               return true;
            }

            if (event.button() == 2) {
               SeedQueue.discardEntry(entry);
               return true;
            }

            if (SeedQueue.playEntry(entry)) {
               this.minecraft.gui.setScreen(null);
            }

            return true;
         }
      }

      return super.mouseClicked(event, doubleClick);
   }

   public int debugCardCount() {
      return cardEntries.size();
   }

   /** Smoke helper: toggles the lock of the first card. */
   public boolean debugToggleLockFirst() {
      if (cardEntries.isEmpty()) {
         return false;
      }

      QueueEntry entry = cardEntries.get(0);
      entry.locked = !entry.locked;
      SeedQueue.LOGGER.info("[seedqueue] entry #{} locked={}", entry.number, entry.locked);
      return true;
   }

   /** Smoke helper: right-clicks the first card (toggle lock). */
   public boolean debugRightClickFirst() {
      if (cardBounds.isEmpty()) {
         return false;
      }

      int[] bounds = cardBounds.get(0);
      return this.mouseClicked(
         new net.minecraft.client.input.MouseButtonEvent(
            (bounds[0] + bounds[2]) / 2.0,
            (bounds[1] + bounds[3]) / 2.0,
            new net.minecraft.client.input.MouseButtonInfo(1, 0)
         ),
         false
      );
   }

   /** Smoke helper: middle-clicks the first card (delete entry). */
   public boolean debugMiddleClickFirst() {
      if (cardBounds.isEmpty()) {
         return false;
      }

      int[] bounds = cardBounds.get(0);
      return this.mouseClicked(
         new net.minecraft.client.input.MouseButtonEvent(
            (bounds[0] + bounds[2]) / 2.0, (bounds[1] + bounds[3]) / 2.0, new net.minecraft.client.input.MouseButtonInfo(2, 0)
         ),
         false
      );
   }

   /** Smoke helper: clicks the first card through the normal mouse path. */
   public boolean debugClickFirst() {
      if (cardBounds.isEmpty()) {
         return false;
      }

      int[] bounds = cardBounds.get(0);
      return this.mouseClicked(
         new net.minecraft.client.input.MouseButtonEvent(
            (bounds[0] + bounds[2]) / 2.0, (bounds[1] + bounds[3]) / 2.0, new net.minecraft.client.input.MouseButtonInfo(0, 0)
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
      if (!Boolean.getBoolean("seedqueue.wallClearBackground")) {
         graphics.fill(0, 0, width, height, 0xFF000000);
      }
      List<QueueEntry> list = entries.get();
      cardBounds.clear();
      cardEntries.clear();
      hoveredEntry = null;
      int columns = 3;
      int cardWidth = Math.max(120, (width - 32) / columns);
      int cardHeight = 118;

      // Always show the configured number of queue slots so the wall layout stays stable.
      int slots = Math.max(list.size(), Math.max(1, QueueConfig.queueSize));

      for (int i = 0; i < slots; i++) {
         QueueEntry entry = i < list.size() ? list.get(i) : null;
         int column = i % columns;
         int rowIndex = i / columns;
         int x = 16 + column * cardWidth;
         int y = 24 + rowIndex * (cardHeight + 8);

         if (entry != null) {
            cardBounds.add(new int[] { x, y, x + cardWidth - 8, y + cardHeight });
            cardEntries.add(entry);
         }

         if (entry != null && mouseX >= x && mouseX <= x + cardWidth - 8 && mouseY >= y && mouseY <= y + cardHeight) {
            hoveredEntry = entry;
         }

         if (entry != null && entry.previewTexture != null) {
            // DynamicTexture from grab (preferred when available).
            graphics.blit(
               net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
               entry.previewTexture,
               x,
               y,
               0.0F,
               0.0F,
               cardWidth - 8,
               cardHeight,
               Math.max(1, entry.captureWidth),
               Math.max(1, entry.captureHeight),
               -1
            );
         } else {
            com.mojang.blaze3d.pipeline.RenderTarget target =
               entry == null ? null : entry.renderTarget;

            if (target != null) {
               graphics.blit(
                  target.getColorTextureView(), sampler(), x, y, cardWidth - 8, cardHeight, 0.0F, 0.0F, 1.0F, 1.0F
               );
            } else {
               // Placeholder so every queue slot has a visible card while its preview renders.
               graphics.fill(x, y, x + cardWidth - 8, y + cardHeight, 0xFF1A1A1A);
               graphics.fill(x, y, x + cardWidth - 8, y + 1, 0xFF3A3A3A);
               graphics.fill(x, y + cardHeight - 1, x + cardWidth - 8, y + cardHeight, 0xFF3A3A3A);
            }
         }

         if (entry != null && entry.locked) {
            drawLock(graphics, x + cardWidth - 26, y + 6);
         }
      }

      if (Boolean.getBoolean("seedqueue.wallFullscreenTarget") && !list.isEmpty()
         && list.get(0).renderTarget != null) {
         graphics.blit(
            list.get(0).renderTarget.getColorTextureView(), sampler(), 0, 0, width, height, 0.0F, 0.0F, 1.0F, 1.0F
         );
      }
   }

   private static void drawLock(GuiGraphicsExtractor graphics, int x, int y) {
      graphics.fill(x + 2, y, x + 10, y + 2, 0xFFFFFFFF);
      graphics.fill(x, y + 2, x + 2, y + 7, 0xFFFFFFFF);
      graphics.fill(x + 10, y + 2, x + 12, y + 7, 0xFFFFFFFF);
      graphics.fill(x, y + 6, x + 12, y + 16, 0xFFFFD755);
      graphics.fill(x + 5, y + 9, x + 7, y + 13, 0xFF000000);
   }
}
