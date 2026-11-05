package me.voidxwalker.worldpreview.mixin;

import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Preparing for world creation..." must not show the title panorama either. */
@Mixin(GenericMessageScreen.class)
public abstract class GenericMessageScreenMixin {
   @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
   private void worldpreview$hideBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
      if (WorldPreview.shouldHideBackground()) {
         Minecraft minecraft = Minecraft.getInstance();
         graphics.fill(0, 0, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), 0xFF000000);
         ci.cancel();
      }
   }
}