package me.contaria.seedqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** SeedQueue settings screen, also used as the SpeedrunAPI config screen. */
public final class SettingsScreen extends Screen {
   private final Screen parent;

   public SettingsScreen(Screen parent) {
      super(Component.literal("SeedQueue Settings"));
      this.parent = parent;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   protected void init() {
      super.init();
      int x = this.width / 2 - 155;
      int right = this.width / 2 + 5;
      int y = 36;
      this.option(x, y, "Queue size: " + QueueConfig.queueSize, () -> {
         QueueConfig.queueSize = QueueConfig.queueSize >= 6 ? 1 : QueueConfig.queueSize + 1;
         QueueConfig.columns = Math.max(QueueConfig.columns, Math.min(QueueConfig.queueSize, 4));
      });
      this.option(right, y, "Preview FPS: " + QueueConfig.previewFps, () -> {
         QueueConfig.previewFps = switch (QueueConfig.previewFps) {
            case 10 -> 15;
            case 15 -> 20;
            case 20 -> 30;
            case 30 -> 60;
            default -> 10;
         };
      });
      y += 26;
      this.option(x, y, "Grid: " + QueueConfig.rows + " x " + QueueConfig.columns, () -> {
         if (QueueConfig.columns < 4) {
            QueueConfig.columns++;
         } else if (QueueConfig.rows < 4) {
            QueueConfig.rows++;
            QueueConfig.columns = 1;
         } else {
            QueueConfig.rows = 1;
            QueueConfig.columns = 1;
         }

         QueueConfig.queueSize = Math.min(QueueConfig.queueSize, QueueConfig.rows * QueueConfig.columns);
      });
      this.option(right, y, "Wall FPS: " + QueueConfig.wallFps, () -> {
         QueueConfig.wallFps = switch (QueueConfig.wallFps) {
            case 30 -> 60;
            case 60 -> 120;
            case 120 -> 240;
            default -> 30;
         };
      });
      y += 26;
      this.option(x, y, "Reset cooldown: " + QueueConfig.resetCooldownMs + "ms", () -> {
         QueueConfig.resetCooldownMs = switch (QueueConfig.resetCooldownMs) {
            case 0 -> 200;
            case 200 -> 400;
            case 400 -> 800;
            default -> 0;
         };
      });
      this.option(right, y, "Chunk radius: " + QueueConfig.chunkRadius, () -> {
         QueueConfig.chunkRadius = QueueConfig.chunkRadius >= 8 ? 0 : QueueConfig.chunkRadius + 1;
      });
      y += 26;
      this.option(x, y, "Wall: " + (QueueConfig.useWall ? "on" : "off"), () -> QueueConfig.useWall = !QueueConfig.useWall);
      this.option(right, y, "Auto queue: " + (QueueConfig.autoQueue ? "on" : "off"), () -> QueueConfig.autoQueue = !QueueConfig.autoQueue);
      y += 26;
      this.option(right, y, "Delete worlds: " + (QueueConfig.deleteDiscardedWorlds ? "on" : "off"), () -> {
         QueueConfig.deleteDiscardedWorlds = !QueueConfig.deleteDiscardedWorlds;
      });
      y += 34;
      this.addRenderableWidget(
         Button.builder(Component.literal("Done"), button -> this.onClose())
            .bounds(this.width / 2 - 100, y, 200, 20)
            .build()
      );
   }

   private void option(int x, int y, String label, Runnable action) {
      this.addRenderableWidget(Button.builder(Component.literal(label), button -> {
         action.run();
         QueueConfig.save();
         this.rebuildWidgets();
      }).bounds(x, y, 150, 20).build());
   }


   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 0xFF101010);
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
      graphics.text(this.font, "SeedQueue Settings", this.width / 2 - 155, 16, 0xFFFFFFFF);
   }

   @Override
   public void onClose() {
      QueueConfig.save();
      this.minecraft.gui.setScreen(this.parent == null ? new net.minecraft.client.gui.screens.TitleScreen() : this.parent);
   }
}
