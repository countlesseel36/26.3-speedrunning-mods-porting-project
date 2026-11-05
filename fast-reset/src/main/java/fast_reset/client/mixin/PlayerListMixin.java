package fast_reset.client.mixin;

import fast_reset.client.FastReset;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player data is part of the save-less quit as well. */
@Mixin(PlayerList.class)
public class PlayerListMixin {
   @Inject(method = "saveAll", at = @At("HEAD"), cancellable = true)
   private void fastreset$skipPlayerSave(CallbackInfo ci) {
      if (!FastReset.saveOnQuit) {
         ci.cancel();
      }
   }
}