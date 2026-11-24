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
   static final boolean CAMERA_LOG = Boolean.getBoolean("worldpreview.cameraLog");
   static final boolean KEEP_WORLD_FOG = Boolean.getBoolean("worldpreview.keepWorldFog");
   static final boolean KEEP_NIGHT_LIGHT = Boolean.getBoolean("worldpreview.keepNightLight");
   public static final Logger LOGGER = LogManager.getLogger("WorldPreview");
   public static final boolean ENABLED = !Boolean.getBoolean("worldpreview.disable");
   public static final String BUILD = "worldpreview-26.3-build-1957";
   private static boolean buildLogged;

   static WorldPreviewProperties properties;
   static boolean pending;
   static boolean recentered;
   static boolean spawnAccepted;
   static boolean spawnFinal;
   static boolean creatingFreshLevel;
   static boolean realLevelBound;
   static boolean hideBackgroundAfterPreview;
   static boolean suppressBackground;
   static boolean building;
   static int populateTicks;

   private static int redirtyIndex;

   private WorldPreview() {
   }

   public static  boolean isActive() {
      return PreviewLifecycleManager.isActive();
   }

   public static  boolean isBuilding() {
      return PreviewLifecycleManager.isBuilding();
   }

   public static  void setBuilding(boolean value) {
      PreviewLifecycleManager.setBuilding(value);
   }

   public static  boolean hasSpawn() {
      return PreviewLifecycleManager.hasSpawn();
   }

   public static  WorldPreviewProperties properties() {
      return PreviewLifecycleManager.properties();
   }

   public static  void set(WorldPreviewProperties previewProperties) {
      PreviewLifecycleManager.set(previewProperties);
   }

   /** Aborts the preview because the player asked for a reset while loading. */
   public static  void abort() {
      PreviewLifecycleManager.abort();
   }

   public static  void invalidateCompiledSections() {
      TerrainManager.invalidateCompiledSections();
   }

   public static  void clear() {
      PreviewLifecycleManager.clear();
   }

   /**
    * Keeps the preview camera on the spawn the server reports. Returns true when the position
    * changed. Small corrections are ignored so the camera (and the compiled geometry) stays stable.
    */
   public static  boolean updateSpawn(BlockPos spawnPos, boolean valid) {
      return PreviewLifecycleManager.updateSpawn(spawnPos, valid);
   }

   /** Mirrors the player position packet onto the preview player (authoritative spawn position). */
   public static  void syncServerPosition(net.minecraft.world.phys.Vec3 position, float yRot, float xRot) {
      PreviewLifecycleManager.syncServerPosition(position, yRot, xRot);
   }

   /** SeedQueue 的进入流程是否正在进行（没有安装 SeedQueue 时返回 false）。 */
   private static  boolean seedqueueJoinPending() {
      return PreviewLifecycleManager.seedqueueJoinPending();
   }

   public static  void onServerPreparingLevels(net.minecraft.server.MinecraftServer server) {
      PreviewLifecycleManager.onServerPreparingLevels(server);
   }

   public static  void setCreatingFreshLevel(boolean value) {
      PreviewLifecycleManager.setCreatingFreshLevel(value);
   }

   public static  boolean hasFinalSpawn() {
      return PreviewLifecycleManager.hasFinalSpawn();
   }

   /** Keeps the panorama hidden until the loading screen closes, even right after the preview. */
   public static  boolean shouldHideBackground() {
      return PreviewLifecycleManager.shouldHideBackground();
   }

   public static  void onLoadingScreenClosed() {
      PreviewLifecycleManager.onLoadingScreenClosed();
   }

   public static  void markSpawnFinal(String source) {
      PreviewLifecycleManager.markSpawnFinal(source);
   }

   /** Re-binds the extractor and Sodium to the real level once the preview is gone. */
   /** Forces Sodium/the extractor back onto the real client level after a wall hand-over. */
   public static  void rebindRealLevel(Minecraft minecraft) {
      LevelBindingManager.rebindRealLevel(minecraft);
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
   public static  void releasePreviewCameraAndEntities(Minecraft minecraft) {
      LevelBindingManager.releasePreviewCameraAndEntities(minecraft);
   }

   public static  void ensureRealLevelBound(Minecraft minecraft) {
      LevelBindingManager.ensureRealLevelBound(minecraft);
   }

   public static  void tickPending(Minecraft minecraft) {
      LevelBindingManager.tickPending(minecraft);
   }

   /**
    * Performs the expensive level-change work (renderer reset, section recompilation, Sodium terrain
    * refresh) outside of a frame, from the client tick. Doing this while a frame is being rendered
    * makes Sodium wait on a fence for the current submit and crash.
    */
   static final org.joml.Vector4f PREVIEW_CLEAR_COLOR = new org.joml.Vector4f(0.44F, 0.60F, 0.93F, 1.0F);

   /**
    * Uses the sky colour as the frame clear colour while a preview is rendered. Without it the area
    * beyond the loaded preview chunks (below the horizon) is cleared to black, which shows up as a
    * black band around the preview.
    */
   public static  void applyPreviewClearColor(Minecraft minecraft) {
      RenderHooksManager.applyPreviewClearColor(minecraft);
   }

   /** Called right before LevelRenderer renders the external preview level (frustum is ready here). */
   public static  void beforeExternalRender(Minecraft minecraft) {
      RenderHooksManager.beforeExternalRender(minecraft);
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
   public static  void applyAtmosphereFallback(Minecraft minecraft) {
      RenderHooksManager.applyAtmosphereFallback(minecraft);
   }

   public static  void prepareExternal(Minecraft minecraft) {
      RenderHooksManager.prepareExternal(minecraft);
   }

   public static  void recenterIfNeeded(Minecraft minecraft) {
      LevelBindingManager.recenterIfNeeded(minecraft);
   }

   /** Fills the visible-section list and seeds meshes so the extractor compiles them. */
   public static  void populateVisibleSections(Minecraft minecraft) {
      TerrainManager.populateVisibleSections(minecraft);
   }

   private static  boolean isEmptyMesh(RenderSection section) {
      return TerrainManager.isEmptyMesh(section);
   }

   /** allChanged() creates the section tracker; recreate it if something reset the extractor. */
   static boolean ensureSectionTracker(Minecraft minecraft) {
      return TerrainManager.ensureSectionTracker(minecraft);
   }

   static java.lang.reflect.Field sectionTrackerField;

   private static  boolean hasSectionTracker(Minecraft minecraft) {
      return TerrainManager.hasSectionTracker(minecraft);
   }

   /**
    * The extractor only submits a compile for an uncompiled section when all of its neighbours are
    * present and lit, which does not hold for the preview world. Giving the section an EMPTY mesh
    * satisfies that check so the extractor compiles it with its own render region cache.
    */
   private static boolean seedEmptyMesh(RenderSection section) {
      return TerrainManager.seedEmptyMesh(section);
   }

   static boolean externalCapture;
   static java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> captureRequest;

   /** Activates rendering of a preview world that was built for another instance. */
   public static  void activateExternal(WorldPreviewProperties props) {
      PreviewLifecycleManager.activateExternal(props);
   }

   public static  void deactivateExternal() {
      PreviewLifecycleManager.deactivateExternal();
   }

   /** Captures the next rendered frame (world only, before HUD/screens) into an image. */
   public static  void requestCapture(java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> consumer) {
      TargetManager.requestCapture(consumer);
   }

   static com.mojang.blaze3d.pipeline.RenderTarget captureSourceTarget;

   /** Captures a specific render target on the next frame (used for per-instance card targets). */
   public static void requestTargetCapture(
      com.mojang.blaze3d.pipeline.RenderTarget target,
      java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> consumer
   ) {
      captureSourceTarget = target;
      captureRequest = consumer;
   }

   static com.mojang.blaze3d.pipeline.RenderTarget captureTarget;
   static com.mojang.blaze3d.pipeline.RenderTarget targetOverride;

   /** When set, LevelRenderer renders into this target instead of the main one. */
   public static  void setTargetOverride(com.mojang.blaze3d.pipeline.RenderTarget target) {
      TargetManager.setTargetOverride(target);
   }

   public static  com.mojang.blaze3d.pipeline.RenderTarget targetOverride() {
      return TargetManager.targetOverride();
   }
   static com.mojang.blaze3d.pipeline.RenderTarget savedMainTarget;

   /** Creates (or reuses) the off-screen target used to render instance previews. */
   public static  com.mojang.blaze3d.pipeline.RenderTarget captureTarget(Minecraft minecraft) {
      return TargetManager.captureTarget(minecraft);
   }

   /** Swaps the GameRenderer main render target field to the given target (all consumers). */
   public static  void swapMainFieldTo(Minecraft minecraft, com.mojang.blaze3d.pipeline.RenderTarget target) {
      TargetManager.swapMainFieldTo(minecraft, target);
   }

   /** Redirects the main render target to the instance capture target. */
   public static  boolean swapMainTarget(Minecraft minecraft) {
      return TargetManager.swapMainTarget(minecraft);
   }

   public static  void restoreMainTarget(Minecraft minecraft) {
      TargetManager.restoreMainTarget(minecraft);
   }

   /** Reads the main render target into an image; call at the end of a frame. */
   public static  void captureImage(com.mojang.blaze3d.pipeline.RenderTarget target, java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> consumer) {
      TargetManager.captureImage(target, consumer);
   }


   /** Reads the off-screen target into an image (call after the frame was rendered). */
   public static  void captureTargetImage(java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> consumer) {
      TargetManager.captureTargetImage(consumer);
   }

   /** Creates a render target with the same formats as the main target. */
   public static  com.mojang.blaze3d.pipeline.RenderTarget createTarget(Minecraft minecraft, String label, int width, int height) {
      return TargetManager.createTarget(minecraft, label, width, height);
   }

   /**
    * Renders one instance world into its own target, using the normal frame render pipeline so the
    * feature dispatcher stays in a consistent state. Must be called from the render thread at the
    * start of a frame.
    */
   public static boolean instanceRendering;
   /** True for F3 lines that describe the current world/position (they do not belong on the wall). */
   public static  boolean isWorldDebugLine(String line) {
      return PreviewLifecycleManager.isWorldDebugLine(line);
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

   static com.mojang.blaze3d.pipeline.RenderTarget grabTarget;
   static boolean mainCopyRequested;
   static boolean mainCopyReady;

   static com.mojang.blaze3d.pipeline.RenderTarget copyTarget;
   static com.mojang.blaze3d.pipeline.RenderTarget copySource;
   static boolean copyTargetRequested;

   /** Requests a GPU copy of the world-only main target into the given render target. */
   public static  void requestCopyTo(com.mojang.blaze3d.pipeline.RenderTarget source, com.mojang.blaze3d.pipeline.RenderTarget target) {
      TargetManager.requestCopyTo(source, target);
   }

   public static  void performCopyTo(Minecraft minecraft) {
      TargetManager.performCopyTo(minecraft);
   }

   /** Requests a GPU copy of the main target at the next GUI-render boundary. */
   public static  void requestMainCopy() {
      TargetManager.requestMainCopy();
   }

   public static  boolean hasMainCopy() {
      return TargetManager.hasMainCopy();
   }

   /** Copies the (world-only) main target into a stable texture before the GUI is drawn. */
   public static  void performMainCopy(Minecraft minecraft) {
      TargetManager.performMainCopy(minecraft);
   }

   /** Reads the copied frame into an image; returns false when no copy is pending. */
   public static  boolean readMainCopy(java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> consumer) {
      return TargetManager.readMainCopy(consumer);
   }

   public static  void captureFrame(Minecraft minecraft) {
      TargetManager.captureFrame(minecraft);
   }

   public static  void swapIn(Minecraft minecraft) {
      TargetManager.swapIn(minecraft);
   }

   public static  void swapOut(Minecraft minecraft) {
      TargetManager.swapOut(minecraft);
   }

   static net.minecraft.client.multiplayer.ClientLevel lastViewAreaLevel;
   private static int sodiumTicks;

   /** Sodium's own terrain setup is not reached for the preview world, so run it ourselves. */
   public static  void setupSodiumTerrain(Minecraft minecraft) {
      TerrainManager.setupSodiumTerrain(minecraft);
   }

   static int previewApplied;
   static int previewExpected;
   static int readyFrames;
   static long previewShownAt;
   static int realLevelFrames;

   public static  void resetPreviewProgress() {
      TerrainManager.resetPreviewProgress();
   }

   public static  void noteChunksApplied(int applied, int expected) {
      TerrainManager.noteChunksApplied(applied, expected);
   }

   /** Ends the preview as soon as the streamed chunks are rendered. */
   public static  void maybeComplete(Minecraft minecraft) {
      TerrainManager.maybeComplete(minecraft);
   }

   private static  boolean hasCompiledSection(Minecraft minecraft) {
      return TerrainManager.hasCompiledSection(minecraft);
   }

   public static  void logSkinOnce(Minecraft minecraft) {
      LevelBindingManager.logSkinOnce(minecraft);
   }

   /**
    * The loading screen only closes when the real level is rendered; while the preview keeps
    * replacing {@code Minecraft.level} the client can wait forever, so stop as soon as the real
    * level has been installed.
    */
   public static  void checkRealLevel(Minecraft minecraft) {
      LevelBindingManager.checkRealLevel(minecraft);
   }

   public static void tick(Minecraft minecraft) {
      // no-op test hook
   }
}
