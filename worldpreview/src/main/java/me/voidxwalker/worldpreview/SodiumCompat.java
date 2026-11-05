package me.voidxwalker.worldpreview;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Soft Sodium compatibility. Sodium keeps its own section renderer and only creates its section
 * manager for the level that was passed to {@code SodiumWorldRenderer#setLevel}; without that it
 * throws when the preview level reports dirty sections.
 */
public final class SodiumCompat {
   public static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("sodium");
   private static boolean initialized;
   private static Object renderer;
   private static java.lang.reflect.Method setLevel;
   private static java.lang.reflect.Method reload;
   private static ClientLevel currentLevel;

   private static java.lang.reflect.Method pumpMethod;
   private static boolean setupTerrainWarned;

   private SodiumCompat() {
   }

   /** Points Sodium's renderer at the preview level. Returns true when Sodium is driving sections. */
   public static boolean setup(ClientLevel level) {
      if (!PRESENT) {
         return false;
      }

      if (initialized && level == currentLevel) {
         return true;
      }

      try {
         if (renderer == null) {
            Class<?> type = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
            renderer = type.getMethod("instance").invoke(null);
            setLevel = type.getMethod("setLevel", ClientLevel.class);
            reload = type.getMethod("reload");

         }

         bind(level);
         initialized = true;
         WorldPreview.LOGGER.info("[preview] handed the preview level to Sodium");
         return true;
      } catch (Throwable t) {
         WorldPreview.LOGGER.warn("[preview] sodium setup failed, falling back to vanilla sections", t);
         initialized = false;
         return false;
      }
   }

   private static void bind(ClientLevel level) throws Exception {
      setLevel.invoke(renderer, level);
      currentLevel = level;
   }

   public static void restore(Minecraft minecraft) {
      if (!PRESENT || !initialized) {
         return;
      }

      try {
         bind(minecraft.level);
      } catch (Throwable t) {
         WorldPreview.LOGGER.warn("[preview] failed to restore sodium level", t);
      }
   }

   private static java.lang.reflect.Method scheduleTerrainUpdate;

   public static void refreshTerrain() {
      if (!PRESENT || !initialized || renderer == null) {
         return;
      }

      try {
         if (scheduleTerrainUpdate == null) {
            scheduleTerrainUpdate = renderer.getClass().getMethod("scheduleTerrainUpdate");
         }

         scheduleTerrainUpdate.invoke(renderer);
      } catch (Throwable t) {
         WorldPreview.LOGGER.warn("[preview] sodium terrain refresh failed", t);
      }
   }

   /** Sodium only marks a chunk renderable once its light data arrived; the preview packets carry
    *  no light data, so report it manually. */
   public static void markLightReady(ClientLevel level, int chunkX, int chunkZ) {
      if (!PRESENT) {
         return;
      }

      try {
         if (chunkTrackerGet == null) {
            Class<?> holder = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder");
            chunkTrackerGet = holder.getMethod("get", ClientLevel.class);
         }

         Object tracker = chunkTrackerGet.invoke(null, level);

         if (onChunkStatusAdded == null) {
            onChunkStatusAdded = tracker.getClass().getMethod("onChunkStatusAdded", int.class, int.class, int.class);
         }

         onChunkStatusAdded.invoke(tracker, chunkX, chunkZ, 2);
      } catch (Throwable t) {
         WorldPreview.LOGGER.warn("[preview] failed to mark chunk light ready", t);
      }
   }

   /** Recreates Sodium's renderer state (used after a preview manager was clamped to a small graph). */
   public static void reload() {
      if (!PRESENT || renderer == null) {
         return;
      }

      try {
         if (reload == null) {
            reload = renderer.getClass().getMethod("reload");
         }

         reload.invoke(renderer);
      } catch (Throwable t) {
         WorldPreview.LOGGER.warn("[preview] sodium reload failed", t);
      }
   }

   /** Schedules a rebuild for a preview chunk column so Sodium compiles its sections. */
   public static void rebuildColumn(ClientLevel level, int chunkX, int chunkZ, int centerSectionY) {
      if (!PRESENT || !initialized || renderer == null || level == null) {
         return;
      }

      try {
         if (scheduleRebuildForChunk == null) {
            scheduleRebuildForChunk = renderer.getClass()
               .getMethod("scheduleRebuildForChunk", int.class, int.class, int.class, boolean.class);
         }

         int from = level.getMinSectionY();
         int to = level.getMaxSectionY();

         for (int y = from; y <= to; y++) {
            scheduleRebuildForChunk.invoke(renderer, chunkX, y, chunkZ, true);
         }
      } catch (Throwable t) {
         WorldPreview.LOGGER.warn("[preview] failed to schedule sodium rebuild", t);
      }
   }

   private static java.lang.reflect.Method setupTerrainMethod;
   private static java.lang.reflect.Method createViewportMethod;
   private static java.lang.reflect.Method getMatrixMethod;
   private static java.lang.reflect.Method getFogParametersMethod;

   /** Runs Sodium''s terrain setup for the preview camera (normally triggered from the extractor). */
   public static void setupTerrain(net.minecraft.client.Camera camera) {
      if (!PRESENT || !initialized || renderer == null || camera == null) {
         return;
      }

      try {
         Object frustum = camera.getCullFrustum();
         if (frustum == null) {
            return;
         }

         if (createViewportMethod == null) {
            createViewportMethod = frustum.getClass().getMethod("sodium$createViewport");
         }

         Object viewport = createViewportMethod.invoke(frustum);

         if (getMatrixMethod == null) {
            getMatrixMethod = frustum.getClass().getMethod("sodium$getMatrix");
         }

         Object matrix = getMatrixMethod.invoke(frustum);
         Object gameRenderer = net.minecraft.client.Minecraft.getInstance().gameRenderer;

         if (getFogParametersMethod == null) {
            getFogParametersMethod = gameRenderer.getClass().getMethod("sodium$getFogParameters");
         }

         Object fog = getFogParametersMethod.invoke(gameRenderer);

         if (setupTerrainMethod == null) {
            for (var method : renderer.getClass().getMethods()) {
               if (method.getName().equals("setupTerrain") && method.getParameterCount() == 6) {
                  setupTerrainMethod = method;
                  break;
               }
            }
         }

         if (setupTerrainMethod != null) {
            setupTerrainMethod.invoke(renderer, camera, viewport, fog, false, true, matrix);
         }
      } catch (Throwable t) {
         if (!setupTerrainWarned) {
            setupTerrainWarned = true;
            WorldPreview.LOGGER.warn("[preview] failed to run sodium setupTerrain: {}", String.valueOf(t.getCause() == null ? t : t.getCause()));
         }
      }
   }

   /** Drains the chunk tracker into Sodium''s section manager (normally done by setupTerrain). */
   public static void pumpChunkEvents() {
      if (!PRESENT || !initialized || renderer == null) {
         return;
      }

      try {
         if (pumpMethod == null) {
            pumpMethod = renderer.getClass().getDeclaredMethod("processChunkEvents");
            pumpMethod.setAccessible(true);
         }

         pumpMethod.invoke(renderer);
      } catch (Throwable t) {
         WorldPreview.LOGGER.warn("[preview] failed to pump sodium chunk events", t);
      }
   }

   /** Number of sections Sodium currently has in its render lists. */
   /** Number of chunks Sodium's tracker reports as ready. */
   public static int readyChunks() {
      if (!PRESENT || renderer == null) {
         return 0;
      }

      try {
         Object tracker = renderer.getClass().getMethod("getChunkTracker").invoke(renderer);
         return (Integer)tracker.getClass().getMethod("getReadyChunkCount").invoke(tracker);
      } catch (Throwable t) {
         return -1;
      }
   }

   public static int visibleSections() {
      if (!PRESENT || renderer == null) {
         return 0;
      }

      try {
         return (Integer)renderer.getClass().getMethod("getVisibleChunkCount").invoke(renderer);
      } catch (Throwable t) {
         return 0;
      }
   }

   private static java.lang.reflect.Method chunkTrackerGet;
   private static java.lang.reflect.Method onChunkStatusAdded;
   private static java.lang.reflect.Method scheduleRebuildForChunk;

   /** Sodium's "terrain render complete" flag for the bound level, or null when unavailable. */
   public static Boolean terrainComplete() {
      if (!PRESENT || renderer == null) {
         return null;
      }

      try {
         Object complete = renderer.getClass().getMethod("isTerrainRenderComplete").invoke(renderer);
         return complete instanceof Boolean value ? value : null;
      } catch (Throwable t) {
         return null;
      }
   }

   public static String stats() {
      if (!PRESENT || renderer == null) {
         return "sodium-absent";
      }

      try {
         int ready = -1;
         if (currentLevel != null) {
            Class<?> holder = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder");
            Object tracker = holder.getMethod("get", ClientLevel.class).invoke(null, currentLevel);
            Object chunks = tracker.getClass().getMethod("getReadyChunks").invoke(tracker);
            ready = ((it.unimi.dsi.fastutil.longs.LongCollection)chunks).size();
         }

         Object visible = renderer.getClass().getMethod("getVisibleChunkCount").invoke(renderer);
         Object complete = renderer.getClass().getMethod("isTerrainRenderComplete").invoke(renderer);
         Object debug = renderer.getClass().getMethod("getChunksDebugString").invoke(renderer);
         return "ready=" + ready + ", visible=" + visible + ", complete=" + complete + ", debug=" + debug;
      } catch (Throwable t) {
         return "stats-failed: " + t;
      }
   }

   public static boolean isDriving() {
      return initialized;
   }
}
