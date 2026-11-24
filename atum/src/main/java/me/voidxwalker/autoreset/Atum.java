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
   static boolean abortTriggered;
   static boolean restoreFastResetSaving;
   static int restoreFastResetSavingTicks;
   /** True while Atum is injecting a synthetic key event, so handlers can ignore it. */
   public static volatile boolean injectingKey;
   /**
    * SeedQueue takes over joining, resetting and the F6 key while it is installed: Atum then only
    * provides its create-world settings (difficulty, structures, bonus chest) which SeedQueue reads.
    * Without SeedQueue, Atum behaves like upstream.
    */
   public static final boolean SEEDQUEUE_PRESENT = net.fabricmc.loader.api.FabricLoader.getInstance()
      .isModLoaded("seedqueue");
   /** Single-world reset and its key are disabled whenever SeedQueue is installed. */
   public static final boolean RESET_KEY_ENABLED = !SEEDQUEUE_PRESENT && Boolean.getBoolean("atum.resetKeyEnabled");


   public static String seed = "";
   public static int difficulty = 1;
   public static int generatorType;
   public static boolean structures = true;
   public static boolean bonusChest;
   public static int rsgAttempts;
   public static int ssgAttempts;


   /** true while a world we opened ourselves still has to be configured. */
   public static boolean configuring;
   /** the create-world screen that still has to be submitted. */
   public static CreateWorldScreen pendingCreate;
   /** true while Atum is opening a fresh create-world screen (prevents duplicate openings). */
   public static boolean creatingWorld;

   static final Map<String, String> EXTRA_PROPERTIES = new LinkedHashMap<>();
   static Path configFile;

   @Override
   public void onInitialize() {
      LOGGER.info("Initializing Atum (26.3 port)");

      KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("atum", "keys"));
      // With SeedQueue installed the reset key is unbound entirely (SeedQueue owns F6 as "back to
      // wall"); without SeedQueue it stays on F6 like upstream.
      resetKey = new KeyMapping(
         "key.atum.reset",
         RESET_KEY_ENABLED ? InputConstants.KEY_F6 : InputConstants.UNKNOWN.getValue(),
         category
      );

      // options.txt still holds the UNKNOWN we wrote while SeedQueue was installed, so removing
      // SeedQueue used to leave the reset key permanently unbound ("the key does nothing"). Restore
      // the upstream default when the mapping comes back unbound.
      if (RESET_KEY_ENABLED
         && Boolean.parseBoolean(System.getProperty("atum.restoreDefaultResetKey", "true"))
         && resetKey.saveString().contains("unknown")) {
         resetKey.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F6));
         KeyMapping.resetMapping();
         LOGGER.info("[atum] reset key was unbound, restored the F6 default");
      }

      configFile = FabricLoader.getInstance().getConfigDir().resolve("atum").resolve("atum.properties");
      load();
   }

   // ------------------------------------------------------------------ state


   public static  void scheduleReset() {
      ResetController.scheduleReset();
   }


   public static  boolean shouldReset() {
      return ResetController.shouldReset();
   }

   public static  boolean isBlocking() {
      return ResetController.isBlocking();
   }

   public static  boolean isLoadingWorld() {
      return ResetController.isLoadingWorld();
   }

   // ------------------------------------------------------------------ tick

   static String lastDebugScreen;

   /** Called at the end of {@code Minecraft#runTick}. */
   public static  void onClientTick() {
      ResetController.onClientTick();
   }

   private static  void abortWorldPreview() {
      ResetController.abortWorldPreview();
   }

   private static  void disableFastResetSaving() {
      ResetController.disableFastResetSaving();
   }

   private static  void restoreFastResetSaving() {
      ResetController.restoreFastResetSaving();
   }

   /** Leaves the current world by pressing the "quit to title" button of the pause menu. */
   private static  void quitToTitle(Minecraft minecraft) {
      ResetController.quitToTitle(minecraft);
   }


   private static  Button findQuitButton(Screen screen) {
      return ResetController.findQuitButton(screen);
   }

   // ------------------------------------------------------------- new world

   public static  void createNewWorld(Minecraft minecraft) {
      WorldCreationHelper.createNewWorld(minecraft);
   }

   /** Applies the configured settings to the create-world screen and schedules its submission. */
   public static  void configureWorld(CreateWorldScreen screen) {
      WorldCreationHelper.configureWorld(screen);
   }

   public static  void openOptionsScreen(Screen parent) {
      WorldCreationHelper.openOptionsScreen(parent);
   }

   private static  WorldCreationUiState.WorldTypeEntry presetFor(WorldCreationUiState uiState, int type) {
      return WorldCreationHelper.presetFor(uiState, type);
   }

   private static  boolean matchesPreset(WorldCreationUiState.WorldTypeEntry entry, String name) {
      return WorldCreationHelper.matchesPreset(entry, name);
   }

   public static  Difficulty difficultyOf(int id) {
      return WorldCreationHelper.difficultyOf(id);
   }

   public static  String getGeneratorTypeString(int type) {
      return WorldCreationHelper.getGeneratorTypeString(type);
   }

   /**
    * 26.3 only loads mod-provided lang files when Fabric API's resource loader is present, so
    * translate when possible and fall back to readable English otherwise (never show a raw key).
    */
   public static  Component text(String key, String fallback) {
      return WorldCreationHelper.text(key, fallback);
   }
   public static  Component difficultyName(int id) {
      return WorldCreationHelper.difficultyName(id);
   }

   public static  Component generatorName(int id) {
      return WorldCreationHelper.generatorName(id);
   }
   // ----------------------------------------------------------------- config

   private static  void load() {
      AtumSettings.load();
   }

   private static  int parseInt(String value, int min, int max) {
      return AtumSettings.parseInt(value, min, max);
   }

   /** 设置变化后通知 SeedQueue 丢弃旧实例（旧实例是用旧设置生成的）。 */
   public static  void notifySettingsChanged() {
      AtumSettings.notifySettingsChanged();
   }

   public static  void save() {
      AtumSettings.save();
   }
}

