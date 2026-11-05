package me.contaria.seedqueue;

import me.contaria.speedrunapi.config.api.SpeedrunConfigScreenProvider;
import net.minecraft.client.gui.screens.Screen;

public final class SeedQueueConfigScreenProvider implements SpeedrunConfigScreenProvider {
   @Override
   public Screen createConfigScreen(Screen parent) {
      return new SettingsScreen(parent);
   }
}