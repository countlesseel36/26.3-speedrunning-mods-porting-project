package me.voidxwalker.worldpreview.mixin;

import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loading an existing save must never run the preview. */
@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenFlowsMixin {
   @Inject(method = "openWorld", at = @At("HEAD"))
   private void worldpreview$markExistingWorld(String levelId, Runnable onLoad, CallbackInfo ci) {
      WorldPreview.setCreatingFreshLevel(false);
   }
}