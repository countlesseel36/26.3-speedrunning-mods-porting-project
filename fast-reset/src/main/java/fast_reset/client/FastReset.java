package fast_reset.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * FastReset - ported to Minecraft 26.3.
 *
 * <p>The mod keeps a single global flag, {@link #saveOnQuit}. While it is {@code false} every
 * world/player/chunk save that the integrated server performs during shutdown is skipped, which
 * turns "quit to title" into an instant operation.</p>
 */
public class FastReset implements ModInitializer {
   public static final Logger LOGGER = LogManager.getLogger("FastReset");

   /** false while a save-less quit is in progress. Cross-thread, hence volatile. */
   public static volatile boolean saveOnQuit = true;
   /**
    * True only while the "Quit to Title" button is disconnecting. Saves are skipped for that window
    * only, so a shutdown hook or a crash still writes the world.
    */
   public static volatile boolean skipSavesNow;

   /** 0 = bottom right, 1 = centered (same as the original mod). */
   public static int buttonLocation;

   private static Path configurationFile;

   @Override
   public void onInitialize() {
      saveOnQuit = true;

      Path directory = FabricLoader.getInstance().getConfigDir().resolve("fastReset");
      configurationFile = directory.resolve("settings.txt");

      try {
         if (!Files.exists(configurationFile)) {
            Files.createDirectories(directory);
            Files.writeString(configurationFile, "0");
            buttonLocation = 0;
         } else {
            String line = Files.readString(configurationFile).trim();
            try {
               buttonLocation = Integer.parseInt(line);
            } catch (NumberFormatException e) {
               buttonLocation = 0;
            }
         }
      } catch (IOException e) {
         LOGGER.error("could not read the FastReset config", e);
         buttonLocation = 0;
      }

      buttonLocation = Math.max(0, Math.min(1, buttonLocation));
      LOGGER.info("Using Fast Reset (26.3 port)");
   }

   /** Label of the quit button; falls back to plain English when no mod lang file is loaded. */
   public static net.minecraft.network.chat.Component quitLabel() {
      return net.minecraft.locale.Language.getInstance().has("menu.quitWorld")
         ? net.minecraft.network.chat.Component.translatable("menu.quitWorld")
         : net.minecraft.network.chat.Component.literal("Quit to Title");
   }
   public static void updateButtonLocation() {
      buttonLocation = (buttonLocation + 1) % 2;
      try {
         Files.writeString(configurationFile, Integer.toString(buttonLocation));
      } catch (IOException e) {
         LOGGER.error("could not save the FastReset config", e);
      }
   }
}
