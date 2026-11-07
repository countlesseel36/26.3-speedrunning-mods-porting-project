package me.contaria.standardsettings;

import com.mojang.blaze3d.platform.Window;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import me.contaria.standardsettings.options.StandardSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.util.Unit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StandardSettings {
   public static final Logger LOGGER = LogManager.getLogger();
   public static final boolean HAS_SODIUM = FabricLoader.getInstance().isModLoaded("sodium");
   public static StandardSettingsConfig config;
   private static StandardSettingsCache settingsCache;
   public static String lastWorld;
   public static boolean onWorldJoinPending;
   public static boolean autoF3EscPending;
   private static int oldMipmaps;
   private static int oldAnisotropyBit;
   private static TextureFilteringMethod oldTextureFiltering;

   public static void reset() {
      config.update();
      cacheMipMap();

      for (StandardSetting<?> setting : config.standardSettings) {
         setting.resetOption();
      }

      updateSettings();
      LOGGER.info("Loaded StandardSettings");
   }

   public static void onWorldJoin() {
      onWorldJoinPending = false;

      for (StandardSetting<?> setting : config.standardSettingsOnWorldJoin) {
         setting.resetOption();
      }

      updateSettings();
      LOGGER.info("Loaded StandardSettings on World Join");
   }

   private static boolean appliedAtStartup;
   private static Object lastAppliedLevel;

   /**
    * Applies the configured defaults once at startup and again whenever a world is joined.
    *
    * <p>The original mod relied on {@code Minecraft#doWorldLoad} and the level loading screen, but a
    * SeedQueue world is joined without either of them, so the settings were never applied at all.</p>
    */
   public static void tick() {
      // Escape hatch for isolating input/pause problems: -Dstandardsettings.disableAutoApply=true keeps the
      // settings from being pushed into the vanilla options automatically.
      if (Boolean.getBoolean("standardsettings.disableAutoApply")) {
         return;
      }

      Minecraft client = Minecraft.getInstance();

      if (!appliedAtStartup) {
         appliedAtStartup = true;
         reset();
      }

      if (client.level != lastAppliedLevel) {
         lastAppliedLevel = client.level;

         if (client.level != null) {
            onWorldJoin();
         }
      }
   }

   private static boolean videoModeApplied;

   public static void updateSettings() {
      Minecraft client = Minecraft.getInstance();
      Window window = client.getWindow();

      // Recreating the swap surface on every world join made the game lose its window surface
      // ("Cannot acquire minimized window") and swallow key input, so only touch it once per launch.
      if (!videoModeApplied || Boolean.getBoolean("standardsettings.reapplyVideoMode")) {
         videoModeApplied = true;
         window.changeFullscreenVideoMode();
      }
      boolean guiScaleChanged = window.getGuiScale() != (Integer)client.options.guiScale().get();

      if (guiScaleChanged) {
         client.resizeGui();
      }

      LanguageManager languageManager = client.getLanguageManager();
      if (!languageManager.getSelected().equals(client.options.languageCode)) {
         if (languageManager.getLanguage(client.options.languageCode) == null) {
            client.options.languageCode = "en_us";
         }

         languageManager.setSelected(client.options.languageCode);
         languageManager.onResourceManagerReload(client.getResourceManager());
      }

      boolean applied = guiScaleChanged;

      if (compareMipMap()) {
         client.updateMaxMipLevel((Integer)client.options.mipmapLevels().get());
         SimpleReloadInstance.create(
               client.getResourceManager(),
               List.of(client.getAtlasManager(), client.getModelManager()),
               Runnable::run,
               Runnable::run,
               CompletableFuture.completedFuture(Unit.INSTANCE),
               false
            )
            .done()
            .join();
         // Re-baseline: without this the comparison above stays true forever and every world join
         // re-ran a full synchronous resource reload (a very visible stutter when entering a world).
         cacheMipMap();
         applied = true;
      }

      if (applied) {
         KeyMapping.resetMapping();
         client.options.save();
         client.debugEntries.save();
      }
   }

   public static void createCache() {
      if (lastWorld != null && (settingsCache == null || !lastWorld.equals(settingsCache.getId()))) {
         settingsCache = new StandardSettingsCache(lastWorld);
         LOGGER.info("Cached options for '{}'", lastWorld);
      }
   }

   public static void loadCache(String worldName) {
      if (settingsCache != null && worldName.equals(settingsCache.getId())) {
         settingsCache.load();
         settingsCache = null;
         LOGGER.info("Restored cached options for '{}'", worldName);
      }
   }

   public static void resetPendingActions() {
      onWorldJoinPending = false;
      autoF3EscPending = false;
   }

   public static void saveToWorldFile(String worldName) {
      List<String> options = new ArrayList<>();

      for (StandardSetting<?> setting : config.standardSettings) {
         options.add(setting.getID() + ":" + setting.getVanilla());
      }

      for (StandardSetting<?> setting : config.standardSettingsOnWorldJoin) {
         options.add(setting.getID() + ":" + setting.get());
      }

      // Writing the file used to happen on the render thread; hand it to the background executor.
      java.util.List<String> snapshot = java.util.List.copyOf(options);
      net.minecraft.util.Util.backgroundExecutor().execute(() -> {
         try {
            Files.write(
               Minecraft.getInstance().getLevelSource().getBaseDir().resolve(worldName).resolve("standardoptions.txt"),
               snapshot,
               StandardCharsets.UTF_8
            );
            LOGGER.info("Saved standardoptions to world file.");
         } catch (IOException e) {
            LOGGER.warn("Failed to save standardoptions to world file.", e);
         }
      });
   }

   public static boolean isEnabled() {
      return config.toggleStandardSettings;
   }

   private static void cacheMipMap() {
      Options options = Minecraft.getInstance().options;
      oldMipmaps = (Integer)options.mipmapLevels().get();
      oldAnisotropyBit = (Integer)options.maxAnisotropyBit().get();
      oldTextureFiltering = (TextureFilteringMethod)options.textureFiltering().get();
   }

   private static boolean compareMipMap() {
      Options options = Minecraft.getInstance().options;
      return (Integer)options.mipmapLevels().get() != oldMipmaps
         || (Integer)options.maxAnisotropyBit().get() != oldAnisotropyBit
         || options.textureFiltering().get() != oldTextureFiltering;
   }
}
