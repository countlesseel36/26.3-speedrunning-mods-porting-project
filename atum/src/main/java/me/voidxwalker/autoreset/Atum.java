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

/**
 * Atum - ported to Minecraft 26.3.
 *
 * <p>The reset itself is split into two steps that are driven from the client tick:</p>
 * <ol>
 *    <li>if a reset is scheduled and a world is loaded, leave the world through the pause menu
 *       (so FastReset's "Quit to Title" button is used when it is installed);</li>
 *    <li>once the title screen is back, open a fresh world with the configured settings and
 *       create it without any further GUI interaction.</li>
 * </ol>
 */
public class Atum implements ModInitializer {
   public static final Logger LOGGER = LogManager.getLogger("Atum");

   public static boolean isRunning;
   public static boolean shouldReset;
   public static KeyMapping resetKey;
   private static boolean abortTriggered;
   private static boolean restoreFastResetSaving;
   private static int restoreFastResetSavingTicks;
   private static boolean loadResetSmokeDone;
   private static int loadResetSmokeTicks;
   private static final boolean RESET_DURING_PREVIEW_SMOKE = Boolean.getBoolean("atum.resetDuringPreview");
   private static final boolean SIMULATE_F6_DURING_PREVIEW = Boolean.getBoolean("atum.simulateF6DuringPreview");

   public static String seed = "";
   public static int difficulty = 1;
   public static int generatorType;
   public static boolean structures = true;
   public static boolean bonusChest;
   public static int rsgAttempts;
   public static int ssgAttempts;

   private static final boolean SMOKE_TEST = Boolean.getBoolean("atum.smoke");
   private static boolean smokeTriggered;
   private static int smokeTicks;
   private static int smokeStage;
   private static boolean worldSmokeTriggered;
   private static int worldSmokeTicks;

   /** true while a world we opened ourselves still has to be configured. */
   public static boolean configuring;
   /** the create-world screen that still has to be submitted. */
   public static CreateWorldScreen pendingCreate;
   /** true while Atum is opening a fresh create-world screen (prevents duplicate openings). */
   public static boolean creatingWorld;

   private static final Map<String, String> EXTRA_PROPERTIES = new LinkedHashMap<>();
   private static Path configFile;

   @Override
   public void onInitialize() {
      LOGGER.info("Initializing Atum (26.3 port)");

      KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("atum", "keys"));
      resetKey = new KeyMapping("key.atum.reset", InputConstants.KEY_F6, category);

