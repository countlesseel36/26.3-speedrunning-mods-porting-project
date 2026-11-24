package me.contaria.seedqueue.mixin.sodium;

import java.util.ArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({RenderSectionManager.class})
public abstract class RenderSectionManagerMixin {
   @Unique
   private boolean seedqueue$destroyed;

   @Inject(
      method = {"destroy"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void seedqueue$guardDestroy(CallbackInfo callback) {
      if (this.seedqueue$destroyed) {
         callback.cancel();
      } else {
         this.seedqueue$destroyed = true;
      }
   }

   @Redirect(
      method = {"collectChunkBuildResults"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/ArrayList;add(Ljava/lang/Object;)Z"
      )}
   )
   private boolean seedqueue$skipFailedBuildResult(ArrayList<Object> list, Object obj) {
      return obj != null && list.add(obj);
   }
}
