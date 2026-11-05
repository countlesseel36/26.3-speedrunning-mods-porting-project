package me.voidxwalker.worldpreview.mixin;

import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the dark disc that shows up beyond the small preview render distance. */
@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
   @Inject(method = "render", at = @At("HEAD"))
   private void worldpreview$hideDarkDisc(com.mojang.renderpearl.api.buffers.GpuBufferSlice buffer, SkyRenderState state, CallbackInfo ci) {
      if ((WorldPreview.isActive() || WorldPreview.forceLevelRender) && state != null) {
         state.shouldRenderDarkDisc = false;
      }
   }
}
