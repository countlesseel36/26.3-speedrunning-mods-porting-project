package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Closes the wall as soon as the handed-over client level is installed. */
@Mixin(Minecraft.class)
public abstract class MinecraftSetLevelMixin {
   @Inject(method = "setLevel", at = @At("TAIL"))
   private void seedqueue$closeWallAfterJoin(ClientLevel level, CallbackInfo ci) {
      if (level != null) {
         SeedQueue.onLevelSet();
      }
   }
}
