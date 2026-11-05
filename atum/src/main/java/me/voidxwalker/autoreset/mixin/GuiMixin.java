package me.voidxwalker.autoreset.mixin;

import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the title screen while a reset is already scheduled.
 *
 * <p>When the current world is left for a reset, the client briefly installs the title screen
 * before the next create-world screen is opened. Suppressing it removes that flash (and the
 * title screen's own init work) during the reset transition.</p>
 */
@Mixin(Gui.class)
public class GuiMixin {
   @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
   private void atum$suppressTitleDuringReset(Screen screen, CallbackInfo ci) {
      if (screen instanceof TitleScreen && Atum.isRunning && Atum.shouldReset) {
         ci.cancel();
      }
   }
}
