package me.voidxwalker.autoreset.mixin;

import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Key bindings are not processed while a screen swallows the input, so the reset key would be lost
 * during the loading preview. Capture it here and schedule the reset, which Atum then performs by
 * aborting the load.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
   @Inject(method = "keyPress", at = @At("HEAD"))
   private void atum$captureResetKey(long window, int key, KeyEvent event, CallbackInfo ci) {
      // F6 must work globally: the keyboard handler runs before screen input handling, so this
      // also catches the key while the loading preview, the create-world screen, chat or any
      // other screen would otherwise swallow it.
      if (Atum.resetKey != null && Atum.resetKey.matches(event)) {
         Atum.scheduleReset();
      }
   }
}