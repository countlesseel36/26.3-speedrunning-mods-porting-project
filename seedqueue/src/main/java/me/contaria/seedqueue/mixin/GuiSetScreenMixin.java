package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

@Mixin({Gui.class})
public abstract class GuiSetScreenMixin {
   @Inject(
      method = {"setScreen"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void seedqueue$keepWallDuringJoin(Screen screen, CallbackInfo callback) {
      if (screen != null) {
         if (!SeedQueue.isTransitionPending() || !(screen instanceof TitleScreen) && !(screen instanceof PauseScreen)) {
            if (screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen) {
               Minecraft minecraft = Minecraft.getInstance();
               if (minecraft.level != null && minecraft.player != null && !SeedQueue.isTransitionPending()) {
                  SeedQueue.LOGGER.info("[seedqueue] refused to show {} (world is already playable)", screen.getClass().getSimpleName());
                  callback.cancel();
                  return;
               }
            }

            if (SeedQueue.suppressLoadingScreens) {
               if (screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen || screen instanceof TitleScreen) {
                  if (Boolean.getBoolean("seedqueue.screenLog")) {
                     SeedQueue.LOGGER.info("[seedqueue] suppressed screen {}", screen.getClass().getSimpleName());
                  }

                  callback.cancel();
               }
            }
         } else {
            SeedQueue.LOGGER.info("[seedqueue] refused to show {} while a transition is pending", screen.getClass().getSimpleName());
            callback.cancel();
         }
      }
   }
}
