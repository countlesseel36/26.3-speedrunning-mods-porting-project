package me.voidxwalker.worldpreview;

import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 26.3 WorldPreview.
 *
 * <p>The preview world is swapped into {@code Minecraft.level} for the whole frame, while
 * {@code LevelExtractor} and the camera stay bound to the preview level. The extractor must only be
 * rebound when it does not already target the preview level: {@code setLevel} calls
 * {@code allChanged()}, which invalidates and clears the compiled geometry.</p>
 */
public final class WorldPreview {
   private static final boolean CAMERA_LOG = Boolean.getBoolean("worldpreview.cameraLog");
   private static final boolean KEEP_WORLD_FOG = Boolean.getBoolean("worldpreview.keepWorldFog");
   private static final boolean KEEP_NIGHT_LIGHT = Boolean.getBoolean("worldpreview.keepNightLight");
   public static final Logger LOGGER = LogManager.getLogger("WorldPreview");
   public static final boolean ENABLED = !Boolean.getBoolean("worldpreview.disable");
   public static final String BUILD = "worldpreview-26.3-build-1957";
   private static boolean buildLogged;

   private static WorldPreviewProperties properties;
   private static boolean pending;
   private static boolean recentered;
   private static boolean spawnAccepted;
   private static boolean spawnFinal;
   private static boolean creatingFreshLevel;
   private static boolean realLevelBound;
   private static boolean hideBackgroundAfterPreview;
   private static boolean suppressBackground;
   private static boolean building;
   private static int populateTicks;

   private static int redirtyIndex;

   private WorldPreview() {
   }

   public static boolean isActive() {
      return properties != null;
   }

   public static boolean isBuilding() {
      return building;
   }

   public static void setBuilding(boolean value) {
      building = value;
   }

   public static boolean hasSpawn() {
      return properties != null && spawnAccepted;
   }

   public static WorldPreviewProperties properties() {
      return properties;
   }

   public static void set(WorldPreviewProperties previewProperties) {
      spawnFinal = false;
      realLevelBound = false;
      properties = previewProperties;
   }

   /** Aborts the preview because the player asked for a reset while loading. */
   public static void abort() {
      if (properties != null) {
         LOGGER.info("[preview] aborted by reset request");
      }

      pending = false;
      creatingFreshLevel = false;
      clear();
   }

   public static void invalidateCompiledSections() {
      // sections are seeded again on every frame, nothing to invalidate
   }

   public static void clear() {
      if (properties != null) {
         Minecraft minecraft = Minecraft.getInstance();
         LOGGER.info(
            "[preview] clearing preview: spawn={}, camera={}, player={}",
            properties.spawnPos,
            minecraft.gameRenderer.mainCamera().position(),
            properties.player.position()
         );
      } else {
         LOGGER.info("[preview] clearing preview");
      }
      if (!keepSodiumBound) {
         SodiumCompat.restore(Minecraft.getInstance());
      }

      Minecraft instance = Minecraft.getInstance();
      if (instance.player != null) {
         logSkinOnce(instance);
      }
      properties = null;
      recentered = false;
      spawnAccepted = false;
      PreviewBuilder.resetChunks();
   }

   /**
    * Keeps the preview camera on the spawn the server reports. Returns true when the position
    * changed. Small corrections are ignored so the camera (and the compiled geometry) stays stable.
    */
   public static boolean updateSpawn(BlockPos spawnPos, boolean valid) {
      if (properties == null || !valid) {
         return false;
      }

      boolean movedFar = !spawnAccepted || properties.spawnPos.distSqr(spawnPos) > 64.0 * 64.0;
      spawnAccepted = true;
      properties.spawnPos = spawnPos;
      properties.player.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
      properties.player.setNoGravity(true);
      properties.player.setOldPosAndRot();
      properties.player.setDeltaMovement(0.0, 0.0, 0.0);
      if (movedFar) {
         recentered = false;
         LOGGER.info("[preview] accepted spawn {}", spawnPos);
         return true;
      }

      return false;
   }

