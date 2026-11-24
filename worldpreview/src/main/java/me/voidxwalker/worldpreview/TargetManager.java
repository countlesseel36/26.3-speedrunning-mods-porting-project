package me.voidxwalker.worldpreview;

import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static me.voidxwalker.worldpreview.WorldPreview.*;

/**
 * 预览渲染目标：主目标交换、目标覆盖、抓帧与拷贝队列。
 * 从 WorldPreview 拆分出来，行为保持不变。
 */
final class TargetManager {
   private TargetManager() {
   }

   public static void requestCapture(java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> consumer) {
      captureRequest = consumer;
   }
   public static void setTargetOverride(com.mojang.blaze3d.pipeline.RenderTarget target) {
      targetOverride = target;
   }
   public static com.mojang.blaze3d.pipeline.RenderTarget targetOverride() {
      return targetOverride;
   }
   public static com.mojang.blaze3d.pipeline.RenderTarget captureTarget(Minecraft minecraft) {
      try {
         if (captureTarget == null) {
            com.mojang.blaze3d.pipeline.RenderTarget main =
               ((me.voidxwalker.worldpreview.mixin.GameRendererAccessor)minecraft.gameRenderer).worldpreview$mainRenderTarget();
            java.lang.reflect.Field colorField = com.mojang.blaze3d.pipeline.RenderTarget.class.getDeclaredField("colorFormat");
            colorField.setAccessible(true);
            java.lang.reflect.Field depthField = com.mojang.blaze3d.pipeline.RenderTarget.class.getDeclaredField("depthFormat");
            depthField.setAccessible(true);
            com.mojang.renderpearl.api.GpuFormat color = (com.mojang.renderpearl.api.GpuFormat)colorField.get(main);
            com.mojang.renderpearl.api.GpuFormat depth = (com.mojang.renderpearl.api.GpuFormat)depthField.get(main);
            // "Simulated window size": render previews at a fixed resolution instead of the real window,
            // which keeps several queued worlds cheap. 0 means "use the real window".
            int simulatedWidth = Integer.getInteger("worldpreview.simulatedWidth", 0);
            int simulatedHeight = Integer.getInteger("worldpreview.simulatedHeight", 0);
            int width = simulatedWidth > 0 ? simulatedWidth : minecraft.getWindow().getWidth();
            int height = simulatedHeight > 0 ? simulatedHeight : minecraft.getWindow().getHeight();
            captureTarget = new com.mojang.blaze3d.pipeline.TextureTarget("seedqueue preview", width, height, color, depth);
            captureTarget.createBuffers(width, height);
         }

         return captureTarget;
      } catch (Throwable t) {
         LOGGER.warn("[preview] failed to create capture target", t);
         return null;
      }
   }
   public static void swapMainFieldTo(Minecraft minecraft, com.mojang.blaze3d.pipeline.RenderTarget target) {
      try {
         if (savedMainTarget == null) {
            savedMainTarget = ((me.voidxwalker.worldpreview.mixin.GameRendererAccessor)minecraft.gameRenderer).worldpreview$mainRenderTarget();
         }

         java.lang.reflect.Field field = net.minecraft.client.renderer.GameRenderer.class.getDeclaredField("mainRenderTarget");
         field.setAccessible(true);
         field.set(minecraft.gameRenderer, target);
      } catch (Throwable t) {
         LOGGER.warn("[preview] failed to swap main target field", t);
      }
   }
   public static boolean swapMainTarget(Minecraft minecraft) {
      try {
         com.mojang.blaze3d.pipeline.RenderTarget target = captureTarget(minecraft);

         if (target == null) {
            return false;
         }

         savedMainTarget = ((me.voidxwalker.worldpreview.mixin.GameRendererAccessor)minecraft.gameRenderer).worldpreview$mainRenderTarget();
         java.lang.reflect.Field field = net.minecraft.client.renderer.GameRenderer.class.getDeclaredField("mainRenderTarget");
         field.setAccessible(true);
         field.set(minecraft.gameRenderer, target);
         return true;
      } catch (Throwable t) {
         LOGGER.warn("[preview] failed to swap main render target", t);
         return false;
      }
   }
   public static void restoreMainTarget(Minecraft minecraft) {
      try {
         if (savedMainTarget != null) {
            java.lang.reflect.Field field = net.minecraft.client.renderer.GameRenderer.class.getDeclaredField("mainRenderTarget");
            field.setAccessible(true);
            field.set(minecraft.gameRenderer, savedMainTarget);
            savedMainTarget = null;
         }
      } catch (Throwable t) {
         LOGGER.warn("[preview] failed to restore main render target", t);
      }
   }
   public static void captureImage(com.mojang.blaze3d.pipeline.RenderTarget target, java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> consumer) {
      net.minecraft.client.Screenshot.takeScreenshot(target, consumer);
   }
   public static void captureTargetImage(java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> consumer) {
      if (captureTarget == null) {
         return;
      }

      net.minecraft.client.Screenshot.takeScreenshot(captureTarget, consumer);
   }
   public static com.mojang.blaze3d.pipeline.RenderTarget createTarget(Minecraft minecraft, String label, int width, int height) {
      try {
         com.mojang.blaze3d.pipeline.RenderTarget main =
            ((me.voidxwalker.worldpreview.mixin.GameRendererAccessor)minecraft.gameRenderer).worldpreview$mainRenderTarget();
         java.lang.reflect.Field colorField = com.mojang.blaze3d.pipeline.RenderTarget.class.getDeclaredField("colorFormat");
         colorField.setAccessible(true);
         java.lang.reflect.Field depthField = com.mojang.blaze3d.pipeline.RenderTarget.class.getDeclaredField("depthFormat");
         depthField.setAccessible(true);
         com.mojang.renderpearl.api.GpuFormat color = (com.mojang.renderpearl.api.GpuFormat)colorField.get(main);
         com.mojang.renderpearl.api.GpuFormat depth = (com.mojang.renderpearl.api.GpuFormat)depthField.get(main);
         com.mojang.blaze3d.pipeline.TextureTarget target =
            new com.mojang.blaze3d.pipeline.TextureTarget(label, width, height, color, depth);
         target.createBuffers(width, height);
         LOGGER.info(
            "[preview] created target {}x{} usage={} mainUsage={}",
            width,
            height,
            target.getColorTexture().usage(),
            main.getColorTexture().usage()
         );
         return target;
      } catch (Throwable t) {
         LOGGER.warn("[preview] failed to create render target", t);
         return null;
      }
   }
   public static void requestCopyTo(com.mojang.blaze3d.pipeline.RenderTarget source, com.mojang.blaze3d.pipeline.RenderTarget target) {
      copySource = source;
      copyTarget = target;
      copyTargetRequested = true;
   }
   public static void performCopyTo(Minecraft minecraft) {
      if (!copyTargetRequested) {
         return;
      }

      copyTargetRequested = false;

      try {
         if (copyTarget != null && copySource != null) {
            copyTarget.blitAndBlendToTexture(copySource.getColorTextureView(), copyTarget.getColorTextureView());
         }
      } catch (Throwable t) {
         LOGGER.warn("[preview] copy-to-target failed", t);
      }
   }
   public static void requestMainCopy() {
      mainCopyRequested = true;
   }
   public static boolean hasMainCopy() {
      return mainCopyReady;
   }
   public static void performMainCopy(Minecraft minecraft) {
      if (!mainCopyRequested) {
         return;
      }

      mainCopyRequested = false;

      try {
         if (grabTarget == null) {
            // Honour the simulated preview size here as well: this target is the one the wall cards are
            // blitted from, and at full window resolution it dominated VRAM use (and caused GPU OOM).
            int simulatedWidth = Integer.getInteger("worldpreview.simulatedWidth", 0);
            int simulatedHeight = Integer.getInteger("worldpreview.simulatedHeight", 0);
            grabTarget = createTarget(
               minecraft,
               "seedqueue grab",
               simulatedWidth > 0 ? simulatedWidth : minecraft.getWindow().getWidth(),
               simulatedHeight > 0 ? simulatedHeight : minecraft.getWindow().getHeight()
            );
         }

         if (grabTarget != null) {
            com.mojang.blaze3d.pipeline.RenderTarget main =
               ((me.voidxwalker.worldpreview.mixin.GameRendererAccessor)minecraft.gameRenderer).worldpreview$mainRenderTarget();
            grabTarget.copyColorFrom(main);
            mainCopyReady = true;
         }
      } catch (Throwable t) {
         LOGGER.warn("[preview] failed to copy main target", t);
      }
   }
   public static boolean readMainCopy(java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> consumer) {
      if (!mainCopyReady || grabTarget == null) {
         return false;
      }

      mainCopyReady = false;
      net.minecraft.client.Screenshot.takeScreenshot(grabTarget, consumer);
      return true;
   }
   public static void captureFrame(Minecraft minecraft) {
      if (Boolean.getBoolean("seedqueue.renderLog")) {
         LOGGER.info(
            "[preview] captureFrame: request={} active={} level={} swapped={}",
            captureRequest != null,
            isActive(),
            minecraft.level,
            properties != null && minecraft.level == properties.level
         );
      }

      if (captureRequest == null) {
         return;
      }

      java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> consumer = captureRequest;
      captureRequest = null;
      com.mojang.blaze3d.pipeline.RenderTarget target = captureSourceTarget;
      captureSourceTarget = null;

      if (target == null) {
         target = ((me.voidxwalker.worldpreview.mixin.GameRendererAccessor)minecraft.gameRenderer).worldpreview$mainRenderTarget();
      }

      net.minecraft.client.Screenshot.takeScreenshot(target, consumer);
   }
   public static void swapIn(Minecraft minecraft) {
      if (properties == null || !spawnFinal) {
         return;
      }

      properties.savedLevel = minecraft.level;
      properties.savedPlayer = minecraft.player;
      properties.savedCameraEntity = minecraft.getCameraEntity();
      properties.savedGameMode = minecraft.gameMode;

      minecraft.level = properties.level;
      minecraft.particleEngine.setLevel(properties.level);
      if (lastViewAreaLevel != properties.level) {
         lastViewAreaLevel = properties.level;
         minecraft.levelRenderer.resetLevelRenderData();
      }
      minecraft.player = properties.player;
      net.minecraft.world.entity.Entity previewCamera =
         properties.cameraEntity != null ? properties.cameraEntity : properties.player;
      minecraft.setCameraEntity(previewCamera);
      minecraft.gameMode = properties.gameMode;

      if (((me.voidxwalker.worldpreview.mixin.LevelExtractorAccessor)minecraft.levelExtractor).worldpreview$getLevel() != properties.level) {
         minecraft.levelExtractor.setLevel(properties.level);
      }

      SodiumCompat.setup(properties.level);
      minecraft.gameRenderer.mainCamera().setLevel(properties.level);
      minecraft.gameRenderer.mainCamera().setEntity(previewCamera);

      // Camera#tick lerps eyeHeight towards the entity's eye height, so a camera reused across
      // levels visibly slides up/down for a moment after each preview swap ("camera movement").
      // Start from the exact eye height of the preview entity instead.
      try {
         var camera = minecraft.gameRenderer.mainCamera();
         var accessor = (me.voidxwalker.worldpreview.mixin.CameraAccessor) camera;
         float eyeHeight = previewCamera.getEyeHeight();
         accessor.worldpreview$setEyeHeight(eyeHeight);
         accessor.worldpreview$setEyeHeightOld(eyeHeight);
      } catch (Throwable ignored) {
      }

      // No block outline in previews: the synthetic player always "looks at" a block.
      minecraft.gameRenderer.setRenderBlockOutline(false);

      // Camera.tick() is what computes fovModifier. Without it the FOV stays 0, the projection
      // matrix becomes infinite and every draw collapses to nothing (the frame keeps only the
      // clear colour). The client only ticks the camera while the game is not paused, so run it
      // once for the preview frame.
      try {
         minecraft.gameRenderer.mainCamera().tick();
      } catch (Throwable t) {
         LOGGER.warn("[preview] camera tick failed", t);
      }

      properties.rendering = true;
   }
   public static void swapOut(Minecraft minecraft) {
      if (properties == null || !properties.rendering) {
         return;
      }

      minecraft.level = properties.savedLevel;
      minecraft.particleEngine.setLevel(properties.savedLevel);
      minecraft.player = properties.savedPlayer;
      minecraft.setCameraEntity(properties.savedCameraEntity);
      minecraft.gameMode = properties.savedGameMode;
      minecraft.gameRenderer.mainCamera().setLevel(properties.savedLevel);
      minecraft.gameRenderer.mainCamera().setEntity(properties.savedCameraEntity);
      minecraft.gameRenderer.setRenderBlockOutline(true);
      properties.rendering = false;
   }
}
