package me.voidxwalker.worldpreview.mixin;

import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The preview has to be built once the integrated server has created its levels. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
   @Inject(method = "prepareLevels", at = @At("HEAD"))
   private void worldpreview$onPrepareLevels(CallbackInfo ci) {
      WorldPreview.onServerPreparingLevels((MinecraftServer) (Object) this);
   }
}
