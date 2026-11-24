package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

@Mixin({GameRenderer.class})
public class GameRendererResizeMixin {
   @Inject(
      method = {"resize"},
      at = {@At("TAIL")}
   )
   private void seedqueue$resizePreviewTargets(int count, int count2, CallbackInfo callback) {
      SeedQueue.onWindowResize(count, count2);
   }
}
