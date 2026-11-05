package me.voidxwalker.worldpreview.mixin;

import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the preview frame right before the GUI is drawn. */
@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private void worldpreview$captureAndSkipGui(CallbackInfo ci) {
      Minecraft minecraft = Minecraft.getInstance();
      WorldPreview.performMainCopy(minecraft);
      WorldPreview.captureFrame(minecraft);

      if (WorldPreview.instanceRendering) {
         ci.cancel();
      }
   }
}
