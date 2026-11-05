package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the wall on screen while a queued world is handed over. {@code doWorldLoad} normally swaps
 * to a progress screen and then a {@link LevelLoadingScreen}; both are suppressed here, so clicking
 * a card never flashes the title or loading screen.
 */
@Mixin(Gui.class)
public abstract class GuiSetScreenMixin {
   @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
   private void seedqueue$keepWallDuringJoin(Screen screen, CallbackInfo ci) {
      if (!SeedQueue.suppressLoadingScreens || screen == null) {
         return;
      }

      if (screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen || screen instanceof TitleScreen) {
         if (Boolean.getBoolean("seedqueue.screenLog")) {
            SeedQueue.LOGGER.info("[seedqueue] suppressed screen {}", screen.getClass().getSimpleName());
         }

         ci.cancel();
      }
   }
}
