package me.voidxwalker.worldpreview.mixin;

import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs Sodium terrain setup for the external preview right before the level is drawn and captures
 * the preview frame as soon as the level render finished (before the GUI is drawn).
 */
@Mixin(LevelRenderer.class)
public class LevelRendererDebugMixin {
   private static long seedqueue$lastLevelLog;
   @Inject(
      method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
      at = @At("HEAD")
   )
   private void worldpreview$logLevelRender(CallbackInfo ci) {
      if (Boolean.getBoolean("seedqueue.renderLog")) {
         long now = System.currentTimeMillis();

         if (now - seedqueue$lastLevelLog > 1000L) {
            seedqueue$lastLevelLog = now;
            WorldPreview.LOGGER.info(
               "[preview] LevelRenderer.render running (force={}) override={} main={}",
               WorldPreview.forceLevelRender,
               WorldPreview.targetOverride(),
               Minecraft.getInstance().gameRenderer.mainRenderTarget()
            );
         }
      }

      if (WorldPreview.forceLevelRender) {
         WorldPreview.beforeExternalRender(Minecraft.getInstance());
      }

      if (WorldPreview.instanceRendering) {
         WorldPreview.LOGGER.info("[preview] LevelRenderer.render during instance render");
      }
   }

   @Inject(
      method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
      at = @At("RETURN")
   )
   private void worldpreview$dumpLevel(CallbackInfo ci) {
      if (!Boolean.getBoolean("seedqueue.renderLog") || !WorldPreview.forceLevelRender || WorldPreview.levelDumped) {
         return;
      }

      WorldPreview.levelDumped = true;
      Minecraft minecraft = Minecraft.getInstance();
      java.io.File dump = new java.io.File("seedqueue_level.png").getAbsoluteFile();
      com.mojang.blaze3d.pipeline.RenderTarget target = WorldPreview.targetOverride();

      if (target == null) {
         target = minecraft.gameRenderer.mainRenderTarget();
      }

      net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
         try {
            image.writeToFile(dump);
            WorldPreview.LOGGER.info("[preview] level target dumped");
         } catch (Throwable t) {
            WorldPreview.LOGGER.warn("[preview] level target dump failed", t);
         }
      });
   }
}
