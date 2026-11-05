package me.voidxwalker.worldpreview.mixin;

import me.voidxwalker.worldpreview.SodiumCompat;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the preview renderer state in sync and captures preview frames on request. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
   @Shadow
   @Final
   private Minecraft minecraft;

   private static long seedqueue$lastLog = 0L;

   @Inject(method = "render", at = @At("TAIL"))
   private void worldpreview$copyAfterRender(CallbackInfo ci) {
      WorldPreview.performCopyTo(this.minecraft);

      if (WorldPreview.forceLevelRender && Boolean.getBoolean("seedqueue.directLevel")) {
         try {
            ((GameRenderer)(Object)this).renderLevel();
            WorldPreview.LOGGER.info("[preview] direct renderLevel executed");
         } catch (Throwable t) {
            WorldPreview.LOGGER.warn("[preview] direct renderLevel failed", t);
         }
      }
   }

   @org.spongepowered.asm.mixin.injection.ModifyVariable(
      method = "extract(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"), argsOnly = true
   )
   private boolean worldpreview$advanceForPreview(boolean advanceGameTime) {
      // A queued world is rendered while the wall screen is open, where the client may consider
      // itself paused. Without forcing the "advance" flag the level extraction is skipped and the
      // frame stays at the clear colour.
      return WorldPreview.isActive() || WorldPreview.forceLevelRender ? true : advanceGameTime;
   }

   /**
    * Replaces the level clear colour with the sky colour while a preview is rendered. The area
    * beyond the loaded preview chunks is otherwise cleared with the (dark) level fog colour and
    * shows up as a black band.
    */
   @org.spongepowered.asm.mixin.injection.ModifyArg(
      method = "renderLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V"
      ),
      index = 4
   )
   private org.joml.Vector4f worldpreview$previewClearColor(org.joml.Vector4f original) {
      if (WorldPreview.isActive() || WorldPreview.forceLevelRender) {
         return new org.joml.Vector4f(0.44F, 0.60F, 0.93F, 1.0F);
      }

      return original;
   }

   @Inject(method = "extract", at = @At("RETURN"))
   private void worldpreview$afterExtract(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
      // Must run before GameRenderer.renderLevel builds the fog buffer: patching the fog later (at
      // LevelRenderer.render) leaves the previously uploaded black fog in place.
      if (WorldPreview.isActive() || WorldPreview.forceLevelRender) {
         if (Boolean.getBoolean("worldpreview.simulateBlackFog")) {
            var fog = ((GameRendererAccessor)this).worldpreview$gameRenderState().levelRenderState.cameraRenderState.fogData;
            fog.color.set(0.0F, 0.0F, 0.0F, 1.0F);
            fog.renderDistanceEnd = 0.0F;
         }

         // Must run after Gui.extractRenderState reset the override and before GameRenderer.render
         // clears the main target, otherwise the area beyond the preview chunks stays black.
         WorldPreview.applyPreviewClearColor(this.minecraft);
         WorldPreview.applyAtmosphereFallback(this.minecraft);
      }

      if (WorldPreview.isActive()) {
         WorldPreview.recenterIfNeeded(this.minecraft);
         if (Boolean.getBoolean("worldpreview.manualSodium")) {
            WorldPreview.setupSodiumTerrain(this.minecraft);
         }
         SodiumCompat.setupTerrain(this.minecraft.gameRenderer.mainCamera());
      }

      if (WorldPreview.forceLevelRender || WorldPreview.isActive()) {
         long now = System.currentTimeMillis();

         if (Boolean.getBoolean("seedqueue.renderLog") && now - seedqueue$lastLog > 1000L) {
            seedqueue$lastLog = now;
            WorldPreview.LOGGER.info(
               "[preview] extract force={} active={} level={} should={}",
               WorldPreview.forceLevelRender,
               WorldPreview.isActive(),
               this.minecraft.level,
               ((GameRendererAccessor)this).worldpreview$gameRenderState().shouldRenderLevel
            );
         }
      }

      if (WorldPreview.forceLevelRender) {
         ((GameRendererAccessor)this).worldpreview$gameRenderState().shouldRenderLevel = true;
         WorldPreview.populateVisibleSections(this.minecraft);
      }
   }
}
