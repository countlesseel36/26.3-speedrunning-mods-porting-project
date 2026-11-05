package me.contaria.seedqueue.mixin;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import java.util.List;
import me.contaria.seedqueue.WallPreview;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirects the frame graph's {@code "main"} external resource to the instance target that is
 * currently being rendered.
 *
 * <p>{@code LevelRenderer.render} builds its graph as
 * {@code frameGraph.importExternal("main", gameRenderer.mainRenderTarget())} and every level pass
 * (clear, sky, main, post chains) resolves the attachment through the handle returned here. The
 * graph is built and executed synchronously inside {@code renderLevel}, so redirecting this single
 * call is enough to make the whole level land in our target, even when another mod bypasses
 * {@code mainRenderTarget()} or keeps a stale field.</p>
 */
@Mixin(FrameGraphBuilder.class)
public abstract class FrameGraphMixin {
   @Shadow
   @Final
   private List<?> passes;

   @Inject(method = "importExternal", at = @At("HEAD"), cancellable = true)
   private void seedqueue$redirectMain(String name, Object resource, CallbackInfoReturnable<ResourceHandle<?>> cir) {
      RenderTarget target = WallPreview.activeTarget();

      if (target == null || WallPreview.redirecting()) {
         return;
      }

      WallPreview.logImport(name, resource);

      if (!"main".equals(name)) {
         return;
      }

      // Re-enter importExternal with our target; the guard keeps the second call from recursing.
      WallPreview.setRedirecting(true);

      try {
         cir.setReturnValue(((FrameGraphBuilder)(Object)this).importExternal(name, target));
      } finally {
         WallPreview.setRedirecting(false);
      }
   }

   @Inject(
      method = "execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V",
      at = @At("HEAD")
   )
   private void seedqueue$logPasses(GraphicsResourceAllocator allocator, FrameGraphBuilder.Inspector inspector, CallbackInfo ci) {
      WallPreview.logPasses(this.passes);
   }
}
