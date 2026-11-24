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
 * 世界创建：新建世界、预设选择、难度/地形名称与选项界面。
 * 从 Atum 拆分出来，行为保持不变。
 */
final class WorldCreationHelper {
   private WorldCreationHelper() {
   }

   public static void createNewWorld(Minecraft minecraft) {
      if (creatingWorld || pendingCreate != null) {
         return;
      }

      shouldReset = false;
      isRunning = true;
      configuring = true;
      creatingWorld = true;
      // Do not bounce back to the title screen when the create screen closes: the world loading
      // screen replaces it and a title flash would restart the reset chain.
      CreateWorldScreen.openFresh(minecraft, () -> { });
   }
   public static void configureWorld(CreateWorldScreen screen) {
      WorldCreationUiState uiState = screen.getUiState();

      if (seed != null && !seed.isEmpty()) {
         uiState.setSeed(seed);
         ssgAttempts++;
      } else {
         uiState.setSeed("");
         rsgAttempts++;
      }

      if (difficulty == -1) {
         uiState.setGameMode(WorldCreationUiState.SelectedGameMode.HARDCORE);
         uiState.setDifficulty(Difficulty.HARD);
      } else {
         uiState.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
         uiState.setDifficulty(difficultyOf(difficulty));
      }

      uiState.setGenerateStructures(structures);
      uiState.setBonusChest(bonusChest);
      WorldCreationUiState.WorldTypeEntry entry = presetFor(uiState, generatorType);
      uiState.setWorldType(entry);
      LOGGER.info("[atum] configure world: difficulty={} -> {} generatorType={} -> {}",
         difficulty,
         difficulty == -1 ? "HARDCORE(HARD)" : difficultyOf(difficulty),
         generatorType,
         entry.preset().unwrapKey().orElse(null));
      uiState.setName(seed != null && !seed.isEmpty() ? "Set Speedrun #" + ssgAttempts : "Random Speedrun #" + rsgAttempts);

      save();
   }
   public static void openOptionsScreen(Screen parent) {
      Minecraft minecraft = Minecraft.getInstance();
      minecraft.gui.setScreen(new AutoResetOptionScreen(parent));
   }
   static WorldCreationUiState.WorldTypeEntry presetFor(WorldCreationUiState uiState, int type) {
      List<WorldCreationUiState.WorldTypeEntry> normal = uiState.getNormalPresetList();
      List<WorldCreationUiState.WorldTypeEntry> alt = uiState.getAltPresetList();

      // 列表顺序来自注册表顺序（按字母排），index 与 generatorName 的编号并不一致：
      // 例如 index 0 实际是 amplified。这里优先按名字匹配，保证"显示什么就用什么"。
      String wanted = getGeneratorTypeString(type);

      if (wanted != null) {
         for (WorldCreationUiState.WorldTypeEntry candidate : normal) {
            if (matchesPreset(candidate, wanted)) {
               return candidate;
            }
         }

         for (WorldCreationUiState.WorldTypeEntry candidate : alt) {
            if (matchesPreset(candidate, wanted)) {
               return candidate;
            }
         }
      }

      LOGGER.info("[atum] preset list: normal={} alt={} requestedIndex={}",
         normal.stream().map(e -> e.preset().unwrapKey().orElse(null)).toList(),
         alt.stream().map(e -> e.preset().unwrapKey().orElse(null)).toList(),
         type);

      if (type >= 0 && type < normal.size()) {
         return normal.get(type);
      }

      int altIndex = type - normal.size();
      if (altIndex >= 0 && altIndex < alt.size()) {
         return alt.get(altIndex);
      }

      return normal.isEmpty() ? new WorldCreationUiState.WorldTypeEntry(null) : normal.get(0);
   }
   static boolean matchesPreset(WorldCreationUiState.WorldTypeEntry entry, String name) {
      return entry.preset().unwrapKey()
         .map(key -> key.identifier().getPath().equals(name))
         .orElse(false);
   }
   public static Difficulty difficultyOf(int id) {
      return switch (id) {
         case 0 -> Difficulty.PEACEFUL;
         case 2 -> Difficulty.NORMAL;
         case 3 -> Difficulty.HARD;
         default -> Difficulty.EASY;
      };
   }
   public static String getGeneratorTypeString(int type) {
      return switch (type) {
         case 0 -> "normal";
         case 1 -> "flat";
         case 2 -> "large_biomes";
         case 3 -> "amplified";
         case 4 -> "single_biome_surface";
         case 5 -> "single_biome_caves";
         case 6 -> "single_biome_floating_islands";
         default -> null;
      };
   }
   public static Component text(String key, String fallback) {
      return Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(fallback);
   }
   public static Component difficultyName(int id) {
      if (id == -1) {
         return Component.translatable("selectWorld.gameMode.hardcore");
      }

      return difficultyOf(id).getDisplayName();
   }
   public static Component generatorName(int id) {
      String name = getGeneratorTypeString(id);
      if (name == null) {
         return Component.literal("?");
      }

      // 26.3 renamed the world preset translations to "generator.<namespace>.<path>", so the
      // old "generator.<path>" key no longer resolves. Try both, then fall back to a readable
      // literal so the button never shows a raw translation key.
      for (String key : new String[] {"generator.minecraft." + name, "generator." + name}) {
         Component candidate = Component.translatable(key);
         if (!candidate.getString().equals(key)) {
            return candidate;
         }
      }

      return Component.literal(name);
   }
}
