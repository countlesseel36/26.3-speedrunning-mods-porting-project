package me.contaria.speedrunapi.config.api;

import net.minecraft.client.gui.screens.Screen;

public interface SpeedrunConfigScreenProvider {
   default boolean hasConfigScreen() {
      return true;
   }

   Screen createConfigScreen(Screen parent);
}
