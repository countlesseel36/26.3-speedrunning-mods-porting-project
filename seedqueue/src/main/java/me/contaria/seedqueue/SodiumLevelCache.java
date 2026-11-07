package me.contaria.seedqueue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Sodium multi-manager patch: keeps one {@code RenderSectionManager} alive per preview level.
 *
 * <p>Sodium's {@code SodiumWorldRenderer#setLevel} destroys the current section manager (shutting
 * down its {@code ChunkBuilder} worker threads and freeing the compiled geometry). The wall has to
 * switch between queued worlds, so without this patch every switch threw away all compiled terrain
 * and the wall had to recompile the world, which is why the preview dwell had to be so long.</p>
 *
 * <p>This cache is only touched from the render thread (Sodium's level binding happens there).</p>
 */
public final class SodiumLevelCache {
   // One manager at a time - deliberately.
   //
   // Sodium's vertex arenas are allocated per device and shared between managers, but each manager
   // owns its render lists. Keeping several managers alive meant one manager could resize or evict an
   // arena while another one still had that range in its render list: the GPU then drew garbage
   // polygons, which is exactly the region that flickers white in the cards (and the missing
   // "GL_OUT_OF_MEMORY" buffers behind the titles-screen hang). Holding a single manager makes the
   // cache behave like vanilla Sodium again - a level switch destroys the previous manager - at the
   // cost of recompiling a preview when the wall switches cards.
   private static final int MAX_MANAGERS = 1;
   // These are reached from the background queue thread (marking previews, queueing evictions) and
   // from the render thread (binding levels, pumping evictions). Plain IdentityHashMap based
   // collections are not safe for that: concurrent add/get can corrupt them and spin forever, which
   // is what made leaving a world hang. Level does not override equals, so a ConcurrentHashMap keeps
   // the identity semantics while being safe across threads.
   private static final Map<ClientLevel, Object> MANAGERS = new java.util.concurrent.ConcurrentHashMap<>();
   private static final Set<ClientLevel> PREVIEW_LEVELS = java.util.concurrent.ConcurrentHashMap.newKeySet();
   private static final Set<ClientLevel> PENDING_EVICT = java.util.concurrent.ConcurrentHashMap.newKeySet();
   private static final ArrayDeque<ClientLevel> ORDER = new ArrayDeque<>();
   private static final ThreadLocal<Integer> THREAD_LIMIT = new ThreadLocal<>();

   private SodiumLevelCache() {
   }

   /** Marks a level as a preview level: managers for it get a smaller chunk builder thread pool. */
   public static void markPreview(ClientLevel level) {
      if (level != null) {
         PREVIEW_LEVELS.add(level);
      }
   }

   /** Marks a level as a preview level. {@code spawn} is kept for diagnostics only. */
   public static void markPreview(ClientLevel level, net.minecraft.core.BlockPos spawn) {
      markPreview(level);
   }

   public static boolean isPreview(ClientLevel level) {
      return level != null && PREVIEW_LEVELS.contains(level);
   }

   public static int size() {
      return MANAGERS.size();
   }

   public static Object get(ClientLevel level) {
      return level == null ? null : MANAGERS.get(level);
   }

   public static void put(ClientLevel level, Object manager) {
      if (level == null || manager == null) {
         return;
      }

      Object previous = MANAGERS.put(level, manager);

      if (previous == null) {
         ORDER.addLast(level);
      }

      while (ORDER.size() > MAX_MANAGERS) {
         ClientLevel oldest = ORDER.pollFirst();

         if (oldest != null && oldest != level) {
            Object evicted = MANAGERS.remove(oldest);
            PREVIEW_LEVELS.remove(oldest);
            destroy(evicted);
         }
      }
   }

   public static Object remove(ClientLevel level) {
      if (level == null) {
         return null;
      }

      ORDER.remove(level);
      return MANAGERS.remove(level);
   }

   /** Queues a manager for destruction; processed on the render thread from {@link #processPending()}. */
   public static void evictLater(ClientLevel level) {
      if (level != null) {
         PENDING_EVICT.add(level);
         SeedQueue.LOGGER.info(
            "[seedqueue] sodium: queued eviction for level {} (cache={})", System.identityHashCode(level), MANAGERS.size()
         );
      }
   }

   /** Queues every preview manager except {@code keep} for destruction (frees GPU arenas). */
   public static void evictAllExcept(ClientLevel keep) {
      for (ClientLevel level : new ArrayList<>(MANAGERS.keySet())) {
         if (level != keep) {
            evictLater(level);
         }
      }
   }

   /** Render thread: destroys managers of discarded preview levels. */
   public static void processPending() {
      if (SeedQueue.shuttingDown || PENDING_EVICT.isEmpty()) {
         return;
      }

      // At most two managers per tick. Tearing several down in a single frame made Sodium allocate a
      // fresh shared arena (256 MiB) while the old ones were still being freed, which ended in a
      // GpuOutOfMemoryException crash; draining a large backlog one per tick was too slow.
      int budget = PENDING_EVICT.size() > 4 ? 2 : 1;

      for (int i = 0; i < budget && !PENDING_EVICT.isEmpty(); i++) {
         destroyOne();
      }

      if (SeedQueue.shuttingDown || PENDING_EVICT.isEmpty()) {
         SeedQueue.LOGGER.info("[seedqueue] sodium: eviction queue drained");
      }
   }

   private static void destroyOne() {
      ClientLevel level = PENDING_EVICT.iterator().next();
      PENDING_EVICT.remove(level);
      Object manager = remove(level);
      PREVIEW_LEVELS.remove(level);
      if (Boolean.getBoolean("seedqueue.sodiumLog")) {
         SeedQueue.LOGGER.info(
            "[seedqueue] sodium: evicting level {} manager={} (cache={}, pending={})",
            System.identityHashCode(level),
            System.identityHashCode(manager),
            MANAGERS.size(),
            PENDING_EVICT.size()
         );
      }
      destroy(manager);

      if (SeedQueue.shuttingDown || PENDING_EVICT.isEmpty()) {
         SeedQueue.LOGGER.info("[seedqueue] sodium: eviction queue drained");
      }
   }

   private static void destroy(Object manager) {
      if (manager instanceof net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager sectionManager) {
         try {
            sectionManager.destroy();
         } catch (Throwable t) {
            SeedQueue.LOGGER.warn("[seedqueue] failed to destroy cached sodium section manager", t);
         }
      }
   }

   public static void setThreadLimit(int limit) {
      THREAD_LIMIT.set(limit);
   }

   public static void clearThreadLimit() {
      THREAD_LIMIT.remove();
   }

   public static int threadLimit() {
      Integer explicit = THREAD_LIMIT.get();

      if (explicit != null) {
         return explicit;
      }

      return 0;
   }
}
