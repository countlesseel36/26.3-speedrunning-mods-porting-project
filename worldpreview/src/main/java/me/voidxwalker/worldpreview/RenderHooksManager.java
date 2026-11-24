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
 * 预览渲染钩子：清屏色、气氛回退与外部预览的渲染准备。
 * 从 WorldPreview 拆分出来，行为保持不变。
 */
final class RenderHooksManager {
   private RenderHooksManager() {
   }

   public static void applyPreviewClearColor(Minecraft minecraft) {
      try {
         var state = ((me.voidxwalker.worldpreview.mixin.GuiAccessor)minecraft.gui).worldpreview$guiRenderState();

         if (isActive() || forceLevelRender) {
            state.clearColorOverride.set(PREVIEW_CLEAR_COLOR);
         } else {
            // GuiRenderState.reset() resets this to 0 every frame; never leave it null.
            state.clearColorOverride.set(0.0F, 0.0F, 0.0F, 0.0F);
         }
      } catch (Throwable ignored) {
      }
   }
   public static void beforeExternalRender(Minecraft minecraft) {
      if (!isActive()) {
         return;
      }

      if (CAMERA_LOG) {
         var camera = minecraft.gameRenderer.mainCamera();
         LOGGER.info(
            "[preview] render camera={} player={} level={}",
            camera.position(),
            properties.player.position(),
            properties.level.dimension().identifier()
         );
      }

      applyPreviewClearColor(minecraft);
      applyAtmosphereFallback(minecraft);
      SodiumCompat.setupTerrain(minecraft.gameRenderer.mainCamera());
      populateVisibleSections(minecraft);
   }
   public static void applyAtmosphereFallback(Minecraft minecraft) {
      try {
         var state = ((me.voidxwalker.worldpreview.mixin.GameRendererAccessor)minecraft.gameRenderer)
            .worldpreview$gameRenderState();
         var cameraState = state.levelRenderState.cameraRenderState;
         var fog = cameraState == null ? null : cameraState.fogData;

         boolean degenerate = fog != null
            && fog.color != null
            && (fog.color.x + fog.color.y + fog.color.z < 0.01F || fog.renderDistanceEnd <= 1.0F);

         boolean overworldPreview = minecraft.level != null && minecraft.level.dimension() == net.minecraft.world.level.Level.OVERWORLD;

         if (degenerate) {
            float far = Math.max(64.0F, minecraft.options.getEffectiveRenderDistance() * 16.0F);
            fog.color.set(0.72F, 0.84F, 1.0F, 1.0F);
            fog.environmentalStart = far * 0.35F;
            fog.environmentalEnd = far;
            fog.renderDistanceStart = far * 0.75F;
            fog.renderDistanceEnd = far;
            fog.skyEnd = far;
            fog.cloudEnd = far * 2.0F;
         } else if (overworldPreview && !KEEP_WORLD_FOG) {
            // The preview level is not ticked, so its fog can resolve to a night colour even though the
            // lightmap is forced to daylight: distant terrain then fades to black ("black fog"). For
            // overworld previews use a daylight fog colour; the nether/end keep their own atmosphere.
            float luminance = (fog.color.x + fog.color.y + fog.color.z) / 3.0F;

            if (luminance < 0.25F) {
               fog.color.set(0.72F, 0.84F, 1.0F, 1.0F);
            }
         }

         var sky = state.levelRenderState.skyRenderState;

         if (sky != null) {
            // The "dark disc"/lower half of the sky dome is visible beyond the loaded preview
            // chunks as a black band; skip the skybox entirely and let the sky-coloured clear
            // colour show through instead.
            sky.shouldRenderDarkDisc = false;

            if (overworldPreview) {
               // The lower half of the preview sky is shaded from skyColor; with the vanilla dusk
               // colour it still shows up as a dark band at the world edge. Use a daylight sky tone.
               sky.skyColor = new org.joml.Vector3f(0.62F, 0.76F, 1.0F);
            }

            if (sky.skyColor == null || sky.skyColor.lengthSquared() < 1.0E-4F) {
               sky.skyColor = new org.joml.Vector3f(0.47F, 0.65F, 1.0F);
            }
         }

         // A preview level is never ticked, so its sky darken can resolve to night: the terrain then
         // renders with a black lightmap ("black fog" over the whole card) while the sky still has
         // colour. Force the daylight sky light contribution for preview frames only.
         var lightmap = state.lightmapRenderState;

         if (lightmap != null && !KEEP_NIGHT_LIGHT) {
            if (lightmap.skyFactor < 1.0F || lightmap.brightness < 1.0F) {
               lightmap.skyFactor = 1.0F;
               lightmap.brightness = 1.0F;
               lightmap.skyLightColor = new org.joml.Vector3f(0.78F, 0.86F, 1.0F);
               lightmap.needsUpdate = true;
            }

            if (lightmap.ambientColor == null || lightmap.ambientColor.lengthSquared() < 1.0E-4F) {
               lightmap.ambientColor = new org.joml.Vector3f(0.12F, 0.12F, 0.14F);
               lightmap.needsUpdate = true;
            }
         }
      } catch (Throwable t) {
         // never break rendering because of a cosmetic fallback
      }
   }
   public static void prepareExternal(Minecraft minecraft) {
      if (!isActive()) {
         return;
      }

      try {
         if (ensureSectionTracker(minecraft)) {
            minecraft.levelExtractor.allChanged();
         }

         SodiumCompat.refreshTerrain();
      } catch (Throwable t) {
         LOGGER.warn("[preview] prepareExternal failed", t);
      }

      recentered = true;
   }
}
