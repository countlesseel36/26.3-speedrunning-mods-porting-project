package me.voidxwalker.autoreset.mixin;

import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Appends the configured seed to the loading screen. */
@Mixin(LevelLoadingScreen.class)
public class LevelLoadingScreenMixin {
   @Inject(method = "extractRenderState", at = @At("TAIL"))
   private void atum$drawSeed(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
      LevelLoadingScreen self = (LevelLoadingScreen) (Object) this;
      if (Atum.isRunning && Atum.seed != null && !Atum.seed.isEmpty()) {
         graphics.centeredText(
            self.getFont(),
            Component.literal("(" + Atum.seed + ")"),
            self.width / 2,
            self.height / 2 + 20,
            -1
         );
      }
   }
}
