package me.voidxwalker.autoreset.mixin;

import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
   @Inject(method = "runTick(Z)V", at = @At("TAIL"))
   private void atum$onClientTick(boolean advanceGameTime, CallbackInfo ci) {
      Atum.onClientTick();
   }
}
