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
 * 重置流程：调度、状态查询、tick 推进、退出世界与 FastReset 交互。
 * 从 Atum 拆分出来，行为保持不变。
 */
final class ResetController {
   private ResetController() {
   }

   public static void scheduleReset() {

      shouldReset = true;
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

      if (RESET_KEY_ENABLED && resetKey != null) {
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
   static void abortWorldPreview() {
      try {
         Class.forName("me.voidxwalker.worldpreview.WorldPreview").getMethod("abort").invoke(null);
      } catch (Throwable ignored) {
      }
   }
   static void disableFastResetSaving() {
      try {
         Class.forName("fast_reset.client.FastReset").getField("saveOnQuit").setBoolean(null, false);
         restoreFastResetSaving = true;
         restoreFastResetSavingTicks = 120;
         LOGGER.info("[atum] FastReset save skipping enabled for the aborted world");
      } catch (Throwable t) {
         LOGGER.warn("[atum] could not enable FastReset save skipping", t);
      }
   }
   static void restoreFastResetSaving() {
      try {
         Class.forName("fast_reset.client.FastReset").getField("saveOnQuit").setBoolean(null, true);
         LOGGER.info("[atum] FastReset saving restored");
      } catch (Throwable t) {
         LOGGER.warn("[atum] could not restore FastReset saving", t);
      }
   }
   static void quitToTitle(Minecraft minecraft) {
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
   static Button findQuitButton(Screen screen) {
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
}
