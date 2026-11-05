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
      int x = this.width / 2 - 100;
      int y = 40;
      this.addRenderableWidget(
         Button.builder(Component.literal("Queue size: " + QueueConfig.queueSize), button -> {
            QueueConfig.queueSize = QueueConfig.queueSize % 4 + 1;
            QueueConfig.save();
            button.setMessage(Component.literal("Queue size: " + QueueConfig.queueSize));
         }).bounds(x, y, 200, 20).build()
      );
      y += 24;
      this.addRenderableWidget(
         Button.builder(Component.literal("Chunk radius: " + QueueConfig.chunkRadius), button -> {
            QueueConfig.chunkRadius = QueueConfig.chunkRadius >= 8 ? 0 : QueueConfig.chunkRadius + 1;
            QueueConfig.save();
            button.setMessage(Component.literal("Chunk radius: " + QueueConfig.chunkRadius));
         }).bounds(x, y, 200, 20).build()
      );
      y += 24;
      this.addRenderableWidget(
         Button.builder(Component.literal("Wall: " + (QueueConfig.useWall ? "on" : "off")), button -> {
            QueueConfig.useWall = !QueueConfig.useWall;
            QueueConfig.save();
            button.setMessage(Component.literal("Wall: " + (QueueConfig.useWall ? "on" : "off")));
         }).bounds(x, y, 200, 20).build()
      );
      y += 24;
      this.addRenderableWidget(
         Button.builder(Component.literal("Auto queue: " + (QueueConfig.autoQueue ? "on" : "off")), button -> {
            QueueConfig.autoQueue = !QueueConfig.autoQueue;
            QueueConfig.save();
            button.setMessage(Component.literal("Auto queue: " + (QueueConfig.autoQueue ? "on" : "off")));
         }).bounds(x, y, 200, 20).build()
      );
      y += 24;
      this.addRenderableWidget(
         Button.builder(Component.literal("Preview: " + perspectiveLabel()), button -> {
            QueueConfig.perspective = "third_person".equals(QueueConfig.perspective) ? "first_person" : "third_person";
            QueueConfig.save();
            button.setMessage(Component.literal("Preview: " + perspectiveLabel()));
         }).bounds(x, y, 200, 20).build()
      );
      y += 30;
      this.addRenderableWidget(
         Button.builder(Component.literal("Done"), button -> this.onClose()).bounds(x, y, 200, 20).build()
      );
   }

   private static String perspectiveLabel() {
      return "third_person".equals(QueueConfig.perspective) ? "Third person" : "First person";
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 0xFF101010);
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
      graphics.text(this.font, "SeedQueue Settings", this.width / 2 - 100, 16, 0xFFFFFFFF);
   }

   @Override
   public void onClose() {
      QueueConfig.save();
      this.minecraft.gui.setScreen(this.parent == null ? new net.minecraft.client.gui.screens.TitleScreen() : this.parent);
   }
}