   /** Mirrors the player position packet onto the preview player (authoritative spawn position). */
   public static void syncServerPosition(net.minecraft.world.phys.Vec3 position, float yRot, float xRot) {
      if (properties == null) {
         return;
      }

      WorldPreview.LOGGER.info("[preview] position packet at {}", position);
      markSpawnFinal("server position packet");
      BlockPos newPos = BlockPos.containing(position.x, position.y, position.z);
      boolean movedFar = !spawnAccepted || properties.spawnPos.distSqr(newPos) > 32.0 * 32.0;
      properties.spawnPos = newPos;
      properties.player.setPos(position);
      properties.player.setYRot(yRot);
      properties.player.setXRot(xRot);
      properties.player.setOldPosAndRot();
      properties.player.setNoGravity(true);
      properties.player.setDeltaMovement(0.0, 0.0, 0.0);
      spawnAccepted = true;

      if (movedFar) {
         recentered = false;
         PreviewBuilder.resetChunks();
         LOGGER.info("[preview] synced spawn from server position {}", position);
      }
   }

   public static void onServerPreparingLevels(net.minecraft.server.MinecraftServer server) {
      LOGGER.info("[preview] {} (prepareLevels), creatingFreshLevel={}", BUILD, creatingFreshLevel);
      // Only worlds created from the "create new world" flow should get a preview; loading an
      // existing world must stay untouched.
      if (ENABLED && creatingFreshLevel) {
         creatingFreshLevel = false;
         pending = true;
         LOGGER.info("[preview] fresh world creation detected, preview armed");
      }
   }

   public static void setCreatingFreshLevel(boolean value) {
      creatingFreshLevel = value;
      // Before the preview is ready the vanilla loading panorama stays visible; the background is
      // only hidden while the preview is on screen (and for the short hand-over afterwards).
      hideBackgroundAfterPreview = false;
      LOGGER.info("[preview] createFreshLevel flag={}", value);
   }

   public static boolean hasFinalSpawn() {
      return spawnFinal;
   }

   /** Keeps the panorama hidden until the loading screen closes, even right after the preview. */
   public static boolean shouldHideBackground() {
      return hideBackgroundAfterPreview || suppressBackground || isActive() && hasFinalSpawn();
   }

   public static void onLoadingScreenClosed() {
      hideBackgroundAfterPreview = false;
   }

   public static void markSpawnFinal(String source) {
      if (!spawnFinal) {
         spawnFinal = true;
         suppressBackground = false;
         LOGGER.info("[preview] spawn finalized via {}", source);
      }
   }

   /** Re-binds the extractor and Sodium to the real level once the preview is gone. */
   /** Forces Sodium/the extractor back onto the real client level after a wall hand-over. */
   public static void rebindRealLevel(Minecraft minecraft) {
      keepSodiumBound = false;
      realLevelBound = false;
      ensureRealLevelBound(minecraft);
      SodiumCompat.refreshTerrain();
      releasePreviewCameraAndEntities(minecraft);
      LOGGER.info("[preview] rebind to real level requested (level={})", minecraft.level == null ? "null" : minecraft.level.dimension().identifier());
   }

