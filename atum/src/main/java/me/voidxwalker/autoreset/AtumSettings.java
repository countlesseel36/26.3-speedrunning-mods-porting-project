package me.voidxwalker.autoreset;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import me.voidxwalker.autoreset.mixin.CreateWorldScreenInvoker;
import me.voidxwalker.autoreset.screen.AutoResetOptionScreen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static me.voidxwalker.autoreset.Atum.*;

/**
 * Atum 设置存储：读取、解析与保存。
 * 从 Atum 拆分出来，行为保持不变。
 */
final class AtumSettings {
   private AtumSettings() {
   }

   static void load() {
      try {
         Files.createDirectories(configFile.getParent());
         if (!Files.exists(configFile)) {
            save();
            return;
         }

         Properties properties = new Properties();
         try (InputStream in = Files.newInputStream(configFile)) {
            properties.load(in);
         }

         for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = (String) entry.getKey();
            if (!key.equals("seed")
               && !key.equals("difficulty")
               && !key.equals("generatorType")
               && !key.equals("rsgAttempts")
               && !key.equals("ssgAttempts")
               && !key.equals("structures")
               && !key.equals("bonusChest")) {
               EXTRA_PROPERTIES.put(key, (String) entry.getValue());
            }
         }

         seed = properties.getProperty("seed", "").trim();
         difficulty = parseInt(properties.getProperty("difficulty"), 1, 4);
         LOGGER.info("[atum] config loaded: difficulty={} generatorType={} (resolveDifficulty={})",
            difficulty, generatorType, difficulty == -1 ? "HARDCORE" : difficultyOf(difficulty));
         generatorType = parseInt(properties.getProperty("generatorType"), 0, 7);
         rsgAttempts = Math.max(0, parseInt(properties.getProperty("rsgAttempts"), 0, Integer.MAX_VALUE));
         ssgAttempts = Math.max(0, parseInt(properties.getProperty("ssgAttempts"), 0, Integer.MAX_VALUE));
         structures = !properties.containsKey("structures") || Boolean.parseBoolean(properties.getProperty("structures"));
         bonusChest = Boolean.parseBoolean(properties.getProperty("bonusChest"));
      } catch (IOException e) {
         LOGGER.error("could not load the Atum config", e);
      }
   }
   static int parseInt(String value, int min, int max) {
      try {
         int parsed = Integer.parseInt(value);
         return parsed >= min && parsed < max ? parsed : min == 1 && max == 4 ? 1 : 0;
      } catch (NumberFormatException e) {
         return min == 1 && max == 4 ? 1 : 0;
      }
   }
   public static void notifySettingsChanged() {
      try {
         Class<?> seedqueue = Class.forName("me.contaria.seedqueue.SeedQueue");
         seedqueue.getMethod("discardAndRebuildForSettingsChange").invoke(null);
      } catch (ClassNotFoundException ignored) {
         // 没装 SeedQueue：无需处理
      } catch (Throwable t) {
         LOGGER.warn("[atum] failed to notify SeedQueue about a settings change", t);
      }
   }
   public static void save() {
      Properties properties = new Properties();
      properties.put("seed", seed == null ? "" : seed);
      properties.put("difficulty", Integer.toString(difficulty));
      properties.put("generatorType", Integer.toString(generatorType));
      properties.put("structures", Boolean.toString(structures));
      properties.put("bonusChest", Boolean.toString(bonusChest));
      properties.put("rsgAttempts", Integer.toString(rsgAttempts));
      properties.put("ssgAttempts", Integer.toString(ssgAttempts));
      properties.putAll(EXTRA_PROPERTIES);

      try (OutputStream out = Files.newOutputStream(configFile)) {
         properties.store(
            out,
            "Atum config (26.3 port)\n"
               + "seed: leave empty for a random seed\n"
               + "difficulty: -1 = HARDCORE, 0 = PEACEFUL, 1 = EASY, 2 = NORMAL, 3 = HARD\n"
               + "generatorType: 0 = DEFAULT, 1 = FLAT, 2 = LARGE_BIOMES, 3 = AMPLIFIED, 4 = SINGLE_BIOME_SURFACE, "
               + "5 = SINGLE_BIOME_CAVES, 6 = SINGLE_BIOME_FLOATING_ISLANDS");
      } catch (IOException e) {
         LOGGER.error("could not save the Atum config", e);
      }
   }
}
