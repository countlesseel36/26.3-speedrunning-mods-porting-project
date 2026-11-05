package me.voidxwalker.worldpreview.mixin;

import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shows the preview through the loading screen, and keeps everything else (panorama, half rendered
 * real world) hidden until the screen closes.
 */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
   @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
   private void worldpreview$hideBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
      if (WorldPreview.isActive() && WorldPreview.hasFinalSpawn()) {
         ci.cancel();
         return;
      }

      if (WorldPreview.shouldHideBackground()) {
         Minecraft minecraft = Minecraft.getInstance();
         graphics.fill(0, 0, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), 0xFF000000);
         ci.cancel();
      }
   }

   @Inject(method = "onClose", at = @At("TAIL"))
   private void worldpreview$clear(CallbackInfo ci) {
      WorldPreview.clear();
      WorldPreview.onLoadingScreenClosed();
   }
}