   /**
    * Undoes everything the preview did to the client camera and level.
    *
    * <p>A preview swaps in a {@code ClientLevel} that carries two fake {@code LocalPlayer}s (the
    * preview player and the third person camera anchor) and points {@code Minecraft#getCameraEntity}
    * at them. A seamless join reuses that very level, so unless this runs the player keeps looking
    * through the preview camera (far behind and above the body, terrain culled around it) and the
    * fake players stay registered in the joined world.</p>
    */
   public static void releasePreviewCameraAndEntities(Minecraft minecraft) {
      try {
         net.minecraft.world.entity.Entity player = minecraft.player;

         if (player == null) {
            return;
         }

         net.minecraft.client.Camera camera = minecraft.gameRenderer.mainCamera();
         boolean released = false;

         // Camera#setEntity has to be re-run unconditionally: the preview binds the render camera to
         // its own anchor entity directly, and Minecraft#getCameraEntity already reports the real
         // player, so a check on the entity alone missed it and the player kept looking from the
         // preview camera (far above and behind the body) after joining.
         minecraft.setCameraEntity(player);
         camera.setEntity(player);
         released = true;

         net.minecraft.client.multiplayer.ClientLevel level = minecraft.level;

         if (level != null) {
            for (int id = 1; id <= 4; id++) {
               net.minecraft.world.entity.Entity entity = level.getEntity(id);

               if (entity != null
                  && entity != player
                  && entity instanceof net.minecraft.client.player.LocalPlayer
                  && !(entity instanceof net.minecraft.client.player.LocalPlayer real && real == player)) {
                  level.removeEntity(id, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                  released = true;
               }
            }
         }

         if (released) {
            LOGGER.info("[preview] released the preview camera and fake entities");
         }
      } catch (Throwable t) {
         LOGGER.warn("[preview] failed to release the preview camera", t);
      }
   }

   public static void ensureRealLevelBound(Minecraft minecraft) {
      if (properties != null || minecraft.level == null || realLevelBound) {
         return;
      }

      var accessor = (me.voidxwalker.worldpreview.mixin.LevelExtractorAccessor)minecraft.levelExtractor;
      suppressBackground = false;

      if (accessor.worldpreview$getLevel() != minecraft.level) {
         minecraft.levelExtractor.setLevel(minecraft.level);
         SodiumCompat.restore(minecraft);
         LOGGER.info("[preview] renderer rebound to the real level {}", minecraft.level.dimension().identifier());
      }

      realLevelBound = true;
   }

   public static void tickPending(Minecraft minecraft) {
      if (!pending || isActive()) {
         return;
      }

      WorldPreviewProperties built = PreviewBuilder.build(minecraft);
      if (built != null) {
         set(built);
         pending = false;
         LOGGER.info("[preview] preview is active");
      }
   }

   /**
    * Performs the expensive level-change work (renderer reset, section recompilation, Sodium terrain
    * refresh) outside of a frame, from the client tick. Doing this while a frame is being rendered
    * makes Sodium wait on a fence for the current submit and crash.
    */
   private static final org.joml.Vector4f PREVIEW_CLEAR_COLOR = new org.joml.Vector4f(0.44F, 0.60F, 0.93F, 1.0F);

   /**
    * Uses the sky colour as the frame clear colour while a preview is rendered. Without it the area
    * beyond the loaded preview chunks (below the horizon) is cleared to black, which shows up as a
    * black band around the preview.
    */
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

   /** Called right before LevelRenderer renders the external preview level (frustum is ready here). */
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

   /**
    * A preview {@link ClientLevel} is never ticked, so the environment attributes that drive fog and
    * sky colour can resolve to zero. A black fog colour paints the whole frame black (the clear
    * colour is the fog colour and every fragment is fully fogged). Replace only degenerate values,
    * so real data from a ticked level is left untouched.
    */
   /**
    * Pulls the fog in to the edge of the previewed chunk area.
    *
    * <p>A preview only owns {@code radius} chunks, so the card always ends in unloaded space. With the
    * player's (large) render distance the fog never reaches that edge and the cut shows up as a hard
    * black band that shimmers while sections rebuild. Ending the fog at the loaded edge makes the
    * terrain dissolve instead of ending in black.</p>
    */
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

   public static void recenterIfNeeded(Minecraft minecraft) {
      if (!isActive() || recentered || forceLevelRender) {
         return;
      }

      recentered = true;
      minecraft.levelRenderer.resetLevelRenderData();
      minecraft.levelRenderer.invalidateCompiledGeometry(
         properties.level,
         minecraft.options,
         minecraft.gameRenderer.mainCamera(),
         minecraft.getBlockColors()
      );

      if (ensureSectionTracker(minecraft)) {
         minecraft.levelExtractor.allChanged();
      }

      SodiumCompat.refreshTerrain();

      var area = minecraft.levelRenderer.viewArea();
      LOGGER.info(
         "[preview] recentered: viewArea={}, size={}, camera={}, spawn={}",
         area != null,
         area == null ? -1 : area.size(),
         minecraft.gameRenderer.mainCamera().position(),
         properties.spawnPos
      );
   }

   /** Fills the visible-section list and seeds meshes so the extractor compiles them. */
   public static void populateVisibleSections(Minecraft minecraft) {
      if (!isActive()) {
         return;
      }

      var area = minecraft.levelRenderer.viewArea();
      if (area == null) {
         return;
      }

      SodiumCompat.pumpChunkEvents();

      SectionPos camera = SectionPos.of(minecraft.gameRenderer.mainCamera().position());
      area.repositionCamera(camera);

      var visible = minecraft.levelRenderer.visibleSections();
      minecraft.levelRenderer.clearVisibleSections();
      // The preview level only holds radius+3 chunks, so a 4-section radius is plenty. The old radius
      // of 8 walked 1445 sections per frame and allocated a BlockPos for every lookup.
      int radius = Math.min(4, area.getViewDistance());
      var mutablePos = new net.minecraft.core.BlockPos.MutableBlockPos();
      int added = 0;
      int seeded = 0;
      boolean dirtyOk = !SodiumCompat.PRESENT && recentered && hasSectionTracker(minecraft);
      boolean sweep = dirtyOk && populateTicks % 20 == 0;
      int sweepBudget = sweep ? 48 : 0;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            for (int dy = -2; dy <= 2; dy++) {
               RenderSection section = area.getRenderSectionAt(
                  mutablePos.set((camera.x() + dx) << 4, (camera.y() + dy) << 4, (camera.z() + dz) << 4)
               );
               if (section == null) {
                  continue;
               }

               visible.add(section);
               added++;

               if (!dirtyOk) {
                  continue;
               }

               if (seedEmptyMesh(section)) {
                  seeded++;
                  minecraft.levelExtractor.setSectionDirty(camera.x() + dx, camera.y() + dy, camera.z() + dz);
               } else if (sweepBudget > 0 && isEmptyMesh(section)) {
                  // recover sections whose compile got cancelled without rebuilding everything
                  minecraft.levelExtractor.setSectionDirty(camera.x() + dx, camera.y() + dy, camera.z() + dz);
                  sweepBudget--;
               }
            }
         }
      }

