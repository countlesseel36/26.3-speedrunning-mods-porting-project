package me.voidxwalker.worldpreview.mixin;

import me.voidxwalker.worldpreview.WorldPreview;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.oit.OitStage;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostics for the external preview: where does Sodium draw its chunks? */
@Mixin(SodiumWorldRenderer.class)
public class SodiumDrawDebugMixin {
   private static final java.util.concurrent.atomic.AtomicInteger DRAWS = new java.util.concurrent.atomic.AtomicInteger();

   @Inject(
      method = "drawChunkLayer(Lcom/mojang/renderpearl/api/commands/RenderPass;Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;DDDLcom/mojang/renderpearl/api/textures/GpuSampler;Lnet/minecraft/client/renderer/oit/OitStage;)V",
      at = @At("HEAD")
   )
   private void worldpreview$logDraw(
      RenderPass pass,
      ChunkSectionLayerGroup group,
      ChunkRenderMatrices matrices,
      double x,
      double y,
      double z,
      GpuSampler sampler,
      OitStage stage,
      CallbackInfo ci
   ) {
      if (!WorldPreview.forceLevelRender) {
         return;
      }

      int n = DRAWS.getAndIncrement();

      if (n < 16) {
         net.minecraft.world.phys.Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
         WorldPreview.LOGGER.info(
            "[preview] sodium draw #{} group={} camera={},{},{} matricesTranslation={},{},{} chunkOrigin={},{},{}",
            n,
            group,
            String.format("%.1f", camera.x),
            String.format("%.1f", camera.y),
            String.format("%.1f", camera.z),
            String.format("%.1f", matrices.modelView().m30()),
            String.format("%.1f", matrices.modelView().m31()),
            String.format("%.1f", matrices.modelView().m32()),
            x,
            y,
            z
         );
      }
   }
}