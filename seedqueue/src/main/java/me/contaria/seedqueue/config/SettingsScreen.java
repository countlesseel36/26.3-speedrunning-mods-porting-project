package me.contaria.seedqueue.config;
import me.contaria.seedqueue.SeedQueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import me.contaria.seedqueue.config.*;

public final class SettingsScreen extends Screen {
   private final Screen parent;

   public SettingsScreen(Screen screen) {
      super(Component.literal("SeedQueue Settings"));
      this.parent = screen;
   }

   public boolean isPauseScreen() {
      return false;
   }

   protected void init() {
      super.init();
      int count = this.width / 2 - 155;
      int count2 = this.width / 2 + 5;
      int count3 = 36;
      this.option(count, count3, "Queue size: " + QueueConfig.queueSize, () -> {
         QueueConfig.queueSize = QueueConfig.queueSize >= 6 ? 1 : QueueConfig.queueSize + 1;
         QueueConfig.columns = Math.max(QueueConfig.columns, Math.min(QueueConfig.queueSize, 4));
      });
      this.option(count2, count3, "Preview FPS: " + QueueConfig.previewFps, () -> {
         QueueConfig.previewFps = switch (QueueConfig.previewFps) {
            case 10 -> 15;
            case 15 -> 20;
            case 20 -> 30;
            case 30 -> 60;
            default -> 10;
         };
      });
      count3 += 26;
      this.option(count, count3, "Grid: " + QueueConfig.rows + " x " + QueueConfig.columns, () -> {
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
      this.option(count2, count3, "Wall FPS: " + QueueConfig.wallFps, () -> {
         QueueConfig.wallFps = switch (QueueConfig.wallFps) {
            case 30 -> 60;
            case 60 -> 120;
            case 120 -> 240;
            default -> 30;
         };
      });
      count3 += 26;
      this.option(count, count3, "Reset cooldown: " + QueueConfig.resetCooldownMs + "ms", () -> {
         QueueConfig.resetCooldownMs = switch (QueueConfig.resetCooldownMs) {
            case 0 -> 200;
            case 200 -> 400;
            case 400 -> 800;
            default -> 0;
         };
      });
      this.option(
         count2, count3, "Chunk radius: " + QueueConfig.chunkRadius, () -> QueueConfig.chunkRadius = QueueConfig.chunkRadius >= 8 ? 0 : QueueConfig.chunkRadius + 1
      );
      count3 += 26;
      this.option(count, count3, "Wall: " + (QueueConfig.useWall ? "on" : "off"), () -> QueueConfig.useWall = !QueueConfig.useWall);
      this.option(count2, count3, "Auto queue: " + (QueueConfig.autoQueue ? "on" : "off"), () -> QueueConfig.autoQueue = !QueueConfig.autoQueue);
      count3 += 26;
      this.option(
         count2,
         count3,
         "Delete worlds: " + (QueueConfig.deleteDiscardedWorlds ? "on" : "off"),
         () -> QueueConfig.deleteDiscardedWorlds = !QueueConfig.deleteDiscardedWorlds
      );
      count3 += 34;
      this.addRenderableWidget(Button.builder(Component.literal("Done"), arg -> this.onClose()).bounds(this.width / 2 - 100, count3, 200, 20).build());
   }

   private void option(int count, int count2, String text, Runnable runnable) {
      this.addRenderableWidget(Button.builder(Component.literal(text), arg -> {
         runnable.run();
         QueueConfig.save();
         this.rebuildWidgets();
      }).bounds(count, count2, 150, 20).build());
   }

   public void extractRenderState(GuiGraphicsExtractor guiGraphicsExtractor, int count, int count2, float ratio) {
      guiGraphicsExtractor.fill(0, 0, this.width, this.height, -15724528);
      super.extractRenderState(guiGraphicsExtractor, count, count2, ratio);
      guiGraphicsExtractor.text(this.font, "SeedQueue Settings", this.width / 2 - 155, 16, -1);
   }

   public void onClose() {
      QueueConfig.save();
      this.minecraft.gui.setScreen((Screen)(this.parent == null ? new TitleScreen() : this.parent));
   }
}
