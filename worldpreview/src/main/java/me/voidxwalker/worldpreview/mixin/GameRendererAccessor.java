package me.voidxwalker.worldpreview.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
   @Accessor("mainRenderTarget")
   RenderTarget worldpreview$mainRenderTarget();

   @Accessor("gameRenderState")
   net.minecraft.client.renderer.state.GameRenderState worldpreview$gameRenderState();
}
