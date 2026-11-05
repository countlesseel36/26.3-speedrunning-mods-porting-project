package me.contaria.seedqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Empty screen used while an instance frame is captured so no menu is drawn over it. */
public final class BlankScreen extends Screen {
   public BlankScreen() {
      super(Component.empty());
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
   }
}