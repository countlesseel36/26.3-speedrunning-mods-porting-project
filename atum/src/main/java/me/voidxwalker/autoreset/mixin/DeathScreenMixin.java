package me.voidxwalker.autoreset.mixin;

import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leaving the death screen to the title stops the reset chain. */
@Mixin(DeathScreen.class)
public class DeathScreenMixin {
   @Inject(method = "exitToTitleScreen", at = @At("HEAD"))
   private void atum$stopResetting(CallbackInfo ci) {
      Atum.isRunning = false;
      Atum.shouldReset = false;
   }
}
