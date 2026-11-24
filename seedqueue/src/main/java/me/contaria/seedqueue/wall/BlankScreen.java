package me.contaria.seedqueue.wall;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import me.contaria.seedqueue.wall.*;

public final class BlankScreen extends Screen {
   public BlankScreen() {
      super(Component.empty());
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void extractRenderState(GuiGraphicsExtractor guiGraphicsExtractor, int count, int count2, float ratio) {
   }
}
