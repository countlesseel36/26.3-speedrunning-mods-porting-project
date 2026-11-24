package me.contaria.seedqueue.mixin;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder.Inspector;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import java.util.List;
import me.contaria.seedqueue.wall.WallPreview;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

@Mixin({FrameGraphBuilder.class})
public abstract class FrameGraphMixin {
   @Shadow
   @Final
   private List<?> passes;

   @Inject(
      method = {"importExternal"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void seedqueue$redirectMain(String text, Object obj, CallbackInfoReturnable<ResourceHandle<?>> callback) {
      RenderTarget target = WallPreview.activeTarget();
      if (target != null && !WallPreview.redirecting()) {
         WallPreview.logImport(text, obj);
         if ("main".equals(text)) {
            WallPreview.setRedirecting(true);

            try {
               callback.setReturnValue((((FrameGraphBuilder)(Object)this)).importExternal(text, target));
            } finally {
               WallPreview.setRedirecting(false);
            }
         }
      }
   }

   @Inject(
      method = {"execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V"},
      at = {@At("HEAD")}
   )
   private void seedqueue$logPasses(GraphicsResourceAllocator graphicsResourceAllocator, Inspector inspector, CallbackInfo callback) {
      WallPreview.logPasses(this.passes);
   }
}
