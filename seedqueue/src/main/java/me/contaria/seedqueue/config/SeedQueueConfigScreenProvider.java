package me.contaria.seedqueue.config;

import me.contaria.speedrunapi.config.api.SpeedrunConfigScreenProvider;
import net.minecraft.client.gui.screens.Screen;
import me.contaria.seedqueue.config.*;

public final class SeedQueueConfigScreenProvider implements SpeedrunConfigScreenProvider {
   public Screen createConfigScreen(Screen screen) {
      return new SettingsScreen(screen);
   }
}
