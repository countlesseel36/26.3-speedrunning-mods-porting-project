package me.voidxwalker.autoreset.mixin;

import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
   @Inject(method = "init", at = @At("TAIL"))
   private void atum$configureWorld(CallbackInfo ci) {
      if (!Atum.configuring) {
         return;
      }

      Atum.configuring = false;
      CreateWorldScreen self = (CreateWorldScreen) (Object) this;
      Atum.configureWorld(self);
      Atum.pendingCreate = self;
   }
}
