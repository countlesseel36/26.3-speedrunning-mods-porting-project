package me.voidxwalker.worldpreview.mixin;

import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * onCreate is only reached when the player actually creates a new world (Atum resets invoke it as
 * well), so the preview is armed here instead of in the server: opening an existing save never
 * touches this method.
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
   @Inject(method = "onCreate", at = @At("HEAD"))
   private void worldpreview$armPreview(CallbackInfo ci) {
      WorldPreview.setCreatingFreshLevel(true);
   }
}