      if (++populateTicks % 40 == 0) {
         int withDraw = 0;
         int uncompiled = 0;
         int empty = 0;
         int other = 0;

         for (var section : visible) {
            try {
               Object mesh = section.getSectionMesh();
               if (mesh == CompiledSectionMesh.UNCOMPILED) {
                  uncompiled++;
               } else if (mesh == CompiledSectionMesh.EMPTY) {
                  empty++;
               } else {
                  other++;
                  Object draw = mesh.getClass()
                     .getMethod("getSectionDraw", net.minecraft.client.renderer.chunk.ChunkSectionLayer.class)
                     .invoke(mesh, net.minecraft.client.renderer.chunk.ChunkSectionLayer.SOLID);
                  if (draw != null) {
                     withDraw++;
                  }
               }
            } catch (Throwable ignored) {
            }
         }

         LOGGER.info("[preview] sodium: {}", SodiumCompat.stats());
         LOGGER.info(
            "[preview] sections: visible={}, seeded={}, uncompiled={}, empty={}, other={}, withDraw={}",
            visible.size(),
            seeded,
            uncompiled,
            empty,
            other,
            withDraw
         );
      }
   }

   private static boolean isEmptyMesh(RenderSection section) {
      try {
         return section.getSectionMesh() == CompiledSectionMesh.EMPTY;
      } catch (Throwable t) {
         return false;
      }
   }

   /** allChanged() creates the section tracker; recreate it if something reset the extractor. */
   private static boolean ensureSectionTracker(Minecraft minecraft) {
      if (hasSectionTracker(minecraft)) {
         return true;
      }

      try {
         minecraft.levelExtractor.allChanged();
      } catch (Throwable t) {
         LOGGER.warn("[preview] failed to recreate section tracker", t);
         return false;
      }

      return hasSectionTracker(minecraft);
   }

   private static java.lang.reflect.Field sectionTrackerField;

   private static boolean hasSectionTracker(Minecraft minecraft) {
      try {
         if (sectionTrackerField == null
            || sectionTrackerField.getDeclaringClass() != minecraft.levelExtractor.getClass()) {
            sectionTrackerField = minecraft.levelExtractor.getClass().getDeclaredField("sectionUpdateTracker");
            sectionTrackerField.setAccessible(true);
         }

         return sectionTrackerField.get(minecraft.levelExtractor) != null;
      } catch (Throwable t) {
         return false;
      }
   }

   /**
    * The extractor only submits a compile for an uncompiled section when all of its neighbours are
    * present and lit, which does not hold for the preview world. Giving the section an EMPTY mesh
    * satisfies that check so the extractor compiles it with its own render region cache.
    */
   private static boolean seedEmptyMesh(RenderSection section) {
      try {
         var field = section.getClass().getField("sectionMesh");
         @SuppressWarnings("unchecked")
         var ref = (AtomicReference<Object>)field.get(section);
         if (ref.get() == CompiledSectionMesh.UNCOMPILED) {
            ref.set(CompiledSectionMesh.EMPTY);
            return true;
         }
      } catch (Throwable t) {
         LOGGER.warn("[preview] failed to seed empty mesh", t);
      }

      return false;
   }

   private static boolean externalCapture;
   private static java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> captureRequest;

   /** Activates rendering of a preview world that was built for another instance. */
   public static void activateExternal(WorldPreviewProperties props) {
      externalCapture = true;
      set(props);

      // Do not reposition the player/camera here: the external preview already placed them (the
      // ExternalPreview surface adjustment). updateSpawn() would reset them to the raw respawn
      // point on every wall switch, making the card jump after it finished loading.
      if (properties != null) {
         properties.spawnPos = props.spawnPos;
      }

      spawnAccepted = true;
      markSpawnFinal("external instance");
      // The external preview does its own Sodium/level setup; never invalidate geometry in-frame.
      recentered = true;
      forceLevelRender = true;
   }

   public static void deactivateExternal() {
      externalCapture = false;
      forceLevelRender = false;
      instanceRendering = false;
      recentered = false;
      clear();
   }

   /** Captures the next rendered frame (world only, before HUD/screens) into an image. */
   public static void requestCapture(java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> consumer) {
      captureRequest = consumer;
   }

   private static com.mojang.blaze3d.pipeline.RenderTarget captureSourceTarget;

   /** Captures a specific render target on the next frame (used for per-instance card targets). */
   public static void requestTargetCapture(
      com.mojang.blaze3d.pipeline.RenderTarget target,
      java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> consumer
   ) {
      captureSourceTarget = target;
      captureRequest = consumer;
   }

   private static com.mojang.blaze3d.pipeline.RenderTarget captureTarget;
   private static com.mojang.blaze3d.pipeline.RenderTarget targetOverride;

   /** When set, LevelRenderer renders into this target instead of the main one. */
   public static void setTargetOverride(com.mojang.blaze3d.pipeline.RenderTarget target) {
      targetOverride = target;
   }

   public static com.mojang.blaze3d.pipeline.RenderTarget targetOverride() {
      return targetOverride;
   }
   private static com.mojang.blaze3d.pipeline.RenderTarget savedMainTarget;

   /** Creates (or reuses) the off-screen target used to render instance previews. */
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

   /** Swaps the GameRenderer main render target field to the given target (all consumers). */
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

   /** Redirects the main render target to the instance capture target. */
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

   /** Reads the main render target into an image; call at the end of a frame. */
   public static void captureImage(com.mojang.blaze3d.pipeline.RenderTarget target, java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> consumer) {
      net.minecraft.client.Screenshot.takeScreenshot(target, consumer);
   }

   public static void captureMainTargetImage(java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> consumer) {
      com.mojang.blaze3d.pipeline.RenderTarget target =
         ((me.voidxwalker.worldpreview.mixin.GameRendererAccessor)Minecraft.getInstance().gameRenderer).worldpreview$mainRenderTarget();
      net.minecraft.client.Screenshot.takeScreenshot(target, consumer);
   }

   /** Reads the off-screen target into an image (call after the frame was rendered). */
   public static void captureTargetImage(java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> consumer) {
      if (captureTarget == null) {
         return;
      }

      net.minecraft.client.Screenshot.takeScreenshot(captureTarget, consumer);
   }

   /** Creates a render target with the same formats as the main target. */
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

   /**
    * Renders one instance world into its own target, using the normal frame render pipeline so the
    * feature dispatcher stays in a consistent state. Must be called from the render thread at the
    * start of a frame.
    */
   public static boolean instanceRendering;
   /** True for F3 lines that describe the current world/position (they do not belong on the wall). */
   public static boolean isWorldDebugLine(String line) {
      if (line == null) {
         return false;
      }

      return line.contains("XYZ")
         || line.contains("Block:")
         || line.contains("Chunk:")
         || line.contains("Facing:")
         || line.contains("Biome:")
         || line.contains("Section-relative")
         || line.contains("minecraft:")
         || line.contains(" FC:")
         || line.startsWith("C: ")
         || line.startsWith("E: ");
   }

   public static boolean hideDebugOverlay;
   /** Real player's skin, reused by the preview player (it has no player list of its own). */
   public static net.minecraft.world.entity.player.PlayerSkin previewSkin;
   public static java.util.UUID previewSkinUuid;
   public static boolean levelDumped;
   public static boolean suppressDebugOverlay;
   public static boolean forceLevelRender;

   private static WorldPreviewProperties lastInstanceProps;

   /** Called once per instance level, after it was swapped into the client (SeedQueue hook). */
   public static java.util.function.Consumer<WorldPreviewProperties> instanceActivator;

   /** SeedQueue hook: runs at the start of every frame, before the preview world is swapped in. */
   public static Runnable frameTickHook;

   /**
    * Keeps Sodium's renderer bound to the preview level across wall previews. Rebinding for every
    * card destroys the {@code RenderSectionManager} together with all compiled geometry, so the
    * wall would never see terrain. SeedQueue clears this when the wall closes.
    */
   public static boolean keepSodiumBound;

   public static boolean renderInstance(
      Minecraft minecraft, WorldPreviewProperties props, com.mojang.blaze3d.pipeline.RenderTarget target
   ) {
      if (Boolean.getBoolean("seedqueue.renderLog")) {
         LOGGER.info("[preview] renderInstance entered props={} target={}", props != null, target != null);
      }

      if (props == null || target == null) {
         return false;
      }

      WorldPreviewProperties previousProperties = properties;
      boolean previousSpawnFinal = spawnFinal;
      boolean previousExternalCapture = externalCapture;
      net.minecraft.client.gui.screens.Screen previousScreen = minecraft.gui.screen();
      boolean hudHidden = minecraft.gui.hud.isHidden();

      boolean firstInstance = lastInstanceProps != props;

      try {
         properties = props;
         spawnFinal = true;
         externalCapture = true;
         recentered = !firstInstance;
         lastInstanceProps = props;
         minecraft.gui.setScreen(null);

         if (!hudHidden) {
            minecraft.gui.hud.toggle();
         }

         swapIn(minecraft);

         if (firstInstance && instanceActivator != null) {
            instanceActivator.accept(props);
         }

         if (Boolean.getBoolean("worldpreview.instanceDebug")) {
            var camera = minecraft.gameRenderer.mainCamera();
            LOGGER.info(
               "[preview] instance swapIn: level={} playerPos={} cameraEntity={} cameraEntityPos={} sodium={}",
               properties.level.dimension().identifier(),
               properties.player.position(),
               properties.cameraEntity == null ? "player" : properties.cameraEntity,
               (properties.cameraEntity == null ? properties.player : properties.cameraEntity).position(),
               SodiumCompat.stats()
            );
         }

         if (!Boolean.getBoolean("worldpreview.instanceMainTarget")) {
            setTargetOverride(target);
         }

         instanceRendering = true;
         boolean previousForce = forceLevelRender;
         forceLevelRender = true;

         try {
            // Camera.update() normally runs from Minecraft.renderFrame; without it the camera stays
            // at (0,0,0) with an empty frustum and the level renders nothing but the clear colour.
            minecraft.gameRenderer.update(minecraft.getDeltaTracker());
            minecraft.gameRenderer.extract(minecraft.getDeltaTracker(), true);

            if (Boolean.getBoolean("worldpreview.forceFog")) {
               var fog = ((me.voidxwalker.worldpreview.mixin.GameRendererAccessor)minecraft.gameRenderer)
                  .worldpreview$gameRenderState().levelRenderState.cameraRenderState.fogData;
               fog.color.set(0.55F, 0.75F, 1.0F, 1.0F);
               fog.environmentalStart = 16.0F;
               fog.environmentalEnd = 96.0F;
               fog.renderDistanceStart = 64.0F;
               fog.renderDistanceEnd = 256.0F;
               fog.skyEnd = 256.0F;
               fog.cloudEnd = 256.0F;
               var sky = ((me.voidxwalker.worldpreview.mixin.GameRendererAccessor)minecraft.gameRenderer)
                  .worldpreview$gameRenderState().levelRenderState.skyRenderState;
               sky.skyColor = new org.joml.Vector3f(0.47F, 0.65F, 1.0F);
            }

            // Buffer/uploads queued by extract() are executed from Minecraft.renderFrame; flush them
            // here or this synthetic frame draws with stale or empty GPU data.
            com.mojang.blaze3d.systems.RenderSystem.executePendingTasks();

            // Sodium's terrain setup normally runs from Minecraft.renderFrame, which never sees this
            // synthetic frame; run it now that the frustum is ready.
            beforeExternalRender(minecraft);
            // Full frame: GameRenderer.render() also renders the lightmap and updates the global
            // shader uniforms (screen size, fog, game time). LevelRenderer.render() alone draws with
            // a stale lightmap/uniform set, which produces nothing but the clear colour.
            minecraft.gameRenderer.render();

            if (Boolean.getBoolean("worldpreview.instanceDebug")) {
               var renderState = ((me.voidxwalker.worldpreview.mixin.GameRendererAccessor)minecraft.gameRenderer)
                  .worldpreview$gameRenderState();
               var cameraState = renderState.levelRenderState.cameraRenderState;
               LOGGER.info(
                  "[preview] instance rendered: camera={} visibleSections={} sodium={} fog={} sky={}",
                  minecraft.gameRenderer.mainCamera().position(),
                  minecraft.levelRenderer.visibleSections().size(),
                  SodiumCompat.stats(),
                  cameraState.fogData == null ? "null" : cameraState.fogData.color,
                  renderState.levelRenderState.skyRenderState
               );
            }
         } finally {
            instanceRendering = false;
            forceLevelRender = previousForce;
            setTargetOverride(null);
            swapOut(minecraft);
         }

         if (Boolean.getBoolean("seedqueue.renderLog")) {
            LOGGER.info("[preview] renderInstance succeeded");
         }

         return true;
      } catch (Throwable t) {
         LOGGER.warn("[preview] failed to render instance preview", t);
         return false;
      } finally {
         if (!hudHidden) {
            minecraft.gui.hud.toggle();
         }

         if (previousScreen != null) {
            minecraft.gui.setScreen(previousScreen);
         }

         properties = previousProperties;
         spawnFinal = previousSpawnFinal;
         externalCapture = previousExternalCapture;
         recentered = false;
      }
   }

   private static com.mojang.blaze3d.pipeline.RenderTarget grabTarget;
   private static boolean mainCopyRequested;
   private static boolean mainCopyReady;

   private static com.mojang.blaze3d.pipeline.RenderTarget copyTarget;
   private static com.mojang.blaze3d.pipeline.RenderTarget copySource;
   private static boolean copyTargetRequested;

   /** Requests a GPU copy of the world-only main target into the given render target. */
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

   /** Requests a GPU copy of the main target at the next GUI-render boundary. */
   public static void requestMainCopy() {
      mainCopyRequested = true;
   }

   public static boolean hasMainCopy() {
      return mainCopyReady;
   }

   /** Copies the (world-only) main target into a stable texture before the GUI is drawn. */
   public static void performMainCopy(Minecraft minecraft) {
      if (!mainCopyRequested) {
         return;
      }

      mainCopyRequested = false;

      try {
         if (grabTarget == null) {
            grabTarget = createTarget(
               minecraft, "seedqueue grab", minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()
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

   /** Reads the copied frame into an image; returns false when no copy is pending. */
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

   private static net.minecraft.client.multiplayer.ClientLevel lastViewAreaLevel;
   private static int sodiumTicks;

   /** Sodium's own terrain setup is not reached for the preview world, so run it ourselves. */
   public static void setupSodiumTerrain(Minecraft minecraft) {
      if (!isActive() || !spawnFinal) {
         return;
      }

      SodiumCompat.setupTerrain(minecraft.gameRenderer.mainCamera());
   }

   private static int previewApplied;
   private static int previewExpected;
   private static int readyFrames;
   private static long previewShownAt;
   private static int realLevelFrames;

   public static void resetPreviewProgress() {
      readyFrames = 0;
   }

   public static void noteChunksApplied(int applied, int expected) {
      previewApplied = applied;
      previewExpected = expected;
   }

   /** Ends the preview as soon as the streamed chunks are rendered. */
   public static void maybeComplete(Minecraft minecraft) {
      if (!isActive()) {
         return;
      }

      boolean ready = SodiumCompat.PRESENT ? SodiumCompat.visibleSections() > 0 : hasCompiledSection(minecraft);

      if (previewApplied >= previewExpected && ready) {
         readyFrames++;
      }

      if (readyFrames == 9) {
         if (Boolean.getBoolean("worldpreview.smoke")) {
            net.minecraft.client.Screenshot.grab(minecraft, false);
         }

         previewShownAt = System.currentTimeMillis();
         LOGGER.info(
            "[preview] preview rendered: chunks={}/{}, sodiumVisible={}",
            previewApplied,
            previewExpected,
            SodiumCompat.visibleSections()
         );
      }

      // The loading screen only closes once the real level renders, so hand over after a short
      // moment of rendered terrain instead of waiting for the screen to close by itself.
      if (readyFrames > 20 && Boolean.getBoolean("worldpreview.earlyhandover")) {
         if (Boolean.getBoolean("worldpreview.closeloading")
            && minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) {
            minecraft.gui.setScreen(null);
            LOGGER.info("[preview] closed the loading screen at handover");
         }

         LOGGER.info("[preview] preview complete: handing over to the real level");
         clear();
      }
   }

   private static boolean hasCompiledSection(Minecraft minecraft) {
      for (var section : minecraft.levelRenderer.visibleSections()) {
         Object mesh = section.getSectionMesh();
         if (mesh != null && mesh != CompiledSectionMesh.UNCOMPILED && mesh != CompiledSectionMesh.EMPTY) {
            return true;
         }
      }

      return false;
   }

   public static void logSkinOnce(Minecraft minecraft) {
      if (minecraft.player == null) {
         return;
      }

      var skin = minecraft.player.getSkin();
      var profile = minecraft.player.getGameProfile();
      LOGGER.info(
         "[preview] player skin: body={}, secure={}, model={}, profile={}, properties={}",
         skin == null ? "null" : skin.body(),
         skin == null ? "null" : skin.secure(),
         skin == null ? "null" : skin.model(),
         profile == null ? "null" : profile.name(),
         profile == null ? -1 : profile.properties().size()
      );
   }

   /**
    * The loading screen only closes when the real level is rendered; while the preview keeps
    * replacing {@code Minecraft.level} the client can wait forever, so stop as soon as the real
    * level has been installed.
    */
   public static void checkRealLevel(Minecraft minecraft) {
      if (properties == null || minecraft.level == null || minecraft.level == properties.level) {
         realLevelFrames = 0;
         return;
      }

      // Once the real level exists its player already carries the authoritative spawn position,
      // so follow it to make the last preview frames match where the player will appear.
      if (minecraft.player != null && minecraft.player != properties.player) {
         syncServerPosition(minecraft.player.position(), minecraft.player.getYRot(), minecraft.player.getXRot());
      }

      ensureRealLevelBound(minecraft);

      // Keep the preview on screen until the real world has generated around the player, then close
      // the loading screen ourselves so the switch goes preview -> world without showing the real
      // (half loaded) world behind the loading screen.
      boolean worldReady = false;

      if (minecraft.player != null && minecraft.player != properties.player) {
         var server = minecraft.getSingleplayerServer();
         var blockPos = minecraft.player.blockPosition();
         worldReady = server != null && server.isReady() && minecraft.level.hasChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
      }

      // Keep the preview on screen for a moment of rendered terrain so it does not just flash by.
      if (worldReady && readyFrames > 60) {
         if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) {
            minecraft.gui.setScreen(null);
            LOGGER.info("[preview] world ready, closed the loading screen");
         }

         LOGGER.info("[preview] world ready, ending preview (preview visible {} ms)", System.currentTimeMillis() - previewShownAt);
         clear();
         return;
      }

      // Give the preview a grace period to render its terrain before giving the client back.
      if (++realLevelFrames > 200) {
         if (readyFrames == 0) {
            LOGGER.warn(
               "[preview] handing over without rendered terrain: chunks={}/{}, sodiumVisible={}, sodiumReady={}, stats={}, spawn={}, camera={}",
               previewApplied,
               previewExpected,
               SodiumCompat.visibleSections(),
               SodiumCompat.readyChunks(),
               SodiumCompat.stats(),
               properties.spawnPos,
               minecraft.gameRenderer.mainCamera().position()
            );
         }

         LOGGER.info("[preview] real level installed, ending preview");
         clear();
      }
   }

   public static void tick(Minecraft minecraft) {
      // no-op test hook
   }
}
