package me.contaria.speedrunapi.config.toast;

import me.contaria.speedrunapi.SpeedrunAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class SpeedrunConfigErrorToast {
   private SpeedrunConfigErrorToast() {
   }

   public static void showLoadError(String modID, Exception exception) {
      SpeedrunAPI.LOGGER.error("Encountered a {} while loading {} config!", exception.getClass().getSimpleName(), modID, exception);
      show(modID, "load");
   }

   public static void showSaveError(String modID, Exception exception) {
      SpeedrunAPI.LOGGER.error("Encountered a {} while saving {} config!", exception.getClass().getSimpleName(), modID, exception);
      show(modID, "save");
   }

   private static void show(String modID, String action) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && minecraft.player != null) {
         minecraft.player.sendSystemMessage(
            Component.translatable("speedrunapi.gui.toast.error.description." + action, modID)
         );
      }
   }
}