      configFile = FabricLoader.getInstance().getConfigDir().resolve("atum").resolve("atum.properties");
      load();
   }

   // ------------------------------------------------------------------ state


   public static void scheduleReset() {

      shouldReset = true;
   }

   public static boolean isResetScheduled() {
      return shouldReset;
   }

   public static boolean shouldReset() {
      return shouldReset && !isBlocking();
   }

   public static boolean isBlocking() {
      Minecraft minecraft = Minecraft.getInstance();
      return minecraft.gui.overlay() != null
         || (minecraft.getSingleplayerServer() != null && minecraft.level == null);
   }

   public static boolean isLoadingWorld() {
      Minecraft minecraft = Minecraft.getInstance();
      return minecraft.getSingleplayerServer() != null && minecraft.level == null;
   }

   // ------------------------------------------------------------------ tick

   private static String lastDebugScreen;

   /** Called at the end of {@code Minecraft#runTick}. */
   public static void onClientTick() {
      Minecraft minecraft = Minecraft.getInstance();

      if (Boolean.getBoolean("atum.debugScreens")) {
         String screenName = minecraft.gui.screen() == null ? "null" : minecraft.gui.screen().getClass().getSimpleName();

         if (!screenName.equals(lastDebugScreen)) {
            lastDebugScreen = screenName;
            LOGGER.info(
               "[atum] screen -> {} level={} shouldReset={} running={} loading={}",
               screenName,
               minecraft.level != null,
               shouldReset,
               isRunning,
               isLoadingWorld()
            );
         }
      }

      if (resetKey != null) {
         while (resetKey.consumeClick()) {
            scheduleReset();
         }
      }

      // Track when the world/create-world transition is over so a new reset can be opened.
      Screen currentScreen = minecraft.gui.screen();

      if (minecraft.level != null || currentScreen instanceof LevelLoadingScreen) {
         creatingWorld = false;
      }

      if (pendingCreate != null && currentScreen != pendingCreate && !(currentScreen instanceof LevelLoadingScreen)) {
         // the create screen was closed before it submitted, treat it as cancelled
         pendingCreate = null;
         configuring = false;
         creatingWorld = false;
      }

      if (SMOKE_TEST && !smokeTriggered) {
         if (smokeStage == 0 && !shouldReset && minecraft.level == null && minecraft.gui.overlay() == null && minecraft.gui.screen() instanceof TitleScreen) {
            smokeStage = 1;
         }

         if (smokeStage >= 1 && smokeStage <= 3) {
            smokeTicks++;

            if (smokeStage == 1 && smokeTicks > 60) {
               LOGGER.info("[smoke] screenshot: title screen");
               net.minecraft.client.Screenshot.grab(minecraft, false);
               LOGGER.info("[smoke] opening the autoreset options screen");
               openOptionsScreen(minecraft.gui.screen());
               smokeStage = 2;
            } else if (smokeStage == 2 && smokeTicks > 80) {
               if (minecraft.gui.screen() instanceof AutoResetOptionScreen optionsScreen) {
                  LOGGER.info("[smoke] screenshot: options screen");
                  net.minecraft.client.Screenshot.grab(minecraft, false);
                  LOGGER.info("[smoke] closing the autoreset options screen");
                  optionsScreen.onClose();
               }

               smokeStage = 3;
            } else if (smokeStage == 3 && smokeTicks > 120) {
               smokeTriggered = true;
               LOGGER.info("[smoke] scheduling a reset from the title screen");
               scheduleReset();
            }
         }
      }

      if (SMOKE_TEST && !worldSmokeTriggered && minecraft.level != null) {
         if (++worldSmokeTicks > 100) {
            worldSmokeTriggered = true;
            LOGGER.info("[smoke] scheduling an in-world reset");
            scheduleReset();
         }
      }
      if (RESET_DURING_PREVIEW_SMOKE && !loadResetSmokeDone && isLoadingWorld()
         && minecraft.gui.screen() instanceof LevelLoadingScreen) {
         if (++loadResetSmokeTicks > 40) {
            loadResetSmokeDone = true;
            LOGGER.info("[smoke] pressing F6 during the world preview");
            if (SIMULATE_F6_DURING_PREVIEW) {
               minecraft.keyboardHandler.keyPress(
                  minecraft.getWindow().handle(),
                  InputConstants.KEY_F6,
                  new net.minecraft.client.input.KeyEvent(InputConstants.KEY_F6, 0, 0)
               );
            } else {
               scheduleReset();
            }
         }
      }

      // The old server keeps saving after the client reference is cleared, so wait a moment
      // before allowing saves again.
      if (restoreFastResetSaving && --restoreFastResetSavingTicks <= 0) {
         restoreFastResetSaving = false;
         restoreFastResetSaving();
      }

      if (!isLoadingWorld()) {
         abortTriggered = false;
      }

      if (shouldReset && isLoadingWorld()) {
         if (!abortTriggered) {
            abortTriggered = true;
            LOGGER.info("[atum] reset requested while a world is loading, aborting the current load");
            abortWorldPreview();
            disableFastResetSaving();

            try {
               minecraft.disconnect(new TitleScreen(), false);
            } catch (Throwable t) {
               LOGGER.warn("[atum] disconnect during load failed, stopping the server instead", t);

               try {
                  var server = minecraft.getSingleplayerServer();

                  if (server != null) {
                     server.halt(true);
                  }
               } catch (Throwable inner) {
                  LOGGER.warn("[atum] halting the server failed", inner);
               }

               minecraft.gui.setScreen(new TitleScreen());
            }
         }

         return;
      }

      if (pendingCreate != null && minecraft.gui.screen() == pendingCreate) {
         CreateWorldScreen screen = pendingCreate;
         pendingCreate = null;
         ((CreateWorldScreenInvoker) screen).atum$onCreate();
         return;
      }

      if (!shouldReset()) {
         return;
      }

      if (minecraft.level != null) {
         quitToTitle(minecraft);
         return;
      }

      if (isBlocking()) {
         return;
      }

      Screen screen = minecraft.gui.screen();
      if (screen != null && !(screen instanceof TitleScreen)) {
         // F6 is global: close whatever menu is open (options, create world, ...) and go back to
         // the title screen so the next tick can open a fresh world.
         if (screen instanceof CreateWorldScreen && (creatingWorld || configuring || pendingCreate != null)) {
            return;
         }

         if (screen instanceof CreateWorldScreen) {
            pendingCreate = null;
            configuring = false;
            creatingWorld = false;
         }

         minecraft.gui.setScreen(null);
         return;
      }

      if (creatingWorld || configuring || pendingCreate != null) {
         return;
      }

      createNewWorld(minecraft);
   }

   private static void abortWorldPreview() {
      try {
         Class.forName("me.voidxwalker.worldpreview.WorldPreview").getMethod("abort").invoke(null);
      } catch (Throwable ignored) {
      }
   }

   private static void disableFastResetSaving() {
      try {
         Class.forName("fast_reset.client.FastReset").getField("saveOnQuit").setBoolean(null, false);
         restoreFastResetSaving = true;
         restoreFastResetSavingTicks = 120;
         LOGGER.info("[atum] FastReset save skipping enabled for the aborted world");
      } catch (Throwable t) {
         LOGGER.warn("[atum] could not enable FastReset save skipping", t);
      }
   }

   private static void restoreFastResetSaving() {
      try {
         Class.forName("fast_reset.client.FastReset").getField("saveOnQuit").setBoolean(null, true);
         LOGGER.info("[atum] FastReset saving restored");
      } catch (Throwable t) {
         LOGGER.warn("[atum] could not restore FastReset saving", t);
      }
   }

   /** Leaves the current world by pressing the "quit to title" button of the pause menu. */
   private static void quitToTitle(Minecraft minecraft) {
      try {
         PauseScreen pauseScreen = new PauseScreen(true);
         pauseScreen.init(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());

         Button quit = findQuitButton(pauseScreen);
         if (quit != null) {
            quit.onPress(null);
         }

         if (minecraft.level != null) {
            minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
         }
      } catch (Exception e) {
         LOGGER.error("could not leave the world, disconnecting directly", e);
         minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
      }
   }


   private static Button findQuitButton(Screen screen) {
      String[] keys = {"menu.quitWorld", "Quit to Title", "menu.returnToMenu", "menu.disconnect"};

      for (String key : keys) {
         String label = Component.translatable(key).getString();
         for (GuiEventListener element : screen.children()) {
            if (element instanceof Button button && button.getMessage().getString().equals(label)) {
               return button;
            }
         }
      }

      return null;
   }

   // ------------------------------------------------------------- new world

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

   /** Applies the configured settings to the create-world screen and schedules its submission. */
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
      uiState.setWorldType(presetFor(uiState, generatorType));
      uiState.setName(seed != null && !seed.isEmpty() ? "Set Speedrun #" + ssgAttempts : "Random Speedrun #" + rsgAttempts);

      save();
   }

   public static void openOptionsScreen(Screen parent) {
      Minecraft minecraft = Minecraft.getInstance();
      minecraft.gui.setScreen(new AutoResetOptionScreen(parent));
   }

   private static WorldCreationUiState.WorldTypeEntry presetFor(WorldCreationUiState uiState, int type) {
      List<WorldCreationUiState.WorldTypeEntry> normal = uiState.getNormalPresetList();
      List<WorldCreationUiState.WorldTypeEntry> alt = uiState.getAltPresetList();

      if (type >= 0 && type < normal.size()) {
         return normal.get(type);
      }

      int altIndex = type - normal.size();
      if (altIndex >= 0 && altIndex < alt.size()) {
         return alt.get(altIndex);
      }

      return normal.isEmpty() ? new WorldCreationUiState.WorldTypeEntry(null) : normal.get(0);
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

   /**
    * 26.3 only loads mod-provided lang files when Fabric API's resource loader is present, so
    * translate when possible and fall back to readable English otherwise (never show a raw key).
    */
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
   // ----------------------------------------------------------------- config

   private static void load() {
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
         generatorType = parseInt(properties.getProperty("generatorType"), 0, 7);
         rsgAttempts = Math.max(0, parseInt(properties.getProperty("rsgAttempts"), 0, Integer.MAX_VALUE));
         ssgAttempts = Math.max(0, parseInt(properties.getProperty("ssgAttempts"), 0, Integer.MAX_VALUE));
         structures = !properties.containsKey("structures") || Boolean.parseBoolean(properties.getProperty("structures"));
         bonusChest = Boolean.parseBoolean(properties.getProperty("bonusChest"));
      } catch (IOException e) {
         LOGGER.error("could not load the Atum config", e);
      }
   }

   private static int parseInt(String value, int min, int max) {
      try {
         int parsed = Integer.parseInt(value);
         return parsed >= min && parsed < max ? parsed : min == 1 && max == 4 ? 1 : 0;
      } catch (NumberFormatException e) {
         return min == 1 && max == 4 ? 1 : 0;
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

