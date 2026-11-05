package me.voidxwalker.worldpreview.mixin;

import java.util.List;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a preview level is swapped in (SeedQueue wall), strips the world-specific F3 lines so the
 * overlay looks like it does on the title screen instead of describing the preview instance.
 */
@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {
   @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
   private void worldpreview$suppressDebug(GuiGraphicsExtractor graphics, CallbackInfo ci) {
      if (WorldPreview.suppressDebugOverlay) {
         ci.cancel();
      }
   }

   @Inject(method = "extractLines", at = @At("HEAD"))
   private void worldpreview$filterWorldLines(
      GuiGraphicsExtractor graphics, List<String> lines, boolean left, int lineHeight, CallbackInfo ci
   ) {
      if (WorldPreview.hideDebugOverlay) {
         lines.removeIf(WorldPreview::isWorldDebugLine);
      }
   }
}
